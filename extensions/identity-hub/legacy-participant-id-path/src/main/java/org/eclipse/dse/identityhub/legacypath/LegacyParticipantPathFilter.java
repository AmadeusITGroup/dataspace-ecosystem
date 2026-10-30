package org.eclipse.dse.identityhub.legacypath;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;

import java.net.URI;

/**
 * Pre-matching JAX-RS filter applying {@link LegacyParticipantPathRewriter} before resource matching.
 */
@PreMatching
public class LegacyParticipantPathFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) {
        var uriInfo = requestContext.getUriInfo();
        var requestUri = uriInfo.getRequestUri();
        LegacyParticipantPathRewriter.rewrite(requestUri.getRawPath())
                .ifPresent((String newPath) -> requestContext.setRequestUri(uriInfo.getBaseUri(), withPath(requestUri, newPath)));
    }

    private static URI withPath(URI requestUri, String newRawPath) {
        var rawQuery = requestUri.getRawQuery();
        var query = (rawQuery == null) ? "" : ("?" + rawQuery);
        return URI.create(requestUri.getScheme() + "://" + requestUri.getRawAuthority() + newRawPath + query);
    }
}
