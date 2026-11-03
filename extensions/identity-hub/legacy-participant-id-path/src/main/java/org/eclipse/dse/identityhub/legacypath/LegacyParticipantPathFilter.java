package org.eclipse.dse.identityhub.legacypath;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;

/**
 * Pre-matching JAX-RS filter applying {@link LegacyParticipantPathRewriter} before resource matching.
 *
 * <p>The rewritten path is already percent-encoded: {@code UriBuilder#replacePath} keeps existing {@code %XX}
 * sequences, so the encoded DID segment reaches resource matching unchanged. Scheme, authority and query are kept.
 */
@PreMatching
public class LegacyParticipantPathFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) {
        var uriInfo = requestContext.getUriInfo();
        LegacyParticipantPathRewriter.rewrite(uriInfo.getRequestUri().getRawPath())
                .ifPresent((String newPath) -> requestContext.setRequestUri(uriInfo.getBaseUri(),
                        uriInfo.getRequestUriBuilder().replacePath(newPath).build()));
    }
}
