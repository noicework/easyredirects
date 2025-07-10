package work.noice.easyredirects;

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

import info.magnolia.cms.beans.config.ServerConfiguration;
import info.magnolia.test.mock.jcr.MockNode;
import org.junit.Before;
import org.junit.Test;

import static work.noice.easyredirects.RedirectsService.PN_LINK;
import static work.noice.easyredirects.RedirectsService.PN_REDIRECT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test for default public url service ({@link DefaultPublicUrlService}).
 *
 * @author frank.sommer
 * @since 16.10.14
 */
public class DefaultPublicUrlServiceTest {
    private DefaultPublicUrlService _service;

    @Test
    public void testExternalTarget() throws Exception {
        MockNode mockNode = new MockNode("node");
        mockNode.setProperty(PN_REDIRECT, "/noice");
        mockNode.setProperty(PN_LINK, "http://www.noice.work");

        assertThat(_service.createTargetUrl(mockNode), equalTo("http://www.noice.work"));
        assertThat(_service.createRedirectUrl(mockNode), equalTo("http://www.noice.work/noice"));
    }

    @Test
    public void testInternalTarget() throws Exception {
        MockNode mockNode = new MockNode("node");
        mockNode.setProperty(PN_REDIRECT, "/noice");
        mockNode.setProperty(PN_LINK, "123-456-789");

        assertThat(_service.createTargetUrl(mockNode), equalTo("http://www.noice.work/context/page.html"));
        assertThat(_service.createRedirectUrl(mockNode), equalTo("http://www.noice.work/noice"));
    }

    @Test
    public void testExternalTargetWithConfiguredTargetContextPath() throws Exception {
        MockNode mockNode = new MockNode("node");
        mockNode.setProperty(PN_REDIRECT, "/noice");
        mockNode.setProperty(PN_LINK, "http://www.noice.work");
        _service.setTargetContextPath("/public");

        assertThat(_service.createTargetUrl(mockNode), equalTo("http://www.noice.work"));
        assertThat(_service.createRedirectUrl(mockNode), equalTo("http://www.noice.work/public/noice"));
    }

    @Test
    public void testInternalTargetWithConfiguredTargetContextPath() throws Exception {
        MockNode mockNode = new MockNode("node");
        mockNode.setProperty(PN_REDIRECT, "/noice");
        mockNode.setProperty(PN_LINK, "123-456-789");
        _service.setTargetContextPath("/public");

        assertThat(_service.createTargetUrl(mockNode), equalTo("http://www.noice.work/context/page.html"));
        assertThat(_service.createRedirectUrl(mockNode), equalTo("http://www.noice.work/public/noice"));
    }

    @Before
    public void setUp() {
        _service = new DefaultPublicUrlService() {
            @Override
            public String getExternalLinkFromId(final String nodeId) {
                return "http://www.noice.work/context/page.html";
            }
        };

        ServerConfiguration serverConfiguration = mock(ServerConfiguration.class);
        when(serverConfiguration.getDefaultBaseUrl()).thenReturn("http://www.noice.work/author");
        _service.setServerConfiguration(serverConfiguration);
        _service.setContextPath("/author");
    }
}
