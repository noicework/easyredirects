package work.noice.easyredirects;

import info.magnolia.cms.i18n.DefaultI18nContentSupport;
import info.magnolia.cms.i18n.I18nContentSupport;
import info.magnolia.context.MgnlContext;
import info.magnolia.repository.RepositoryConstants;
import info.magnolia.test.ComponentsTestUtil;
import info.magnolia.test.mock.MockWebContext;
import info.magnolia.test.mock.jcr.MockNode;
import info.magnolia.test.mock.jcr.MockSession;
import org.junit.Before;
import org.junit.Test;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.nullValue;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

/**
 * Test the service class.
 *
 * @author frank.sommer
 * @since 28.05.14
 */
public class RedirectsServiceTest {

    private static final String TEST_UUID = "123-4556-123";
    private static final String TEST_UUID_FORWARD = "123-4556-124";

    private RedirectsService _service;

    @Test
    public void testRedirectWithNull() {
        assertThat(_service.createRedirectUrl(null, null), equalTo(""));
    }

    @Test
    public void testTargetUrlWithEmptyNode() {
        MockNode mockNode = new MockNode("node");
        assertThat(_service.createRedirectUrl(mockNode, null), equalTo(""));
        assertThat(_service.createPreviewUrl(mockNode), equalTo(""));
    }

    @Test
    public void testTargetUrlInternalWithAnchor() throws Exception {
        MockNode mockNode = new MockNode("node");
        mockNode.setProperty("link", TEST_UUID);
        mockNode.setProperty("linkSuffix", "#anchor1");

        assertThat(_service.createRedirectUrl(mockNode, null), equalTo("redirect:/internal/page.html#anchor1"));
        assertThat(_service.createPreviewUrl(mockNode), equalTo("/internal/page.html#anchor1"));
    }

    @Test
    public void testTargetUrlExternal() throws Exception {
        MockNode mockNode = new MockNode("node");
        mockNode.setProperty("link", "http://www.noice.work");

        assertThat(_service.createRedirectUrl(mockNode, null), equalTo("redirect:http://www.noice.work"));
        assertThat(_service.createPreviewUrl(mockNode), equalTo("http://www.noice.work"));
    }

    @Test
    public void testForward() throws Exception {
        MockNode mockNode = new MockNode("node");
        mockNode.setProperty("link", TEST_UUID_FORWARD);
        mockNode.setProperty("type", "forward");
        assertThat(_service.createRedirectUrl(mockNode, null), equalTo("forward:/internal/forward/page.html"));
    }

    @Test
    public void testRedirectWithAnchor() throws Exception {
        MockNode mockNode = new MockNode("node");
        mockNode.setProperty("link", TEST_UUID_FORWARD);
        mockNode.setProperty("type", "301");
        mockNode.setProperty("linkSuffix", "#anchor1");
        assertThat(_service.createRedirectUrl(mockNode, null), equalTo("permanent:/internal/forward/page.html#anchor1"));
    }

    @Test
    public void testRedirectWithOriginSuffix() throws Exception {
        MockNode mockNode = new MockNode("node");
        mockNode.setProperty("link", TEST_UUID_FORWARD);
        mockNode.setProperty("type", "301");
        assertThat(_service.createRedirectUrl(mockNode, "?test=1"), equalTo("permanent:/internal/forward/page.html?test=1"));
    }

    @Test
    public void testForwardWithInvalidUrl() throws Exception {
        MockNode mockNode = new MockNode("node");
        mockNode.setProperty("link", "http://www.noice.work");
        mockNode.setProperty("type", "forward");
        assertThat(_service.createRedirectUrl(mockNode, null), equalTo(""));
    }

    @Test
    public void testPublicUrl() {
        assertThat(_service.createPublicUrl(null), equalTo("http://www.noice.work/page.html"));
    }

    @Test
    public void testRedirectUrl() {
        assertThat(_service.createRedirectUrl(null), equalTo("http://www.noice.work/redirect"));
    }


    @Test
    public void testWildcardPatternMatching() {
        // Test simple wildcard pattern
        Map<String, String> result = RedirectsService.matchPattern("/products/electronics/laptop", "/products/*");
        assertThat(result, notNullValue());
        assertThat(result.isEmpty(), equalTo(true));

        // Test no match
        result = RedirectsService.matchPattern("/blog/2023/article", "/products/*");
        assertThat(result, nullValue());

        // Test wildcard in middle
        result = RedirectsService.matchPattern("/docs/v1/latest", "/docs/*/latest");
        assertThat(result, notNullValue());
    }

