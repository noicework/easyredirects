package work.noice.easyredirects.app;

/*
 * #%L
 * easyredirects Magnolia Module
 * %%
 * Copyright (C) 2013 - 2014 IBM iX
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.machinezoo.noexception.Exceptions;
import com.vaadin.ui.Notification;
import work.noice.easyredirects.RedirectsService;
import info.magnolia.i18nsystem.SimpleTranslator;
import info.magnolia.jcr.util.NodeNameHelper;
import info.magnolia.jcr.util.NodeUtil;
import info.magnolia.ui.AlertBuilder;
import info.magnolia.ui.CloseHandler;
import info.magnolia.ui.ValueContext;
import info.magnolia.ui.api.app.AppContext;
import info.magnolia.ui.api.location.LocationController;
import info.magnolia.ui.contentapp.ContentBrowserSubApp;
import info.magnolia.ui.contentapp.Datasource;
import info.magnolia.ui.contentapp.action.CommitAction;
import info.magnolia.ui.contentapp.action.CommitActionDefinition;
import info.magnolia.ui.datasource.ItemResolver;
import info.magnolia.ui.editor.EditorView;
import info.magnolia.ui.observation.DatasourceObservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.List;

import static work.noice.easyredirects.RedirectsService.PN_SITE;
import static work.noice.easyredirects.RedirectsService.PN_REDIRECT;
import static info.magnolia.jcr.util.NodeUtil.getNodeIdentifierIfPossible;
import static info.magnolia.jcr.util.PropertyUtil.getString;
import static org.apache.commons.lang3.StringUtils.stripStart;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

/**
 * Save form action for redirects.
 *
 * @author frank.sommer
 * @since 28.05.14
 */
public class RedirectsSaveFormAction extends CommitAction<Node> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedirectsSaveFormAction.class);

    private final AppContext _appContext;
    private final LocationController _locationController;
    private final ItemResolver<Node> _itemResolver;
    private SimpleTranslator _simpleTranslator;
    private RedirectsService _redirectsService;
    private NodeNameHelper _nodeNameHelper;

    //CHECKSTYLE:OFF
    @Inject
    public RedirectsSaveFormAction(CommitActionDefinition definition, CloseHandler closeHandler, ValueContext<Node> valueContext, EditorView<Node> form, Datasource<Node> datasource, DatasourceObservation.Manual datasourceObservation, LocationController locationController, AppContext appContext, ItemResolver<Node> itemResolver) {
        super(definition, closeHandler, valueContext, form, datasource, datasourceObservation);
        _appContext = appContext;
        _locationController = locationController;
        _itemResolver = itemResolver;
    }
    //CHECKSTYLE:ON

    @Override
    protected boolean validateForm() {
        boolean isValid = super.validateForm();
        if (isValid && getValueContext().getSingle().isPresent()) {
            Node node = getValueContext().getSingle().get();
            getForm().write(node);

            String site = getString(node, PN_SITE);
            String redirect = getString(node, PN_REDIRECT);
            if (site != null && redirect != null) {
                List<Node> nodes = _redirectsService.queryForRedirectNodes(redirect, site);
                String currentIdentifier = getNodeIdentifierIfPossible(node);
                for (Node resultNode : nodes) {
                    if (!currentIdentifier.equals(getNodeIdentifierIfPossible(resultNode))) {
                        isValid = false;
                        AlertBuilder.alert(_simpleTranslator.translate("actions.commit.failureMessage"))
                            .withLevel(Notification.Type.WARNING_MESSAGE)
                            .withBody(_simpleTranslator.translate("redirect.errorMessage.notUnique"))
                            .withOkButtonCaption(_simpleTranslator.translate("button.ok"))
                            .buildAndOpen();
                        break;
                    }
                }
            }
        }
        return isValid;
    }

    @Override
    protected void write() {
        getValueContext().getSingle().ifPresent(Exceptions.wrap().consumer(
            item -> {
                setNodeName(item);

                getDatasource().commit(item);
                getDatasourceObservation().trigger();
            }
        ));

        // update location after saving content
        _locationController.goTo(
            new ContentBrowserSubApp.BrowserLocation(
                _appContext.getName(), "browser", getValueContext().getSingle().map(_itemResolver::getId).orElse("")
            )
        );
    }

    private String getNormalizedRedirect(final Node node) {
        String redirect = getString(node, PN_REDIRECT);
        redirect = stripStart(trimToEmpty(redirect), "/");
        return redirect;
    }


    private void setNodeName(Node node) throws RepositoryException {
        if (node.hasProperty(PN_REDIRECT) && !node.hasProperty("jcrName")) {
            String newNodeName = _nodeNameHelper.getValidatedName(getNormalizedRedirect(node));
            if (!node.getName().equals(newNodeName)) {
                newNodeName = _nodeNameHelper.getUniqueName(node.getParent(), newNodeName);
                NodeUtil.renameNode(node, newNodeName);
            }
        }
    }

    @Inject
    public void setRedirectsService(final RedirectsService redirectsService) {
        _redirectsService = redirectsService;
    }

    @Inject
    public void setSimpleTranslator(final SimpleTranslator simpleTranslator) {
        _simpleTranslator = simpleTranslator;
    }

    @Inject
    public void setNodeNameHelper(final NodeNameHelper nodeNameHelper) {
        _nodeNameHelper = nodeNameHelper;
    }


}
