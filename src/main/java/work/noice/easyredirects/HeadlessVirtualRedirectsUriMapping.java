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

import jakarta.inject.Inject;
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

    private SiteManager _siteManager;

    @Override
    protected String extractPath(URI uri) {
        String path = EMPTY;

        final String headlessEndpoint = getRedirectsModule().getHeadlessEndpoint();
        if (isNotBlank(headlessEndpoint)) {
            path = substringAfter(uri.getPath(), headlessEndpoint);
        }

        return path;
    }

    @Override
    protected String retrieveSite(String redirect) {
        String siteName = _siteManager.getAssignedSite("", redirect).getName();
        return siteName;
    }
    
    @Override
    protected SiteUrlInfo extractSiteFromUrl(String redirect) {
        
        // For headless mode, use the site manager to determine site assignment
        // but still allow site prefix extraction for compatibility
        SiteUrlInfo siteInfo = super.extractSiteFromUrl(redirect);
        
        // If no site was extracted from URL, use the site manager assignment
        if (siteInfo.getSiteName() == null) {
            String assignedSiteName = _siteManager.getAssignedSite("", redirect).getName();
            return new SiteUrlInfo(assignedSiteName, siteInfo.getRedirect());
        }
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
