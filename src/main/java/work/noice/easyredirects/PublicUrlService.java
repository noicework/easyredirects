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

import info.magnolia.link.LinkUtil;

import javax.jcr.Node;

import static work.noice.easyredirects.RedirectsService.getNodeFromId;
import static org.apache.commons.lang3.StringUtils.EMPTY;

/**
 * Service for creating the public urls from author perspective.
 *
 * @author frank.sommer
 * @since 16.10.14
 */
public interface PublicUrlService {
    /**
     * Builds the redirect url for the public instance. Needed for the qr code generation on author instance.
     *
     * @param node redirect node
     * @return redirect url
     */
    String createRedirectUrl(Node node);

    /**
     * Creates the public url for displaying as target link in app view.
     *
     * @param node redirect node
     * @return public url
     */
    String createTargetUrl(Node node);

    default String getExternalLinkFromId(final String nodeId) {
        Node nodeFromId = getNodeFromId(nodeId);
        return nodeFromId == null ? EMPTY : LinkUtil.createExternalLink(nodeFromId);
    }
}
