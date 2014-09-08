/*
 *  This file is part of the Wayback archival access software
 *   (http://archive-access.sourceforge.net/projects/wayback/).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual 
 *  contributors. 
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.archive.wayback.replay;

import java.util.List;

import org.archive.wayback.ReplayDispatcher;
import org.archive.wayback.ReplayRenderer;
import org.archive.wayback.core.CaptureSearchResult;
import org.archive.wayback.core.CaptureSearchResults;
import org.archive.wayback.core.Resource;
import org.archive.wayback.core.WaybackRequest;
import org.archive.wayback.exception.BetterRequestException;
import org.archive.wayback.memento.MementoConstants;
import org.archive.wayback.memento.MementoUtils;
import org.archive.wayback.replay.mimetype.MimeTypeDetector;
import org.archive.wayback.webapp.AccessPoint;

/**
 * ReplayDispatcher instance which uses a configurable ClosestResultSelector
 * to find the best result to show from a given set, and a list of 
 * ReplayRendererSelector to determine how best to replay that result to a user.
 *
 * <p>Optionally it can be configured with {@link MimeTypeDetector}s used for
 * overriding unknown ({@code "unk"}) or often-misused ({@code "text/html"})
 * value of {@link CaptureSearchResult#getMimeType()}.</p>
 *
 * @author brad
 */
public class SelectorReplayDispatcher implements ReplayDispatcher {
	private List<ReplayRendererSelector> selectors = null;
	private List<MimeTypeDetector> mimeTypeDetectors = null;
	private ClosestResultSelector closestSelector = null;
	
	/**
	 * check if mime-type detection is suggested for mimeType.
	 * @param mimeType mime-type to test (must not be null/empty/"unk")
	 * @return {@code true} if mime-type should be determined
	 * by looking into Resource.
	 */
	protected boolean shouldDetectMimeType(String mimeType) {
		// TODO: want to make this configurable?
		if (mimeType.startsWith("text/html"))
			return true;
		return false;
	}

	@Override
	public ReplayRenderer getRenderer(WaybackRequest wbRequest,
			CaptureSearchResult result, Resource resource) {
		// if content-type is already specified, don't override it.
		if (wbRequest.getForcedContentType() == null) {
			String mimeType = result.getMimeType();
			// TODO: this code should be encapsulated in CaptureSearchResult.getMimeType()
			if (AccessPoint.REVISIT_STR.equals(mimeType)) {
				if (result.getDuplicatePayload() != null) {
					mimeType = result.getDuplicatePayload().getMimeType();
				} else {
					// let following code get it from resource
					mimeType = null;
				}
			}
			// Many old ARCs have "unk" or "no-type" in ARC header even though
			// HTTP response has valid Content-Type header. CDX writer does not fix
			// it (although it's capable of fixing it internally). If CaptureSearchResult
			// says mimeType is "unk", try reading Content-Type header from the resource.
			if (mimeType == null || mimeType.isEmpty() || "unk".equals(mimeType)) {
				mimeType = resource.getHeader("Content-Type");
			}
			// "unk" and "" are changed to Content-Type header value (or null if in fact missing)
			// so null test is enough.
			if (mimeType == null || shouldDetectMimeType(mimeType)) {
				if (mimeTypeDetectors != null) {
					for (MimeTypeDetector detector : mimeTypeDetectors) {
						String detected = detector.sniff(resource);
						if (detected != null) {
							// detected mimeType is communicated to Selectors
							// through forcedContentType. better way? replace
							// CaptureSearchResult.mimeType?
							wbRequest.setForcedContentType(detected);
						}
					}
				}
			} else {
				// hmm, now CaptureSearchResult.mimeType can be set to
				// forcedContentType - it should work, but this may
				// be a bad design.
				wbRequest.setForcedContentType(mimeType);
			}
		}

		if (selectors != null) {
			for (ReplayRendererSelector selector : selectors) {
				if (selector.canHandle(wbRequest, result, resource, resource)) {
					return selector.getRenderer();
				}
			}
		}
		return null;
	}
	
	@Override
	public ReplayRenderer getRenderer(WaybackRequest wbRequest,
			CaptureSearchResult result, Resource httpHeadersResource,
			Resource payloadResource) {
		if (httpHeadersResource == payloadResource)
			return getRenderer(wbRequest, result, httpHeadersResource);
		else {
			Resource resource = new CompositeResource(httpHeadersResource, payloadResource);
			return getRenderer(wbRequest, result, resource);
		}
	}

	public CaptureSearchResult getClosest(WaybackRequest wbRequest,
			CaptureSearchResults results) throws BetterRequestException {
		
		try {
			return closestSelector.getClosest(wbRequest, results);
		} catch (BetterRequestException e) {
			
			if (wbRequest.isMementoEnabled()) {
				// Issue either a Memento URL-G response, or "intermediate resource" response
				if (wbRequest.isMementoTimegate()) {
					e.addHeader(MementoConstants.VARY, MementoConstants.NEGOTIATE_DATETIME);
					e.addHeader(MementoConstants.LINK, MementoUtils.generateMementoLinkHeaders(results, wbRequest, false, true));
				} else {
					e.addHeader(MementoConstants.LINK, MementoUtils.makeOrigHeader(wbRequest.getRequestUrl()));
				}
			}
			
			throw e;
		}
	}
	
	/**
	 * @return the List of ReplayRendererSelector objects configured
	 */
	public List<ReplayRendererSelector> getSelectors() {
		return selectors;
	}

	/**
	 * @param selectors the List of ReplayRendererSelector to use
	 */
	public void setSelectors(List<ReplayRendererSelector> selectors) {
		this.selectors = selectors;
	}

	public List<MimeTypeDetector> getMimeTypeDetectors() {
		return mimeTypeDetectors;
	}

	public void setMimeTypeDetectors(List<MimeTypeDetector> sniffers) {
		this.mimeTypeDetectors = sniffers;
	}

	/**
	 * @param closestSelector the closestSelector to set
	 */
	public void setClosestSelector(ClosestResultSelector closestSelector) {
		this.closestSelector = closestSelector;
	}
	/**
	 * @return the closestSelector
	 */
	public ClosestResultSelector getClosestSelector() {
		return closestSelector;
	}
}
