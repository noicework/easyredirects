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
 * Virtual Uri Mapping of vanity URLs managed in the vanity url app.
 *
 * @author frank.sommer
 */
public class HeadlessVirtualVanityUriMapping extends VirtualVanityUriMapping {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeadlessVirtualVanityUriMapping.class);

    private SiteManager _siteManager;

    @Override
    protected String extractPath(URI uri) {
        String path = EMPTY;
        
        LOGGER.debug("HeadlessVanityUriMapping: Extracting path from URI: {}", uri.toString());

        final String headlessEndpoint = getVanityUrlModule().getHeadlessEndpoint();
        if (isNotBlank(headlessEndpoint)) {
            path = substringAfter(uri.getPath(), headlessEndpoint);
            LOGGER.debug("HeadlessVanityUriMapping: Headless endpoint: {}, extracted path: {}", headlessEndpoint, path);
        } else {
            LOGGER.debug("HeadlessVanityUriMapping: No headless endpoint configured, using empty path");
        }

        return path;
    }

    @Override
    protected String retrieveSite(String vanityUrl) {
        String siteName = _siteManager.getAssignedSite("", vanityUrl).getName();
        LOGGER.debug("HeadlessVanityUriMapping: Retrieved site for vanityUrl {}: {}", vanityUrl, siteName);
        return siteName;
    }
    
    @Override
    protected SiteUrlInfo extractSiteFromUrl(String vanityUrl) {
        LOGGER.debug("HeadlessVanityUriMapping: Extracting site from URL in headless mode: {}", vanityUrl);
        
        // For headless mode, use the site manager to determine site assignment
        // but still allow site prefix extraction for compatibility
        SiteUrlInfo siteInfo = super.extractSiteFromUrl(vanityUrl);
        
        LOGGER.debug("HeadlessVanityUriMapping: Parent extraction result - site: {}, vanityUrl: {}", 
            siteInfo.getSiteName(), siteInfo.getVanityUrl());
        
        // If no site was extracted from URL, use the site manager assignment
        if (siteInfo.getSiteName() == null) {
            String assignedSiteName = _siteManager.getAssignedSite("", vanityUrl).getName();
            LOGGER.debug("HeadlessVanityUriMapping: No site extracted from URL, using site manager assignment: {}", 
                assignedSiteName);
            return new SiteUrlInfo(assignedSiteName, siteInfo.getVanityUrl());
        }
        
        LOGGER.debug("HeadlessVanityUriMapping: Using extracted site info - site: {}, vanityUrl: {}", 
            siteInfo.getSiteName(), siteInfo.getVanityUrl());
        return siteInfo;
    }

    @Override
    protected String getUriOfVanityUrl(String siteName, String vanityUrl, String originSuffix) {
        final Map<String, URI2RepositoryMapping> mappings = _siteManager.getSite(siteName).getMappings();
        String mappedUri = mappings.values().stream().filter(repositoryMapping -> WEBSITE.equals(repositoryMapping.getRepository())).findFirst().map(mapping -> vanityUrl.replace(mapping.getHandlePrefix(), defaultString(mapping.getURIPrefix()))).orElse(EMPTY);
        return super.getUriOfVanityUrl(siteName, mappedUri, originSuffix);
    }

    @Override
    protected String createUrlForVanityNode(Node node, String originSuffix) {
        return createUrlForVanityNode(node, originSuffix, null);
    }
    
    @Override
    protected String createUrlForVanityNode(Node node, String originSuffix, Map<String, String> extractedParams) {
        String redirectUrl = getVanityUrlService().createRedirectUrl(node, true, originSuffix, extractedParams);
        if (redirectUrl.startsWith(FORWARD_PREFIX)) {
            final String headlessEndpoint = getVanityUrlModule().getHeadlessEndpoint();
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
