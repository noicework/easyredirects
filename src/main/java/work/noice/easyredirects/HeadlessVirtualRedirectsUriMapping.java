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

import info.magnolia.cms.beans.config.URI2RepositoryMapping;
import info.magnolia.module.site.SiteManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.jcr.Node;
import java.net.URI;
import java.util.Map;

import static info.magnolia.cms.util.RequestDispatchUtil.FORWARD_PREFIX;
import static info.magnolia.repository.RepositoryConstants.WEBSITE;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.substringAfter;

/**
 * Virtual Uri Mapping of redirects managed in the redirects app.
 *
 * @author frank.sommer
 */
public class HeadlessVirtualRedirectsUriMapping extends VirtualRedirectsUriMapping {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeadlessVirtualRedirectsUriMapping.class);

    private SiteManager _siteManager;

    @Override
    protected String extractPath(URI uri) {
        String path = EMPTY;
        
        LOGGER.debug("HeadlessRedirectsUriMapping: Extracting path from URI: {}", uri.toString());

        final String headlessEndpoint = getRedirectsModule().getHeadlessEndpoint();
        if (isNotBlank(headlessEndpoint)) {
            path = substringAfter(uri.getPath(), headlessEndpoint);
            LOGGER.debug("HeadlessRedirectsUriMapping: Headless endpoint: {}, extracted path: {}", headlessEndpoint, path);
        } else {
            LOGGER.debug("HeadlessRedirectsUriMapping: No headless endpoint configured, using empty path");
        }

        return path;
    }

    @Override
    protected String retrieveSite(String redirect) {
        String siteName = _siteManager.getAssignedSite("", redirect).getName();
        LOGGER.debug("HeadlessRedirectsUriMapping: Retrieved site for redirect {}: {}", redirect, siteName);
        return siteName;
    }
    
    @Override
    protected SiteUrlInfo extractSiteFromUrl(String redirect) {
        LOGGER.debug("HeadlessRedirectsUriMapping: Extracting site from URL in headless mode: {}", redirect);
        
        // For headless mode, use the site manager to determine site assignment
        // but still allow site prefix extraction for compatibility
        SiteUrlInfo siteInfo = super.extractSiteFromUrl(redirect);
        
        LOGGER.debug("HeadlessRedirectsUriMapping: Parent extraction result - site: {}, redirect: {}", 
            siteInfo.getSiteName(), siteInfo.getRedirect());
        
        // If no site was extracted from URL, use the site manager assignment
        if (siteInfo.getSiteName() == null) {
            String assignedSiteName = _siteManager.getAssignedSite("", redirect).getName();
            LOGGER.debug("HeadlessRedirectsUriMapping: No site extracted from URL, using site manager assignment: {}", 
                assignedSiteName);
            return new SiteUrlInfo(assignedSiteName, siteInfo.getRedirect());
        }
        
        LOGGER.debug("HeadlessRedirectsUriMapping: Using extracted site info - site: {}, redirect: {}", 
            siteInfo.getSiteName(), siteInfo.getRedirect());
        return siteInfo;
    }

    @Override
    protected String getUriOfRedirect(String siteName, String redirect, String originSuffix) {
        final Map<String, URI2RepositoryMapping> mappings = _siteManager.getSite(siteName).getMappings();
        String mappedUri = mappings.values().stream().filter(repositoryMapping -> WEBSITE.equals(repositoryMapping.getRepository())).findFirst().map(mapping -> redirect.replace(mapping.getHandlePrefix(), defaultString(mapping.getURIPrefix()))).orElse(EMPTY);
        return super.getUriOfRedirect(siteName, mappedUri, originSuffix);
    }

    @Override
    protected String createUrlForRedirectNode(Node node, String originSuffix) {
        return createUrlForRedirectNode(node, originSuffix, null);
    }
    
    @Override
    protected String createUrlForRedirectNode(Node node, String originSuffix, Map<String, String> extractedParams) {
        String redirectUrl = getRedirectsService().createRedirectUrl(node, true, originSuffix, extractedParams);
        if (redirectUrl.startsWith(FORWARD_PREFIX)) {
            final String headlessEndpoint = getRedirectsModule().getHeadlessEndpoint();
            if (isNotBlank(headlessEndpoint)) {
                redirectUrl = FORWARD_PREFIX + headlessEndpoint + substringAfter(redirectUrl, FORWARD_PREFIX);
            }
        }
        return redirectUrl;
    }

    @Inject
    public void setSiteManager(SiteManager siteManager) {
        _siteManager = siteManager;
    }

}
