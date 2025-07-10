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
import info.magnolia.module.site.ExtendedAggregationState;
import info.magnolia.module.site.NullSite;
import info.magnolia.module.site.Site;
import info.magnolia.virtualuri.VirtualUriMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.PatternSyntaxException;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.removeStart;
import info.magnolia.jcr.util.PropertyUtil;
import info.magnolia.module.site.SiteManager;

/**
 * Virtual Uri Mapping of redirects managed in the redirects app.
 *
 * @author frank.sommer
 */
public class VirtualRedirectsUriMapping implements VirtualUriMapping {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualRedirectsUriMapping.class);

    private Provider<RedirectsModule> _redirectsModule;
    private Provider<RedirectsService> _redirectsService;
    private Provider<SiteManager> _siteManager;

    @Inject
    public void setRedirectsModule(final Provider<RedirectsModule> redirectsModule) {
        _redirectsModule = redirectsModule;
    }

    @Inject
    public void setRedirectsService(final Provider<RedirectsService> redirectsService) {
        _redirectsService = redirectsService;
    }
    
    @Inject
    public void setSiteManager(final Provider<SiteManager> siteManager) {
        _siteManager = siteManager;
    }

    @Override
    public Optional<Result> mapUri(final URI uri) {
        Optional<Result> result = Optional.empty();
        try {
            String redirect = extractPath(uri);
            
            if (isRedirectCandidate(redirect)) {
                LOGGER.debug("RedirectsUriMapping: URI is redirect candidate: {}", redirect);
                
                // Extract site and clean redirect URL
                SiteUrlInfo siteUrlInfo = extractSiteFromUrl(redirect);
                final String siteName = siteUrlInfo.getSiteName() != null ? siteUrlInfo.getSiteName() : retrieveSite(redirect);
                final String cleanRedirect = siteUrlInfo.getRedirect();
                
                LOGGER.debug("RedirectsUriMapping: Site extraction - original: {}, extracted site: {}, clean redirect URL: {}", 
                    redirect, siteName, cleanRedirect);
                
                String toUri = getUriOfRedirect(siteName, cleanRedirect, Optional.ofNullable(uri.getQuery()).map(value -> "?" + value).orElse(null));
                
                if (isNotBlank(toUri)) {
                    LOGGER.debug("RedirectsUriMapping: Found redirect mapping - from: {} (site: {}) to: {}", 
                        cleanRedirect, siteName, toUri);
                    result = Optional.of(new Result(toUri, redirect.length(), this));
                } else {
                    LOGGER.debug("RedirectsUriMapping: No redirect mapping found for redirect URL: {} with site: {}", 
                        cleanRedirect, siteName);
                }
            }
        } catch (PatternSyntaxException e) {
            LOGGER.error("A redirect exclude pattern is not set correctly.", e);
        }
        return result;
    }

    protected String extractPath(URI uri) {
        return uri.getPath();
    }

    protected boolean isRedirectCandidate(String uri) {
        boolean contentUri = !isRootRequest(uri);
        if (contentUri) {
            Map<String, String> excludes = _redirectsModule.get().getExcludes();
            for (String exclude : excludes.values()) {
                if (isNotEmpty(exclude) && uri.matches(exclude)) {
                    contentUri = false;
                    break;
                }
            }
        }
        return contentUri;
    }

    private boolean isRootRequest(final String uri) {
        return uri.length() <= 1;
    }

    protected String getUriOfRedirect(String siteName, final String redirect, String originSuffix) {
        Node node = null;
        Map<String, String> extractedParams = null;
        
        LOGGER.debug("RedirectsUriMapping: Searching for redirect node - redirect: {}, siteName: {}, originSuffix: {}", 
            redirect, siteName, originSuffix);

        try {
            // do it in the system context, so the anonymous need no read rights for using redirects
            node = MgnlContext.doInSystemContext(
                (MgnlContext.Op<Node, RepositoryException>) () -> _redirectsService.get().queryForRedirectNode(redirect, siteName)
            );
            
            if (node != null) {
                LOGGER.debug("RedirectsUriMapping: Found exact match node for redirect: {} with site: {}", redirect, siteName);
            } else {
                LOGGER.debug("RedirectsUriMapping: No exact match found for redirect: {} with site: {}, trying pattern matching", 
                    redirect, siteName);
            }

            // If no exact match found, try pattern matching
            if (node == null) {
                final String finalRedirect = redirect;
                List<Node> patternNodes = MgnlContext.doInSystemContext(
                    (MgnlContext.Op<List<Node>, RepositoryException>) () -> _redirectsService.get().queryForPatternRedirectNodes(siteName)
                );
                
                LOGGER.debug("RedirectsUriMapping: Found {} pattern nodes for site: {}", patternNodes.size(), siteName);

                // Check each pattern node for a match
                for (Node patternNode : patternNodes) {
                    // Try fromUrl field first (for redirects), then redirect field (for redirects)
                    String pattern = PropertyUtil.getString(patternNode, RedirectsService.PN_FROM_URL, EMPTY);
                    if (isEmpty(pattern)) {
                        pattern = PropertyUtil.getString(patternNode, RedirectsService.PN_REDIRECT, EMPTY);
                    }
                    
                    if (isNotEmpty(pattern)) {
                        LOGGER.debug("RedirectsUriMapping: Testing pattern: {} against redirect: {}", pattern, finalRedirect);
                        Map<String, String> params = RedirectsService.matchPattern(finalRedirect, pattern);
                        if (params != null) {
                            LOGGER.debug("RedirectsUriMapping: Pattern match found! Pattern: {}, extracted params: {}", 
                                pattern, params);
                            node = patternNode;
                            extractedParams = params;
                            break;
                        }
                    }
                }
                
                if (node == null) {
                    LOGGER.debug("RedirectsUriMapping: No pattern matches found for redirect: {} with site: {}", 
                        finalRedirect, siteName);
                }
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Error on querying for redirect.", e);
        }

        String result = node == null ? EMPTY : createUrlForRedirectNode(node, originSuffix, extractedParams);
        LOGGER.debug("RedirectsUriMapping: Final result for redirect: {} with site: {} is: {}", 
            redirect, siteName, result.isEmpty() ? "[EMPTY]" : result);
        return result;
    }

    /**
     * Override for alternative redirect url creation.
     *
     * @param node         redirect node
     * @param originSuffix origin url suffix
     * @return redirect or forward url
     */
    protected String createUrlForRedirectNode(final Node node, String originSuffix) {
        return createUrlForRedirectNode(node, originSuffix, null);
    }

    /**
     * Override for alternative redirect url creation with pattern parameters.
     *
     * @param node         redirect node
     * @param originSuffix origin url suffix
     * @param extractedParams parameters extracted from pattern matching
     * @return redirect or forward url
     */
    protected String createUrlForRedirectNode(final Node node, String originSuffix, Map<String, String> extractedParams) {
        return _redirectsService.get().createRedirectUrl(node, originSuffix, extractedParams);
    }

    protected String retrieveSite(String redirect) {
        final AggregationState aggregationState = MgnlContext.getAggregationState();
        Site site = aggregationState instanceof ExtendedAggregationState ? ((ExtendedAggregationState) aggregationState).getSite() : new NullSite();
        return site.getName();
    }

    @Override
    public boolean isValid() {
        return _redirectsService.get() != null;
    }

    public RedirectsModule getRedirectsModule() {
        return _redirectsModule.get();
    }

    public RedirectsService getRedirectsService() {
        return _redirectsService.get();
    }
    
    /**
     * Extracts site information from the redirect URL if it contains a site prefix.
     * 
     * @param redirect the original redirect URL
     * @return SiteUrlInfo containing the extracted site name and cleaned redirect URL
     */
    protected SiteUrlInfo extractSiteFromUrl(String redirect) {
        LOGGER.debug("RedirectsUriMapping: Extracting site from URL: {}", redirect);
        
        String siteName = null;
        String cleanRedirect = redirect;
        
        if (_siteManager != null && _siteManager.get() != null) {
            // Check if URL starts with a known site name
            if (redirect.startsWith("/") && redirect.length() > 1) {
                String pathWithoutLeadingSlash = removeStart(redirect, "/");
                int nextSlashIndex = pathWithoutLeadingSlash.indexOf('/');
                
                LOGGER.debug("RedirectsUriMapping: Path without leading slash: {}, next slash index: {}", 
                    pathWithoutLeadingSlash, nextSlashIndex);
                
                if (nextSlashIndex > 0) {
                    String potentialSiteName = pathWithoutLeadingSlash.substring(0, nextSlashIndex);
                    String remainingPath = pathWithoutLeadingSlash.substring(nextSlashIndex);
                    
                    LOGGER.debug("RedirectsUriMapping: Checking potential site name: {}, remaining path: {}", 
                        potentialSiteName, remainingPath);
                    
                    // Check if this is a valid site name
                    try {
                        if (_siteManager.get().getSite(potentialSiteName) != null) {
                            LOGGER.debug("RedirectsUriMapping: Valid site found! Site: {}, extracted redirect URL: {}", 
                                potentialSiteName, remainingPath);
                            siteName = potentialSiteName;
                            cleanRedirect = remainingPath;
                        } else {
                            LOGGER.debug("RedirectsUriMapping: '{}' is not a valid site name", potentialSiteName);
                        }
                    } catch (Exception e) {
                        LOGGER.debug("RedirectsUriMapping: Exception checking site '{}': {}, treating as regular redirect URL", 
                            potentialSiteName, e.getMessage());
                    }
                }
            }
        } else {
            LOGGER.debug("RedirectsUriMapping: SiteManager not available, no site extraction possible");
        }
        
        LOGGER.debug("RedirectsUriMapping: Extraction complete - site: {}, redirect: {}", 
            siteName != null ? siteName : "[null]", cleanRedirect);
        return new SiteUrlInfo(siteName, cleanRedirect);
    }
    
    /**
     * Helper class to hold site extraction results.
     */
    protected static class SiteUrlInfo {
        private final String _siteName;
        private final String _redirect;
        
        public SiteUrlInfo(String siteName, String redirect) {
            _siteName = siteName;
            _redirect = redirect;
        }
        
        public String getSiteName() {
            return _siteName;
        }
        
        public String getRedirect() {
            return _redirect;
        }
    }
}