    @Test
    public void testPathParameterMatching() {
        // Test single parameter
        Map<String, String> result = RedirectsService.matchPattern("/user/john123/profile", "/user/{username}/profile");
        assertThat(result, notNullValue());
        assertThat(result.get("username"), equalTo("john123"));

        // Test multiple parameters
        result = RedirectsService.matchPattern("/blog/2023/12", "/blog/{year}/{month}");
        assertThat(result, notNullValue());
        assertThat(result.get("year"), equalTo("2023"));
        assertThat(result.get("month"), equalTo("12"));

        // Test no match
        result = RedirectsService.matchPattern("/blog/2023", "/blog/{year}/{month}");
        assertThat(result, nullValue());
    }

    @Test
    public void testResolvePlaceholders() {
        Map<String, String> params = new HashMap<>();
        params.put("year", "2023");
        params.put("month", "12");
        params.put("slug", "my-article");

        String resolved = RedirectsService.resolvePlaceholders("/archives/{year}-{month}/{slug}", params);
        assertThat(resolved, equalTo("/archives/2023-12/my-article"));

        // Test with missing parameter (should not replace)
        resolved = RedirectsService.resolvePlaceholders("/archives/{year}/{missing}", params);
        assertThat(resolved, equalTo("/archives/2023/{missing}"));
    }

    @Test
    public void testRedirectWithPatternParameters() throws Exception {
        MockNode mockNode = new MockNode("node");
        mockNode.setProperty("link", TEST_UUID);
        mockNode.setProperty("linkSuffix", "/{category}");
        mockNode.setProperty("usePattern", true);

        Map<String, String> params = new HashMap<>();
        params.put("category", "electronics");

        assertThat(_service.createRedirectUrl(mockNode, null, params), equalTo("redirect:/internal/page.html/electronics"));
    }

    @Test
    public void testExternalRedirectWithPatternParameters() throws Exception {
        MockNode mockNode = new MockNode("node");
        mockNode.setProperty("link", "https://example.com/search?q={query}");
        mockNode.setProperty("usePattern", true);

        Map<String, String> params = new HashMap<>();
        params.put("query", "magnolia-cms");

        assertThat(_service.createRedirectUrl(mockNode, null, params), equalTo("redirect:https://example.com/search?q=magnolia-cms"));
    }

    private MockNode createNode(MockSession session, String path) throws Exception {
        MockNode current = (MockNode) session.getRootNode();
        String[] parts = path.split("/");
        for (String part : parts) {
            MockNode child;
            if (current.hasNode(part)) {
                child = (MockNode) current.getNode(part);
            } else {
                child = new MockNode(part);
                current.addNode(child);
            }
            current = child;
        }
        return current;
    }

    @Before
    public void setUp() throws Exception {
        ComponentsTestUtil.setInstance(I18nContentSupport.class, new DefaultI18nContentSupport());
        MockWebContext webContext = new MockWebContext();
        MockSession session = new MockSession(RepositoryConstants.WEBSITE);
        createNode(session, "/internal/forward/page").setIdentifier(TEST_UUID_FORWARD);
        createNode(session, "/internal/page").setIdentifier(TEST_UUID);
        webContext.addSession(RepositoryConstants.WEBSITE, session);
        MgnlContext.setInstance(webContext);
        _service = new RedirectsService() {

            @Override
            protected String getLinkFromNode(final Node node, final boolean isForward) {
                String link = "";
                if (node != null) {
                    try {
                        link = node.getPath() + ".html";
                    } catch (RepositoryException e) {
                        // should not happen
                    }
                }
                return link;
            }
        };

        RedirectsModule redirectsModule = new RedirectsModule();
        PublicUrlService publicUrlService = mock(PublicUrlService.class);
        when(publicUrlService.createTargetUrl(any())).thenReturn("http://www.noice.work/page.html");
        when(publicUrlService.createRedirectUrl(any())).thenReturn("http://www.noice.work/redirect");
        redirectsModule.setPublicUrlService(publicUrlService);
        _service.setRedirectsModule(() -> redirectsModule);
    }
}
