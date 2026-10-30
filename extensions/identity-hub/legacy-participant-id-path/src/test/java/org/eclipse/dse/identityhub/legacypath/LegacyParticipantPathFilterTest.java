package org.eclipse.dse.identityhub.legacypath;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class LegacyParticipantPathFilterTest {

    private static final URI BASE_URI = URI.create("http://identityhub:8181/api/identity/");
    private static final String DID = "did:web:consumer-identityhub%3A8383:api:did";
    private static final String B64 = Base64.getUrlEncoder().withoutPadding().encodeToString(DID.getBytes(StandardCharsets.UTF_8));

    private final ContainerRequestContext requestContext = mock();
    private final UriInfo uriInfo = mock();
    private final LegacyParticipantPathFilter filter = new LegacyParticipantPathFilter();

    @BeforeEach
    void setUp() {
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getBaseUri()).thenReturn(BASE_URI);
    }

    @Test
    void shouldRewriteLegacyPathAndKeepQuery() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://identityhub:8181/api/identity/v1alpha/participants/" + B64 + "/keypairs?active=true"));

        filter.filter(requestContext);

        var newUri = ArgumentCaptor.forClass(URI.class);
        verify(requestContext).setRequestUri(eq(BASE_URI), newUri.capture());
        assertThat(newUri.getValue().getRawPath()).startsWith("/api/identity/v1beta/participants/did%3Aweb%3A").endsWith("/keypairs");
        assertThat(newUri.getValue().getRawQuery()).isEqualTo("active=true");
        assertThat(newUri.getValue().getRawAuthority()).isEqualTo("identityhub:8181");
    }

    @Test
    void shouldRewriteWithoutQuery() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://identityhub:8181/api/identity/v1alpha/participants/user/keypairs"));

        filter.filter(requestContext);

        var newUri = ArgumentCaptor.forClass(URI.class);
        verify(requestContext).setRequestUri(eq(BASE_URI), newUri.capture());
        assertThat(newUri.getValue()).isEqualTo(URI.create("http://identityhub:8181/api/identity/v1beta/participants/user/keypairs"));
    }

    @Test
    void shouldLeaveCurrentPathUntouched() {
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://identityhub:8181/api/identity/v1beta/participants/user/keypairs"));

        filter.filter(requestContext);

        verify(requestContext, never()).setRequestUri(any(), any());
    }
}
