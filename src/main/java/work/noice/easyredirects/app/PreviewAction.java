package work.noice.easyredirects.app;

/*
 * #%L
 * easyredirects Magnolia Module
 * %%
 * 
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

import work.noice.easyredirects.RedirectsService;
import info.magnolia.ui.ValueContext;
import info.magnolia.ui.api.action.AbstractAction;
import info.magnolia.ui.api.location.DefaultLocation;
import info.magnolia.ui.api.location.Location;
import info.magnolia.ui.api.location.LocationController;
import info.magnolia.ui.contentapp.action.OpenLocationActionDefinition;

import jakarta.inject.Inject;
import javax.jcr.Node;

import static info.magnolia.ui.api.location.Location.LOCATION_TYPE_APP;

/**
 * Preview action for redirects. Opens the website page or the external url in the configured app.
 *
 * @author frank.sommer
 * @since 06.05.14
 */
public class PreviewAction extends AbstractAction<OpenLocationActionDefinition> {

    private final LocationController _locationController;
    private final ValueContext<Node> _valueContext;
    private RedirectsService _redirectsService;

    @Inject
    public PreviewAction(OpenLocationActionDefinition definition, ValueContext<Node> valueContext, LocationController locationController) {
        super(definition);
        _locationController = locationController;
        _valueContext = valueContext;
    }

    @Override
    public void execute() {
        if (_valueContext.getSingle().isPresent()) {
            String link = _redirectsService.createPreviewUrl(_valueContext.getSingle().get());
            Location location = new DefaultLocation(LOCATION_TYPE_APP, getDefinition().getAppName(), getDefinition().getSubAppId(), link);
            _locationController.goTo(location);
        }
    }

    @Inject
    public void setRedirectsService(final RedirectsService redirectsService) {
        _redirectsService = redirectsService;
    }
}
