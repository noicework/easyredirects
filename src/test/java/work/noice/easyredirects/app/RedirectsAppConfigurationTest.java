package work.noice.easyredirects.app;

import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;
import info.magnolia.module.model.ModuleDefinition;
import info.magnolia.module.model.reader.JacksonModuleDefinitionReader;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** Guards the deployed app definition, not a separate test fixture. */
public class RedirectsAppConfigurationTest {
    private Map<String, Object> app;

    @Before
    public void readDefinition() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/easyredirects/apps/redirects.yaml")) {
            assertNotNull(stream);
            app = new Yaml().load(stream);
        }
    }

    @SuppressWarnings("unchecked")
    private Object at(String path) {
        Object value = app;
        for (String key : path.split("/")) value = ((Map<String, Object>) value).get(key);
        return value;
    }

    private Object action(String name, String property) {
        return at("subApps/browser/actions/" + name + "/" + property);
    }

    @Test
    public void packagedDescriptorHasResolvedIdentityAndDependencyVersion() throws Exception {
        ModuleDefinition module = new JacksonModuleDefinitionReader()
                .readFromResource("/META-INF/magnolia/easyredirects.xml");
        assertEquals("easyredirects", module.getName());
        assertNotNull(module.getVersion());
        try (InputStream stream = getClass().getResourceAsStream("/META-INF/magnolia/easyredirects.xml")) {
            assertFalse(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("${"));
        }
    }

    @Test
    public void publishAndUnpublishUseMagnolia64CommandsAndWriteGuards() {
        assertEquals("versioned", action("publish", "catalog"));
        assertEquals("publish", action("publish", "command"));
        assertEquals("unpublish", action("unpublish", "command"));
        // The versioned unpublish command was removed in Magnolia 6.4.
        assertNull(action("unpublish", "catalog"));
        for (String name : List.of("publish", "unpublish", "publishDeletion", "markDeleted", "restore")) {
            assertEquals(true, action(name, "availability/writePermissionRequired"));
        }
        assertEquals("jcrPublishableRule", action("publish", "availability/rules/isPublishable/$type"));
        assertEquals(true, action("publish", "availability/rules/notDeleted/negate"));
        assertEquals("jcrPublishedRule", action("unpublish", "availability/rules/isPublished/$type"));
    }

    @Test
    public void deletionIsConfirmedAndPublishedInsteadOfLeavingOrphansOnPublic() {
        assertEquals("markDeleted", action("deleteRedirect", "successActionName"));
        assertEquals("markAsDeletedAction", action("markDeleted", "$type"));
        assertEquals("publishDeletion", action("deletePermanently", "successActionName"));
        assertEquals("versioned", action("publishDeletion", "catalog"));
        assertEquals("publish", action("publishDeletion", "command"));
        assertEquals("jcrIsDeletedRule", action("publishDeletion", "availability/rules/isDeleted/$type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void actionbarReferencesResolveAndDeletedItemsHaveTheirOwnSection() {
        List<Map<String, Object>> sections = (List<Map<String, Object>>) at("subApps/browser/actionbar/sections");
        assertEquals("deletedRedirect", sections.get(0).get("name"));
        Map<String, Object> actions = (Map<String, Object>) at("subApps/browser/actions");
        for (Map<String, Object> section : sections) {
            for (Map<String, Object> group : (List<Map<String, Object>>) section.get("groups")) {
                for (Map<String, Object> item : (List<Map<String, Object>>) group.get("items")) {
                    assertTrue("Undefined action " + item.get("name"), actions.containsKey(item.get("name")));
                }
            }
        }
        for (String name : List.of("redirect", "folder")) {
            Map<String, Object> section = sections.stream().filter(s -> name.equals(s.get("name"))).findFirst().orElseThrow();
            assertTrue(section.toString().contains("name=publish"));
            assertTrue(section.toString().contains("name=unpublish"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void statusColumnAndNamingSaveActionAreWiredToExistingWorkspace() {
        assertEquals("redirects", at("datasource/workspace"));
        assertEquals("commitAction", at("subApps/detail/actions/commit/$type"));
        assertEquals(SaveRedirectAction.class.getName(), at("subApps/detail/actions/commit/implementationClass"));
        assertEquals("vaadinForm", at("subApps/detail/form/$type"));
        List<Map<String, Object>> views = (List<Map<String, Object>>) at("subApps/browser/workbench/contentViews");
        List<Map<String, Object>> columns = (List<Map<String, Object>>) views.get(0).get("columns");
        assertTrue(columns.stream().anyMatch(column -> "statusColumn".equals(column.get("$type"))));
        assertFalse(columns.stream().anyMatch(column -> "propertyColumn".equals(column.get("$type"))));
    }
}
