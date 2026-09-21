package work.noice.easyredirects.app;

import info.magnolia.jcr.RuntimeRepositoryException;
import info.magnolia.jcr.util.NodeNameHelper;
import info.magnolia.jcr.util.NodeUtil;
import info.magnolia.ui.CloseHandler;
import info.magnolia.ui.ValueContext;
import info.magnolia.ui.api.action.ActionDefinition.RefreshBehavior;
import info.magnolia.ui.contentapp.Datasource;
import info.magnolia.ui.contentapp.action.CommitAction;
import info.magnolia.ui.contentapp.action.CommitActionDefinition;
import info.magnolia.ui.editor.EditorView;
import info.magnolia.ui.observation.DatasourceObservation;

import jakarta.inject.Inject;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

/** Saves the form normally, naming only newly created redirects from their From URL. */
public class SaveRedirectAction extends CommitAction<Node> {
    private final NodeNameHelper nodeNameHelper;

    @Inject
    public SaveRedirectAction(CommitActionDefinition definition, CloseHandler closeHandler,
            ValueContext<Node> valueContext, EditorView<Node> form, Datasource<Node> datasource,
            DatasourceObservation.Manual<Node> observation, NodeNameHelper nodeNameHelper) {
        super(definition, closeHandler, valueContext, form, datasource, observation);
        this.nodeNameHelper = nodeNameHelper;
    }

    @Override
    protected void write() {
        getValueContext().getSingle().ifPresent(node -> {
            getForm().write(node);
            try {
                // Never rename an existing/published record, even if its From URL changes.
                if (node.isNew() && node.hasProperty("fromUrl")) {
                    String name = nodeNameHelper.getValidatedName(
                            nameFromUrl(node.getProperty("fromUrl").getString()));
                    if (!name.equals(node.getName())) {
                        NodeUtil.renameNode(node, nodeNameHelper.getUniqueName(node.getParent(), name));
                    }
                }
            } catch (RepositoryException e) {
                throw new RuntimeRepositoryException(e);
            }
            // Preserve Magnolia's normal save lifecycle and refresh semantics. Saving is
            // deliberately not publishing: the explicit Publish action controls release.
            getDatasource().save(node);
            if (getDefinition().getDatasourceRefreshBehavior() == RefreshBehavior.ITEMS) {
                getDatasourceObservation().trigger(node);
            } else {
                getDatasourceObservation().trigger();
            }
        });
    }

    static String nameFromUrl(String fromUrl) {
        String name = fromUrl.trim().replaceAll("[^\\p{L}\\p{N}._-]+", "-")
                .replaceAll("-+", "-").replaceAll("^[-.]+|[-.]+$", "");
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return "redirect-root";
        }
        // Bound names for long URLs/patterns; sibling collisions are resolved by Magnolia.
        return name.length() > 120 ? name.substring(0, 120) : name;
    }
}
