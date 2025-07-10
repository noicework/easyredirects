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

import info.magnolia.cms.core.AggregationState;
import info.magnolia.context.MgnlContext;
import info.magnolia.context.SystemContext;
import info.magnolia.context.WebContext;
import info.magnolia.module.site.NullSite;
import info.magnolia.module.site.Site;
import info.magnolia.module.site.SiteManager;
import info.magnolia.test.ComponentsTestUtil;
import info.magnolia.test.mock.jcr.MockNode;
import info.magnolia.virtualuri.VirtualUriMapping;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Provider;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test for mapping.
 *
 * @author frank.sommer
 * @since 28.05.14
 */
public class VirtualRedirectsUriMappingTest {

    private VirtualRedirectsUriMapping _uriMapping;

    @Test
    public void testRootRequest() throws Exception {
        Optional<VirtualUriMapping.Result> mappingResult = _uriMapping.mapUri(new URI("/"));
        assertThat(mappingResult, is(Optional.empty()));
    }

    @Test
    public void testPageRequest() throws Exception {
        Optional<VirtualUriMapping.Result> mappingResult = _uriMapping.mapUri(new URI("/home.html"));
        assertThat(mappingResult.isPresent(), is(false));
    }

    @Test
    public void testRedirectUrlWithoutTarget() throws Exception {
        Optional<VirtualUriMapping.Result> mappingResult = _uriMapping.mapUri(new URI("/home"));
        assertThat(mappingResult, is(Optional.empty()));
    }
    
    @Test
    public void testExtractSiteFromUrl() {
        VirtualRedirectsUriMapping.SiteUrlInfo result = _uriMapping.extractSiteFromUrl("/Dotmar/test");
        assertThat(result.getSiteName(), equalTo("Dotmar"));
        assertThat(result.getRedirect(), equalTo("/test"));
        
        // Test URL without site prefix
        result = _uriMapping.extractSiteFromUrl("/test");
        assertThat(result.getSiteName(), equalTo((String) null));
        assertThat(result.getRedirect(), equalTo("/test"));
        
        // Test URL with non-existent site
        result = _uriMapping.extractSiteFromUrl("/NonExistentSite/test");
        assertThat(result.getSiteName(), equalTo((String) null));
        assertThat(result.getRedirect(), equalTo("/NonExistentSite/test"));
    }

    @Test
    public void testRedirectUrlWithTarget() throws Exception {
        Optional<VirtualUriMapping.Result> mappingResult = _uriMapping.mapUri(new URI("/xmas"));
        assertThat(mappingResult, notNullValue());
        assertThat(mappingResult.isPresent() ? mappingResult.get().getToUri() : "", equalTo("redirect:/internal/page.html"));
    }
    
    @Test
    public void testSiteSpecificRedirectUrl() throws Exception {
        Optional<VirtualUriMapping.Result> mappingResult = _uriMapping.mapUri(new URI("/Dotmar/test"));
        assertThat(mappingResult, notNullValue());
        assertThat(mappingResult.isPresent() ? mappingResult.get().getToUri() : "", equalTo("redirect:/products.html"));
    }
    
    @Test
    public void testSiteSpecificRedirectUrlWithNonExistentSite() throws Exception {
        Optional<VirtualUriMapping.Result> mappingResult = _uriMapping.mapUri(new URI("/NonExistentSite/test"));
        assertThat(mappingResult, is(Optional.empty()));
    }

    @Before
    public void setUp() {
        _uriMapping = new VirtualRedirectsUriMapping();

        @SuppressWarnings("unchecked")
        Provider<RedirectsModule> moduleProvider = mock(Provider.class);
        RedirectsModule module = new RedirectsModule();
        Map<String, String> excludes = new HashMap<>();
        excludes.put("pages", ".*\\..*");
        module.setExcludes(excludes);
        when(moduleProvider.get()).thenReturn(module);
        _uriMapping.setRedirectsModule(moduleProvider);

        @SuppressWarnings("unchecked")
        Provider<RedirectsService> serviceProvider = mock(Provider.class);
        RedirectsService redirectsService = mock(RedirectsService.class);
        when(redirectsService.queryForRedirectNode("/home", NullSite.SITE_NAME)).thenReturn(null);

        MockNode mockNode = new MockNode("xmas");
        when(redirectsService.queryForRedirectNode("/xmas", NullSite.SITE_NAME)).thenReturn(mockNode);
        
        // Mock site-specific redirect URL
        MockNode siteSpecificNode = new MockNode("test");
        when(redirectsService.queryForRedirectNode("/test", "Dotmar")).thenReturn(siteSpecificNode);
        when(redirectsService.queryForRedirectNode("/test", NullSite.SITE_NAME)).thenReturn(null);

        when(redirectsService.createRedirectUrl(mockNode, null)).thenReturn("redirect:/internal/page.html");
        when(redirectsService.createRedirectUrl(mockNode, null, null)).thenReturn("redirect:/internal/page.html");
        when(redirectsService.createRedirectUrl(siteSpecificNode, null)).thenReturn("redirect:/products.html");
        when(redirectsService.createRedirectUrl(siteSpecificNode, null, null)).thenReturn("redirect:/products.html");
        when(serviceProvider.get()).thenReturn(redirectsService);
        _uriMapping.setRedirectsService(serviceProvider);
        
        // Mock SiteManager
        @SuppressWarnings("unchecked")
        Provider<SiteManager> siteManagerProvider = mock(Provider.class);
        SiteManager siteManager = mock(SiteManager.class);
        Site dotmarSite = mock(Site.class);
        when(dotmarSite.getName()).thenReturn("Dotmar");
        when(siteManager.getSite("Dotmar")).thenReturn(dotmarSite);
        when(siteManager.getSite("NonExistentSite")).thenReturn(null);
        when(siteManagerProvider.get()).thenReturn(siteManager);
        _uriMapping.setSiteManager(siteManagerProvider);

        initWebContext();
        initComponentProvider();
    }

    private void initComponentProvider() {
        SystemContext systemContext = mock(SystemContext.class);
        ComponentsTestUtil.setInstance(SystemContext.class, systemContext);
    }

    private void initWebContext() {
        WebContext webContext = mock(WebContext.class);
        AggregationState aggregationState = mock(AggregationState.class);
        when(webContext.getAggregationState()).thenReturn(aggregationState);
        MgnlContext.setInstance(webContext);
    }

    @After
    public void tearDown() {
        MgnlContext.setInstance(null);
    }
}
