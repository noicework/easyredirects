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

import info.magnolia.jcr.util.NodeUtil;
import info.magnolia.jcr.util.PropertyUtil;
import info.magnolia.link.LinkUtil;
import info.magnolia.module.site.NullSite;
import org.apache.jackrabbit.value.StringValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static info.magnolia.cms.util.RequestDispatchUtil.FORWARD_PREFIX;
import static info.magnolia.cms.util.RequestDispatchUtil.PERMANENT_PREFIX;
import static info.magnolia.cms.util.RequestDispatchUtil.REDIRECT_PREFIX;
import static info.magnolia.context.MgnlContext.getJCRSession;
import static info.magnolia.jcr.util.NodeUtil.asIterable;
import static info.magnolia.jcr.util.NodeUtil.asList;
import static info.magnolia.jcr.util.PropertyUtil.getString;
import static info.magnolia.repository.RepositoryConstants.WEBSITE;
import static javax.jcr.query.Query.JCR_SQL2;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.startsWithIgnoreCase;
import static org.apache.commons.lang3.StringUtils.substringAfter;

/**
 * Query service for redirect nodes in redirects workspace.
 *
 * @author frank.sommer
 * @since 28.05.14
 */
public class RedirectsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedirectsService.class);

    private static final String QUERY = "select * from [" + RedirectsModule.NT_REDIRECT + "] where redirect = $redirect";
    private static final String QUERY_WITH_SITE = QUERY + " and site = $site";
    private static final String QUERY_PATTERN = "select * from [" + RedirectsModule.NT_REDIRECT + "] where usePattern = 'true'";
    private static final String QUERY_PATTERN_WITH_SITE = QUERY_PATTERN + " and site = $site";
    
    // Redirect-specific queries (for redirects app using fromUrl/toUrl)
    private static final String QUERY_REDIRECT = "select * from [" + RedirectsModule.NT_REDIRECT + "] where fromUrl = $fromUrl";
    private static final String QUERY_REDIRECT_WITH_SITE = QUERY_REDIRECT + " and site = $site";
    private static final String QUERY_REDIRECT_PATTERN = "select * from [" + RedirectsModule.NT_REDIRECT + "] where usePattern = 'true' and fromUrl is not null";
    private static final String QUERY_REDIRECT_PATTERN_WITH_SITE = QUERY_REDIRECT_PATTERN + " and site = $site";
    public static final String DEF_SITE = "default";
    public static final String PN_SITE = "site";
    public static final String PN_REDIRECT = "redirect";
    public static final String PN_LINK = "link";
    public static final String PN_SUFFIX = "linkSuffix";
    public static final String PN_TYPE = "type";
    public static final String PN_USE_PATTERN = "usePattern";
    
    // Redirect-specific property names
    public static final String PN_FROM_URL = "fromUrl";
    public static final String PN_TO_URL = "toUrl";
    public static final String PN_TO_URL_SUFFIX = "toUrlSuffix";
    public static final String PN_REDIRECT_TYPE = "redirectType";

    @Inject
    @Named(value = "magnolia.contextpath")
    private String _contextPath = "";

    private Provider<RedirectsModule> _redirectsModule;

    /**
     * Creates the redirect url for uri mapping.
     * Without context path, because of Magnolia's {@link info.magnolia.cms.util.RequestDispatchUtil}.
     *
     * @param node         redirect node
     * @param originSuffix origin url suffix
     * @return redirect url
     */
    protected String createRedirectUrl(final Node node, String originSuffix) {
        return createRedirectUrl(node, false, originSuffix);
    }

    /**
     * Creates the redirect url for uri mapping with pattern parameters.
     * Without context path, because of Magnolia's {@link info.magnolia.cms.util.RequestDispatchUtil}.
     *
     * @param node         redirect node
     * @param originSuffix origin url suffix
     * @param extractedParams parameters extracted from pattern matching
     * @return redirect url
     */
    protected String createRedirectUrl(final Node node, String originSuffix, Map<String, String> extractedParams) {
        return createRedirectUrl(node, false, originSuffix, extractedParams);
    }

    /**
     * Creates the redirect url for uri mapping.
     * Without context path, because of Magnolia's {@link info.magnolia.cms.util.RequestDispatchUtil}.
     *
     * @param node         redirect node
     * @param asExternal   external link flag
     * @param originSuffix origin url suffix
     * @return redirect url
     */
    protected String createRedirectUrl(final Node node, final boolean asExternal, final String originSuffix) {
        return createRedirectUrl(node, asExternal, originSuffix, null);
    }

    /**
     * Creates the redirect url for uri mapping with pattern parameters.
     * Without context path, because of Magnolia's {@link info.magnolia.cms.util.RequestDispatchUtil}.
     *
     * @param node         redirect node
     * @param asExternal   external link flag
     * @param originSuffix origin url suffix
     * @param extractedParams parameters extracted from pattern matching
     * @return redirect url
     */
    protected String createRedirectUrl(final Node node, final boolean asExternal, final String originSuffix, Map<String, String> extractedParams) {
        String result;
        // Try redirect type first (redirectType), then redirect type (type)
        String type = getString(node, PN_REDIRECT_TYPE, EMPTY);
        if (isEmpty(type)) {
            type = getString(node, PN_TYPE, EMPTY);
        }
        String prefix;
        
        if (node != null) {
            try {
                String nodePath = node.getPath();
                String redirect = getString(node, PN_REDIRECT, "");
                String link = getString(node, PN_LINK, "");
                LOGGER.debug("RedirectsService: Creating redirect URL for node: {}, redirect: {}, type: {}, link: {}, asExternal: {}, originSuffix: {}, extractedParams: {}", 
                    nodePath, redirect, type, link, asExternal, originSuffix, extractedParams);
            } catch (RepositoryException e) {
                LOGGER.warn("RedirectsService: Error reading node properties for logging: {}", e.getMessage());
            }
        } else {
            LOGGER.debug("RedirectsService: Creating redirect URL for null node, type: {}, asExternal: {}, originSuffix: {}, extractedParams: {}", 
                type, asExternal, originSuffix, extractedParams);
        }
        
        if ("forward".equals(type)) {
            result = createForwardLink(node, extractedParams);
            prefix = FORWARD_PREFIX;
            LOGGER.debug("RedirectsService: Created forward link: {}, using prefix: {}", result, prefix);
        } else {
            result = createTargetLink(node, asExternal, originSuffix, extractedParams);
            prefix = "301".equals(type) ? PERMANENT_PREFIX : REDIRECT_PREFIX;
            LOGGER.debug("RedirectsService: Created target link: {}, redirect type: {}, using prefix: {}", result, type, prefix);
        }
        
        if (isNotEmpty(result)) {
            result = prefix + result;
            LOGGER.debug("RedirectsService: Final redirect URL: {}", result);
        } else {
            LOGGER.debug("RedirectsService: Empty result for redirect URL creation");
        }
        
        return result;
    }

    /**
     * Creates the public url for displaying as target link in app view.
     *
     * @param node redirect node
     * @return public url
     */
    public String createPublicUrl(final Node node) {
        PublicUrlService publicUrlService = _redirectsModule.get().getPublicUrlService();
        return publicUrlService.createTargetUrl(node);
    }

    /**
     * Creates the redirect url for public instance.
     *
     * @param node redirect node
     * @return redirect url
     */
    public String createRedirectUrl(final Node node) {
        PublicUrlService publicUrlService = _redirectsModule.get().getPublicUrlService();
        return publicUrlService.createRedirectUrl(node);
    }

    /**
     * Creates the preview url for app preview.
     * Without contextPath, because of Magnolia's app framework.
     *
     * @param node redirect node
     * @return preview url
     */
    public String createPreviewUrl(final Node node) {
        return createTargetLink(node, false, null);
    }

    private String createForwardLink(final Node node) {
        return createForwardLink(node, null);
    }

    private String createForwardLink(final Node node, Map<String, String> extractedParams) {
        // nearly the same functionality as in createTargetLink. the only difference
        // is the clearing of the url if an external url had been configured
        return createTargetLink(node, true, false, null, extractedParams);
    }

    private String createTargetLink(final Node node, final boolean asExternal, String originSuffix) {
        return createTargetLink(node, false, asExternal, originSuffix, null);
    }

    private String createTargetLink(final Node node, final boolean asExternal, String originSuffix, Map<String, String> extractedParams) {
        return createTargetLink(node, false, asExternal, originSuffix, extractedParams);
    }

    private String createTargetLink(final Node node, final boolean isForward, final boolean asExternal, String originSuffix, Map<String, String> extractedParams) {
        String url = EMPTY;
        if (node != null) {
            // Try redirect field first (toUrl), then redirect field (link)
            String linkValue = getString(node, PN_TO_URL, EMPTY);
            if (isEmpty(linkValue)) {
                linkValue = getString(node, PN_LINK, EMPTY);
            }
            
            LOGGER.debug("RedirectsService: createTargetLink - node has toUrl: {}, link: {}, using: {}", 
                getString(node, PN_TO_URL, "[empty]"), getString(node, PN_LINK, "[empty]"), linkValue);
            
            if (isNotEmpty(linkValue)) {
                // Check if this is a toUrl (redirect) before placeholder replacement
                String toUrlValue = getString(node, PN_TO_URL, EMPTY);
                boolean isRedirectToUrl = isNotEmpty(toUrlValue);
                
                // Replace placeholders if we have extracted parameters
                if (extractedParams != null && !extractedParams.isEmpty()) {
                    linkValue = resolvePlaceholders(linkValue, extractedParams);
                }
                
                if (isExternalLink(linkValue)) {
                    // we won't allow external links in a forward
                    if (!isForward) {
                        url = linkValue;
                    }
                    LOGGER.debug("RedirectsService: External link detected: {}, isForward: {}, final url: {}", 
                        linkValue, isForward, url);
                } else {
                    if (isRedirectToUrl) {
                        // This is a redirect toUrl - use as path directly, with site resolution
                        url = resolveRedirectPath(linkValue, node, isForward, asExternal);
                        LOGGER.debug("RedirectsService: Redirect path detected: {}, resolved to: {}", 
                            linkValue, url);
                    } else {
                        // This is a redirect link - treat as node identifier
                        Node nodeFromId = getNodeFromId(linkValue);
                        url = createTargetLinkForPage(nodeFromId, isForward, asExternal);
                        LOGGER.debug("RedirectsService: Internal link, nodeFromId: {}, final url: {}", 
                            nodeFromId != null ? "found" : "null", url);
                    }
                }
            }

            if (isNotEmpty(url) && !isForward) {
                // Try redirect suffix first (toUrlSuffix), then redirect suffix (linkSuffix)
                String suffix = getString(node, PN_TO_URL_SUFFIX, EMPTY);
                if (isEmpty(suffix)) {
                    suffix = getString(node, PN_SUFFIX, originSuffix);
                } else if (isNotEmpty(originSuffix)) {
                    suffix = originSuffix;
                }
                
                // Replace placeholders in suffix if we have extracted parameters
                if (extractedParams != null && !extractedParams.isEmpty() && isNotEmpty(suffix)) {
                    suffix = resolvePlaceholders(suffix, extractedParams);
                }
                url += defaultString(suffix);
                
                LOGGER.debug("RedirectsService: Added suffix: {}, final url with suffix: {}", suffix, url);
            }
        }
        return url;
    }

    private String createTargetLinkForPage(Node pageNode, boolean isForward, boolean asExternal) {
        String url = EMPTY;
        if (pageNode != null) {
            if (asExternal) {
                url = LinkUtil.createExternalLink(pageNode);
            } else {
                url = getLinkFromNode(pageNode, isForward);
                if (isNotBlank(url) && url.contains(_contextPath)) {
                    url = substringAfter(url, _contextPath);
                }
            }
        }
        return url;
    }


    /**
     * Query for a redirect node.
     *
     * @param redirect redirect from request
     * @param siteName  site name from aggegation state
     * @return first redirect node of result or null, if nothing found
     */
    public Node queryForRedirectNode(final String redirect, final String siteName) {
        Node node = null;

        List<Node> nodes = queryForRedirectNodes(redirect, siteName);
        if (!nodes.isEmpty()) {
            node = nodes.get(0);
        }

        return node;
    }

    /**
     * Query for a redirect nodes.
     *
     * @param redirect redirect from request
     * @param siteName  site name from aggegation state
     * @return redirect nodes or empty list, if nothing found
     */
    public List<Node> queryForRedirectNodes(final String redirect, final String siteName) {
        List<Node> nodes = Collections.emptyList();
        
        LOGGER.debug("RedirectsService: Querying for redirect nodes - redirect: {}, siteName: {}", redirect, siteName);

        try {
            Session jcrSession = getJCRSession(RedirectsModule.WORKSPACE);
            QueryManager queryManager = jcrSession.getWorkspace().getQueryManager();

            // First try redirect query
            Query query;
            String queryString;
            if (NullSite.SITE_NAME.equals(siteName)) {
                queryString = QUERY;
                query = queryManager.createQuery(QUERY, JCR_SQL2);
                query.bindValue(PN_REDIRECT, new StringValue(redirect));
                LOGGER.debug("RedirectsService: Using redirect query without site filter: {}", queryString);
            } else {
                queryString = QUERY_WITH_SITE;
                query = queryManager.createQuery(QUERY_WITH_SITE, JCR_SQL2);
                query.bindValue(PN_REDIRECT, new StringValue(redirect));
                query.bindValue(PN_SITE, new StringValue(siteName));
                LOGGER.debug("RedirectsService: Using redirect query with site filter: {}, binding site: {}", queryString, siteName);
            }

            QueryResult queryResult = query.execute();
            nodes = asList(asIterable(queryResult.getNodes()));
            
            LOGGER.debug("RedirectsService: Redirect query executed, found {} nodes", nodes.size());
            
            // If no redirect nodes found, try redirect query
            if (nodes.isEmpty()) {
                LOGGER.debug("RedirectsService: No redirect nodes found, trying redirect query");
                
                if (NullSite.SITE_NAME.equals(siteName)) {
                    queryString = QUERY_REDIRECT;
                    query = queryManager.createQuery(QUERY_REDIRECT, JCR_SQL2);
                    query.bindValue(PN_FROM_URL, new StringValue(redirect));
                    LOGGER.debug("RedirectsService: Using redirect query without site filter: {}", queryString);
                } else {
                    queryString = QUERY_REDIRECT_WITH_SITE;
                    query = queryManager.createQuery(QUERY_REDIRECT_WITH_SITE, JCR_SQL2);
                    query.bindValue(PN_FROM_URL, new StringValue(redirect));
                    query.bindValue(PN_SITE, new StringValue(siteName));
                    LOGGER.debug("RedirectsService: Using redirect query with site filter: {}, binding site: {}", queryString, siteName);
                }
                
                queryResult = query.execute();
                nodes = asList(asIterable(queryResult.getNodes()));
                
                LOGGER.debug("RedirectsService: Redirect query executed, found {} nodes", nodes.size());
            }
                
            if (!nodes.isEmpty()) {
                for (int i = 0; i < nodes.size(); i++) {
                    Node node = nodes.get(i);
                    try {
                        String nodeRedirect = getString(node, PN_REDIRECT, "");
                        String nodeFromUrl = getString(node, PN_FROM_URL, "");
                        String nodeSite = getString(node, PN_SITE, "");
                        String nodeLink = getString(node, PN_LINK, "");
                        String nodeToUrl = getString(node, PN_TO_URL, "");
                        String nodeType = getString(node, PN_TYPE, "");
                        String nodeRedirectType = getString(node, PN_REDIRECT_TYPE, "");
                        LOGGER.debug("RedirectsService: Found node[{}] - path: {}, redirect: {}, fromUrl: {}, site: {}, link: {}, toUrl: {}, type: {}, redirectType: {}", 
                            i, node.getPath(), nodeRedirect, nodeFromUrl, nodeSite, nodeLink, nodeToUrl, nodeType, nodeRedirectType);
                    } catch (RepositoryException e) {
                        LOGGER.warn("RedirectsService: Error reading properties from node[{}]: {}", i, e.getMessage());
                    }
                }
            }
        } catch (RepositoryException e) {
            LOGGER.error("RedirectsService: Error executing query for redirect: {} with site: {}", redirect, siteName, e);
        }

        return nodes;
    }

    /**
     * Query for pattern-based redirect nodes.
     *
     * @param siteName site name from aggregation state
     * @return pattern-based redirect nodes or empty list, if nothing found
     */
    public List<Node> queryForPatternRedirectNodes(final String siteName) {
        List<Node> nodes = Collections.emptyList();
        
        LOGGER.debug("RedirectsService: Querying for pattern redirect nodes with siteName: {}", siteName);

        try {
            Session jcrSession = getJCRSession(RedirectsModule.WORKSPACE);
            QueryManager queryManager = jcrSession.getWorkspace().getQueryManager();

            // First try redirect pattern query
            Query query;
            String queryString;
            if (NullSite.SITE_NAME.equals(siteName)) {
                queryString = QUERY_PATTERN;
                query = queryManager.createQuery(QUERY_PATTERN, JCR_SQL2);
                LOGGER.debug("RedirectsService: Using redirect pattern query without site filter: {}", queryString);
            } else {
                queryString = QUERY_PATTERN_WITH_SITE;
                query = queryManager.createQuery(QUERY_PATTERN_WITH_SITE, JCR_SQL2);
                query.bindValue(PN_SITE, new StringValue(siteName));
                LOGGER.debug("RedirectsService: Using redirect pattern query with site filter: {}, binding site: {}", queryString, siteName);
            }

            QueryResult queryResult = query.execute();
            nodes = asList(asIterable(queryResult.getNodes()));
            
            LOGGER.debug("RedirectsService: Redirect pattern query executed, found {} pattern nodes", nodes.size());
            
            // Also try redirect pattern query
            List<Node> redirectNodes;
            if (NullSite.SITE_NAME.equals(siteName)) {
                queryString = QUERY_REDIRECT_PATTERN;
                query = queryManager.createQuery(QUERY_REDIRECT_PATTERN, JCR_SQL2);
                LOGGER.debug("RedirectsService: Using redirect pattern query without site filter: {}", queryString);
            } else {
                queryString = QUERY_REDIRECT_PATTERN_WITH_SITE;
                query = queryManager.createQuery(QUERY_REDIRECT_PATTERN_WITH_SITE, JCR_SQL2);
                query.bindValue(PN_SITE, new StringValue(siteName));
                LOGGER.debug("RedirectsService: Using redirect pattern query with site filter: {}, binding site: {}", queryString, siteName);
            }
            
            queryResult = query.execute();
            redirectNodes = asList(asIterable(queryResult.getNodes()));
            
            LOGGER.debug("RedirectsService: Redirect pattern query executed, found {} redirect pattern nodes", redirectNodes.size());
            
            // Combine both result sets
            if (!redirectNodes.isEmpty()) {
                if (nodes.isEmpty()) {
                    nodes = redirectNodes;
                } else {
                    // Create a new list combining both
                    List<Node> combinedNodes = new java.util.ArrayList<>(nodes);
                    combinedNodes.addAll(redirectNodes);
                    nodes = combinedNodes;
                }
            }
                
            for (int i = 0; i < nodes.size(); i++) {
                Node node = nodes.get(i);
                try {
                    String nodeRedirect = getString(node, PN_REDIRECT, "");
                    String nodeFromUrl = getString(node, PN_FROM_URL, "");
                    String nodeSite = getString(node, PN_SITE, "");
                    boolean usePattern = PropertyUtil.getBoolean(node, PN_USE_PATTERN, false);
                    LOGGER.info("RedirectsService: Pattern node[{}] - path: {}, redirect: {}, fromUrl: {}, site: {}, usePattern: {}", 
                        i, node.getPath(), nodeRedirect, nodeFromUrl, nodeSite, usePattern);
                } catch (RepositoryException e) {
                    LOGGER.warn("RedirectsService: Error reading properties from pattern node[{}]: {}", i, e.getMessage());
                }
            }
        } catch (RepositoryException e) {
            LOGGER.error("RedirectsService: Error querying pattern redirects for site: {}", siteName, e);
        }

        return nodes;
    }

    /**
     * Override for testing.
     */
    protected String getLinkFromNode(final Node node, boolean isForward) {
        return isForward ? NodeUtil.getPathIfPossible(node) : LinkUtil.createLink(node);
    }

    @Inject
    public void setRedirectsModule(final Provider<RedirectsModule> redirectsModule) {
        _redirectsModule = redirectsModule;
    }

    protected static Node getNodeFromId(final String nodeId) {
        Node node = null;
        try {
            Session jcrSession = getJCRSession(WEBSITE);
            node = jcrSession.getNodeByIdentifier(nodeId);
        } catch (RepositoryException e) {
            LOGGER.info("Error getting node for {}.", nodeId);
            LOGGER.debug("Error getting node for {}.", nodeId, e);
        }
        return node;
    }

    static boolean isExternalLink(String linkValue) {
        return startsWithIgnoreCase(linkValue, "https://") || startsWithIgnoreCase(linkValue, "http://");
    }

    /**
     * Converts a redirect pattern to a regex pattern.
     * Supports wildcards (*) and path parameters ({param}).
     *
     * @param pattern the redirect pattern
     * @return compiled regex pattern
     */
    public static Pattern convertToRegexPattern(String pattern) {
        String regex = pattern;
        
        LOGGER.debug("RedirectsService: Converting pattern: {}", pattern);
        
        // Check if this is already a regex pattern (contains unescaped parentheses or brackets)
        boolean isAlreadyRegex = pattern.contains("(") || pattern.contains("[") || pattern.contains("^") || pattern.contains("$");
        
        if (isAlreadyRegex) {
            // Pattern is already regex - use as-is but ensure anchoring
            if (!regex.startsWith("^")) {
                regex = "^" + regex;
            }
            if (!regex.endsWith("$")) {
                regex = regex + "$";
            }
            LOGGER.debug("RedirectsService: Using pattern as regex: {}", regex);
        } else {
            // Convert simple pattern to regex
            // Escape special regex characters except * and {}
            regex = regex.replaceAll("([.+?^$|\\\\\\[\\]()])", "\\\\$1");
            // Replace {param} with named capture group
            regex = regex.replaceAll("\\{([^}]+)\\}", "(?<$1>[^/]+)");
            // Replace * with .* for wildcard matching
            regex = regex.replaceAll("\\*", ".*");
            // Ensure exact match
            regex = "^" + regex + "$";
            LOGGER.debug("RedirectsService: Converted simple pattern to regex: {}", regex);
        }
        
        return Pattern.compile(regex);
    }

    /**
     * Matches a request URL against a pattern and extracts parameters.
     *
     * @param requestUrl the incoming request URL
     * @param pattern the redirect pattern
     * @return map of extracted parameters or null if no match
     */
    public static Map<String, String> matchPattern(String requestUrl, String pattern) {
        Pattern regex = convertToRegexPattern(pattern);
        Matcher matcher = regex.matcher(requestUrl);
        
        LOGGER.debug("RedirectsService: matchPattern - requestUrl: {}, pattern: {}, regex: {}", 
            requestUrl, pattern, regex.pattern());
        
        if (matcher.matches()) {
            Map<String, String> params = new HashMap<>();
            
            // First, extract numbered groups (for $1, $2, etc.)
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String groupValue = matcher.group(i);
                if (groupValue != null) {
                    params.put(String.valueOf(i), groupValue);
                    LOGGER.debug("RedirectsService: Captured group {}: {}", i, groupValue);
                }
            }
            
            // Also extract named groups (for {name} placeholders)
            Pattern namedGroupPattern = Pattern.compile("\\(\\?<([^>]+)>");
            Matcher groupMatcher = namedGroupPattern.matcher(regex.pattern());
            while (groupMatcher.find()) {
                String groupName = groupMatcher.group(1);
                try {
                    String groupValue = matcher.group(groupName);
                    if (groupValue != null) {
                        params.put(groupName, groupValue);
                        LOGGER.debug("RedirectsService: Captured named group {}: {}", groupName, groupValue);
                    }
                } catch (IllegalArgumentException e) {
                    // Group not found, skip
                }
            }
            
            LOGGER.debug("RedirectsService: Pattern matched! Extracted params: {}", params);
            return params;
        }
        
        LOGGER.debug("RedirectsService: Pattern did not match");
        return null;
    }

    /**
     * Replaces placeholders in target URL with extracted parameters.
     *
     * @param targetUrl the target URL with placeholders
     * @param parameters extracted parameters from pattern matching
     * @return resolved target URL
     */
    public static String resolvePlaceholders(String targetUrl, Map<String, String> parameters) {
        String resolved = targetUrl;
        
        LOGGER.debug("RedirectsService: resolvePlaceholders - targetUrl: {}, parameters: {}", targetUrl, parameters);
        
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            // Handle both {name} and $1 style placeholders
            String bracketPlaceholder = "{" + key + "}";
            String dollarPlaceholder = "$" + key;
            
            if (resolved.contains(bracketPlaceholder)) {
                resolved = resolved.replace(bracketPlaceholder, value);
                LOGGER.debug("RedirectsService: Replaced {} with {}", bracketPlaceholder, value);
            }
            
            if (resolved.contains(dollarPlaceholder)) {
                resolved = resolved.replace(dollarPlaceholder, value);
                LOGGER.debug("RedirectsService: Replaced {} with {}", dollarPlaceholder, value);
            }
        }
        
        LOGGER.debug("RedirectsService: Final resolved URL: {}", resolved);
        return resolved;
    }
    
    /**
     * Resolves a redirect path. When using public instance with site mapping,
     * returns the path as-is to let Magnolia's site mapping handle routing.
     * 
     * @param redirectPath the redirect path (e.g., "/products")
     * @param redirectNode the redirect node containing site information
     * @param isForward whether this is a forward
     * @param asExternal whether to create external link
     * @return resolved URL
     */
    private String resolveRedirectPath(String redirectPath, Node redirectNode, boolean isForward, boolean asExternal) {
        String url = EMPTY;
        
        try {
            String siteName = getString(redirectNode, PN_SITE, EMPTY);
            LOGGER.debug("RedirectsService: Resolving redirect path: {} for site: {}", redirectPath, siteName);
            
            if (isExternalLink(redirectPath)) {
                // External URL - use as is
                url = redirectPath;
                LOGGER.debug("RedirectsService: External redirect path: {}", url);
            } else {
                // Internal path - for redirects, use path as-is since site mapping handles the routing
                // Only add site prefix if the path explicitly starts with a different site name
                url = redirectPath;
                
                LOGGER.debug("RedirectsService: Using redirect path as-is (site mapping will handle routing): {} -> {}", redirectPath, url);
                
                // If this is for external use and we have a context path, we might need to adjust
                if (asExternal && isNotBlank(_contextPath) && !url.startsWith(_contextPath)) {
                    url = _contextPath + url;
                    LOGGER.debug("RedirectsService: Added context path for external use: {}", url);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("RedirectsService: Error resolving redirect path: {}", redirectPath, e);
            // Fall back to original path
            url = redirectPath;
        }
        
        return url;
    }
}
