package work.noice.easyredirects.app;

import info.magnolia.init.MagnoliaConfigurationProperties;
import info.magnolia.context.Context;
import info.magnolia.jcr.util.NodeNameHelper;
import info.magnolia.test.mock.jcr.MockNode;
import info.magnolia.test.mock.jcr.MockSession;
import info.magnolia.ui.CloseHandler;
import info.magnolia.ui.ValueContext;
import info.magnolia.ui.api.action.ActionDefinition.RefreshBehavior;
import info.magnolia.ui.contentapp.Datasource;
import info.magnolia.ui.contentapp.action.CommitActionDefinition;
import info.magnolia.ui.editor.EditorView;
import info.magnolia.ui.datasource.jcr.JcrDatasource;
import info.magnolia.ui.datasource.jcr.JcrDatasourceDefinition;
import info.magnolia.ui.observation.DatasourceObservation;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.apache.jackrabbit.core.TransientRepository;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import java.io.InputStream;

import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SaveRedirectActionTest {
    @Rule public TemporaryFolder repositoryHome = new TemporaryFolder();
    private MockSession session;
    private ValueContext<Node> context;
    private EditorView<Node> form;
    private Datasource<Node> datasource;
    private DatasourceObservation.Manual<Node> observation;
    private CloseHandler closeHandler;
    private CommitActionDefinition definition;
    private SaveRedirectAction action;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        session = new MockSession("redirects");
        context = mock(ValueContext.class);
        form = mock(EditorView.class);
        datasource = mock(Datasource.class);
        observation = mock(DatasourceObservation.Manual.class);
        closeHandler = mock(CloseHandler.class);
        definition = new CommitActionDefinition();
        action = new SaveRedirectAction(definition, closeHandler, context, form, datasource,
                observation, new NodeNameHelper(mock(MagnoliaConfigurationProperties.class)));
    }

    private MockNode prepare(String path, boolean isNew, String fromUrl) throws Exception {
        MockNode node = (MockNode) session.getRootNode().addNode(path, "mgnl:redirect");
        node.setIsNew(isNew);
        when(context.getSingle()).thenReturn(Optional.of(node));
        // The URL is only available after the form writes, just as in the UI.
        doAnswer(invocation -> { node.setProperty("fromUrl", fromUrl); return null; })
                .when(form).write(node);
        return node;
    }

    @Test
    public void newRedirectIsNamedBeforeSavingAndClosesNormally() throws Exception {
        MockNode node = prepare("untitled", true, "/about-us/governance/lleg");
        doAnswer(invocation -> {
            // MockSession.move copies nodes rather than updating existing references.
            assertTrue(session.nodeExists("/about-us-governance-lleg"));
            assertFalse(session.nodeExists("/untitled"));
            return node;
        }).when(datasource).save(node);
        action.execute();
        assertEquals("/about-us/governance/lleg", session.getNode("/about-us-governance-lleg")
                .getProperty("fromUrl").getString());
        verify(form).validate();
        verify(form).write(node);
        verify(datasource).save(node);
        verify(observation).trigger();
        verify(closeHandler).close();
        verifyNoMoreInteractions(datasource);
    }

    @Test
    public void collisionsGetUniqueNamesWithoutOverwriting() throws Exception {
        Node existing = session.getRootNode().addNode("about-us-lleg", "mgnl:redirect");
        existing.setProperty("fromUrl", "/about/us/lleg");
        MockNode node = prepare("untitled", true, "/about-us/lleg");
        action.execute();
        assertFalse(session.nodeExists("/untitled"));
        Node renamed = session.getNode("/about-us-lleg0");
        assertEquals("/about-us/lleg", renamed.getProperty("fromUrl").getString());
        assertEquals("/about/us/lleg", existing.getProperty("fromUrl").getString());
        assertEquals(2, session.getRootNode().getNodes().getSize());
    }

    @Test
    public void existingUntitledAndPublishedNamesAreNeverChanged() throws Exception {
        for (String name : new String[] { "untitled11", "old-readable-name" }) {
            MockNode node = prepare(name, false, "/changed-url");
            node.setProperty("mgnl:activationStatus", true);
            action.execute();
            assertEquals(name, node.getName());
            assertTrue(node.getProperty("mgnl:activationStatus").getBoolean());
        }
    }

    @Test
    public void newRedirectStaysInItsFolderAndRefreshesItemWhenRequested() throws Exception {
        session.getRootNode().addNode("campaign", "mgnl:folder");
        MockNode node = prepare("campaign/untitled", true, "/old-url");
        definition.setDatasourceRefreshBehavior(RefreshBehavior.ITEMS);
        action.execute();
        assertTrue(session.nodeExists("/campaign/old-url"));
        assertFalse(session.nodeExists("/campaign/untitled"));
        verify(observation).trigger(node);
        verify(observation, never()).trigger();
    }

    @Test
    public void failedValidationDoesNotWriteRenameSaveOrClose() throws Exception {
        MockNode node = prepare("untitled", true, "/old-url");
        SaveRedirectAction validatingAction = new SaveRedirectAction(definition, closeHandler,
                context, form, datasource, observation,
                new NodeNameHelper(mock(MagnoliaConfigurationProperties.class))) {
            @Override protected boolean validateForm() { return false; }
        };
        validatingAction.execute();
        assertEquals("untitled", node.getName());
        verify(form, never()).write(any());
        verifyNoInteractions(datasource, observation, closeHandler);
    }

    @Test
    public void namesHandleRootPatternsPunctuationAndLongUrls() {
        assertEquals("about-us-governance-lleg", SaveRedirectAction.nameFromUrl(" /about-us/governance/lleg/ "));
        assertEquals("redirect-root", SaveRedirectAction.nameFromUrl("/"));
        assertEquals("redirect-root", SaveRedirectAction.nameFromUrl("/../"));
        assertEquals("news", SaveRedirectAction.nameFromUrl("/news/(.*)"));
        assertEquals("café", SaveRedirectAction.nameFromUrl("/café"));
        assertEquals(120, SaveRedirectAction.nameFromUrl("/" + "a".repeat(200)).length());
    }

    @Test
    public void realJcrSavePreservesIdentityAndOnlyRenamesOnFirstSave() throws Exception {
        RepositoryConfig config;
        try (InputStream xml = getClass().getResourceAsStream("/redirect-test-repository.xml")) {
            config = RepositoryConfig.create(xml, repositoryHome.newFolder("repository").getAbsolutePath());
        }
        TransientRepository repository = new TransientRepository(config);
        Session realSession = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
        try {
            Context magnoliaContext = mock(Context.class);
            when(magnoliaContext.getJCRSession("default")).thenReturn(realSession);
            JcrDatasourceDefinition jcrDefinition = new JcrDatasourceDefinition();
            jcrDefinition.setWorkspace("default");
            JcrDatasource realDatasource = new JcrDatasource(() -> magnoliaContext, jcrDefinition);
            // Exercise the same decorated node/session types used by the content app.
            Node node = realDatasource.getRoot().addNode("untitled", "nt:unstructured");
            node.addMixin("mix:referenceable");
            String id = node.getIdentifier();
            when(context.getSingle()).thenReturn(Optional.of(node));
            doAnswer(invocation -> { node.setProperty("fromUrl", "/new-redirect"); return null; })
                    .when(form).write(node);
            doAnswer(invocation -> realDatasource.save(node)).when(datasource).save(node);
            action.execute();
            assertEquals("/new-redirect", node.getPath());
            assertEquals(id, realSession.getNode("/new-redirect").getIdentifier());
            assertFalse(realSession.nodeExists("/untitled"));
            assertFalse(node.isNew());
            doAnswer(invocation -> { node.setProperty("fromUrl", "/edited-url"); return null; })
                    .when(form).write(node);
            action.execute();
            assertEquals("/new-redirect", node.getPath());
            assertEquals("/edited-url", node.getProperty("fromUrl").getString());
        } finally {
            realSession.logout();
            repository.shutdown();
        }
    }
}
