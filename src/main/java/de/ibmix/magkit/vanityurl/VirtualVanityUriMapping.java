package de.ibmix.magkit.vanityurl;

/*
 * #%L
 * magkit-vanity-url Magnolia Module
 * %%
 * Copyright (C) 2013 - 2014 IBM iX
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
 * Virtual Uri Mapping of vanity URLs managed in the vanity url app.
 *
 * @author frank.sommer
 */
public class VirtualVanityUriMapping implements VirtualUriMapping {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualVanityUriMapping.class);

    private Provider<VanityUrlModule> _vanityUrlModule;
    private Provider<VanityUrlService> _vanityUrlService;
    private Provider<SiteManager> _siteManager;

    @Inject
    public void setVanityUrlModule(final Provider<VanityUrlModule> vanityUrlModule) {
        _vanityUrlModule = vanityUrlModule;
    }

    @Inject
    public void setVanityUrlService(final Provider<VanityUrlService> vanityUrlService) {
        _vanityUrlService = vanityUrlService;
    }
    
    @Inject
    public void setSiteManager(final Provider<SiteManager> siteManager) {
        _siteManager = siteManager;
    }

    @Override
    public Optional<Result> mapUri(final URI uri) {
        Optional<Result> result = Optional.empty();
        try {
            String vanityUrl = extractPath(uri);
            
            if (isVanityCandidate(vanityUrl)) {
                LOGGER.debug("VanityUriMapping: URI is vanity candidate: {}", vanityUrl);
                
                // Extract site and clean vanity URL
                SiteUrlInfo siteUrlInfo = extractSiteFromUrl(vanityUrl);
                final String siteName = siteUrlInfo.getSiteName() != null ? siteUrlInfo.getSiteName() : retrieveSite(vanityUrl);
                final String cleanVanityUrl = siteUrlInfo.getVanityUrl();
                
                LOGGER.debug("VanityUriMapping: Site extraction - original: {}, extracted site: {}, clean vanity URL: {}", 
                    vanityUrl, siteName, cleanVanityUrl);
                
                String toUri = getUriOfVanityUrl(siteName, cleanVanityUrl, Optional.ofNullable(uri.getQuery()).map(value -> "?" + value).orElse(null));
                
                if (isNotBlank(toUri)) {
                    LOGGER.debug("VanityUriMapping: Found redirect mapping - from: {} (site: {}) to: {}", 
                        cleanVanityUrl, siteName, toUri);
                    result = Optional.of(new Result(toUri, vanityUrl.length(), this));
                } else {
                    LOGGER.debug("VanityUriMapping: No redirect mapping found for vanity URL: {} with site: {}", 
                        cleanVanityUrl, siteName);
                }
            }
        } catch (PatternSyntaxException e) {
            LOGGER.error("A vanity url exclude pattern is not set correctly.", e);
        }
        return result;
    }

    protected String extractPath(URI uri) {
        return uri.getPath();
    }

    protected boolean isVanityCandidate(String uri) {
        boolean contentUri = !isRootRequest(uri);
        if (contentUri) {
            Map<String, String> excludes = _vanityUrlModule.get().getExcludes();
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

    protected String getUriOfVanityUrl(String siteName, final String vanityUrl, String originSuffix) {
        Node node = null;
        Map<String, String> extractedParams = null;
        
        LOGGER.debug("VanityUriMapping: Searching for vanity URL node - vanityUrl: {}, siteName: {}, originSuffix: {}", 
            vanityUrl, siteName, originSuffix);

        try {
            // do it in the system context, so the anonymous need no read rights for using vanity urls
            node = MgnlContext.doInSystemContext(
                (MgnlContext.Op<Node, RepositoryException>) () -> _vanityUrlService.get().queryForVanityUrlNode(vanityUrl, siteName)
            );
            
            if (node != null) {
                LOGGER.debug("VanityUriMapping: Found exact match node for vanityUrl: {} with site: {}", vanityUrl, siteName);
            } else {
                LOGGER.debug("VanityUriMapping: No exact match found for vanityUrl: {} with site: {}, trying pattern matching", 
                    vanityUrl, siteName);
            }

            // If no exact match found, try pattern matching
            if (node == null) {
                final String finalVanityUrl = vanityUrl;
                List<Node> patternNodes = MgnlContext.doInSystemContext(
                    (MgnlContext.Op<List<Node>, RepositoryException>) () -> _vanityUrlService.get().queryForPatternVanityUrlNodes(siteName)
                );
                
                LOGGER.debug("VanityUriMapping: Found {} pattern nodes for site: {}", patternNodes.size(), siteName);

                // Check each pattern node for a match
                for (Node patternNode : patternNodes) {
                    // Try fromUrl field first (for redirects), then vanityUrl field (for vanity URLs)
                    String pattern = PropertyUtil.getString(patternNode, VanityUrlService.PN_FROM_URL, EMPTY);
                    if (isEmpty(pattern)) {
                        pattern = PropertyUtil.getString(patternNode, VanityUrlService.PN_VANITY_URL, EMPTY);
                    }
                    
                    if (isNotEmpty(pattern)) {
                        LOGGER.debug("VanityUriMapping: Testing pattern: {} against vanityUrl: {}", pattern, finalVanityUrl);
                        Map<String, String> params = VanityUrlService.matchPattern(finalVanityUrl, pattern);
                        if (params != null) {
                            LOGGER.debug("VanityUriMapping: Pattern match found! Pattern: {}, extracted params: {}", 
                                pattern, params);
                            node = patternNode;
                            extractedParams = params;
                            break;
                        }
                    }
                }
                
                if (node == null) {
                    LOGGER.debug("VanityUriMapping: No pattern matches found for vanityUrl: {} with site: {}", 
                        finalVanityUrl, siteName);
                }
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Error on querying for vanity url.", e);
        }

        String result = node == null ? EMPTY : createUrlForVanityNode(node, originSuffix, extractedParams);
        LOGGER.debug("VanityUriMapping: Final result for vanityUrl: {} with site: {} is: {}", 
            vanityUrl, siteName, result.isEmpty() ? "[EMPTY]" : result);
        return result;
    }

    /**
     * Override for alternative redirect url creation.
     *
     * @param node         vanity url node
     * @param originSuffix origin url suffix
     * @return redirect or forward url
     */
    protected String createUrlForVanityNode(final Node node, String originSuffix) {
        return createUrlForVanityNode(node, originSuffix, null);
    }

    /**
     * Override for alternative redirect url creation with pattern parameters.
     *
     * @param node         vanity url node
     * @param originSuffix origin url suffix
     * @param extractedParams parameters extracted from pattern matching
     * @return redirect or forward url
     */
    protected String createUrlForVanityNode(final Node node, String originSuffix, Map<String, String> extractedParams) {
        return _vanityUrlService.get().createRedirectUrl(node, originSuffix, extractedParams);
    }

    protected String retrieveSite(String vanityUrl) {
        final AggregationState aggregationState = MgnlContext.getAggregationState();
        Site site = aggregationState instanceof ExtendedAggregationState ? ((ExtendedAggregationState) aggregationState).getSite() : new NullSite();
        return site.getName();
    }

    @Override
    public boolean isValid() {
        return _vanityUrlService.get() != null;
    }

    public VanityUrlModule getVanityUrlModule() {
        return _vanityUrlModule.get();
    }

    public VanityUrlService getVanityUrlService() {
        return _vanityUrlService.get();
    }
    
    /**
     * Extracts site information from the vanity URL if it contains a site prefix.
     * 
     * @param vanityUrl the original vanity URL
     * @return SiteUrlInfo containing the extracted site name and cleaned vanity URL
     */
    protected SiteUrlInfo extractSiteFromUrl(String vanityUrl) {
        LOGGER.debug("VanityUriMapping: Extracting site from URL: {}", vanityUrl);
        
        String siteName = null;
        String cleanVanityUrl = vanityUrl;
        
        if (_siteManager != null && _siteManager.get() != null) {
            // Check if URL starts with a known site name
            if (vanityUrl.startsWith("/") && vanityUrl.length() > 1) {
                String pathWithoutLeadingSlash = removeStart(vanityUrl, "/");
                int nextSlashIndex = pathWithoutLeadingSlash.indexOf('/');
                
                LOGGER.debug("VanityUriMapping: Path without leading slash: {}, next slash index: {}", 
                    pathWithoutLeadingSlash, nextSlashIndex);
                
                if (nextSlashIndex > 0) {
                    String potentialSiteName = pathWithoutLeadingSlash.substring(0, nextSlashIndex);
                    String remainingPath = pathWithoutLeadingSlash.substring(nextSlashIndex);
                    
                    LOGGER.debug("VanityUriMapping: Checking potential site name: {}, remaining path: {}", 
                        potentialSiteName, remainingPath);
                    
                    // Check if this is a valid site name
                    try {
                        if (_siteManager.get().getSite(potentialSiteName) != null) {
                            LOGGER.debug("VanityUriMapping: Valid site found! Site: {}, extracted vanity URL: {}", 
                                potentialSiteName, remainingPath);
                            siteName = potentialSiteName;
                            cleanVanityUrl = remainingPath;
                        } else {
                            LOGGER.debug("VanityUriMapping: '{}' is not a valid site name", potentialSiteName);
                        }
                    } catch (Exception e) {
                        LOGGER.debug("VanityUriMapping: Exception checking site '{}': {}, treating as regular vanity URL", 
                            potentialSiteName, e.getMessage());
                    }
                }
            }
        } else {
            LOGGER.debug("VanityUriMapping: SiteManager not available, no site extraction possible");
        }
        
        LOGGER.debug("VanityUriMapping: Extraction complete - site: {}, vanityUrl: {}", 
            siteName != null ? siteName : "[null]", cleanVanityUrl);
        return new SiteUrlInfo(siteName, cleanVanityUrl);
    }
    
    /**
     * Helper class to hold site extraction results.
     */
    protected static class SiteUrlInfo {
        private final String _siteName;
        private final String _vanityUrl;
        
        public SiteUrlInfo(String siteName, String vanityUrl) {
            _siteName = siteName;
            _vanityUrl = vanityUrl;
        }
        
        public String getSiteName() {
            return _siteName;
        }
        
        public String getVanityUrl() {
            return _vanityUrl;
        }
    }
}
