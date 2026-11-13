package org.eclipse.edc.api;

import org.eclipse.edc.FilterRequest;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.iam.VerificationContext;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.util.FederatedCatalogService;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.eclipse.edc.web.spi.exception.EdcApiException;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;
import org.eclipse.edc.web.spi.exception.NotAuthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VcCatalogFilterControllerTest {

    private final ServiceExtensionContext context = mock();
    private final Monitor monitor = mock();
    private final FederatedCatalogService catalogService = mock();
    private final IdentityService identityService = mock();
    private final TypeTransformerRegistry transformerRegistry = mock();
    private final TokenRepresentation token = TokenRepresentation.Builder.newInstance().token("jwt").build();
    private final ClaimToken claimToken = ClaimToken.Builder.newInstance().build();
    private final FilterRequest request = new FilterRequest(token, "did:web:participant", QuerySpec.none());

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void filter_shouldUseConfiguredParticipantContextId() throws Exception {
        tokenIsValid();
        when(catalogService.fetchAndFilterCatalog(claimToken, "did:web:participant", QuerySpec.none()))
                .thenReturn(List.of());

        var response = controller().filter(request);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(List.of());
        verify(identityService).verifyJwtToken(eq("participant-1"), eq(token), any(VerificationContext.class));
    }

    @Test
    void filter_whenValidationFails_isNotAuthorized() {
        when(context.getSetting("edc.participant.id", "default-participant")).thenReturn("participant-1");
        when(identityService.verifyJwtToken(eq("participant-1"), eq(token), any(VerificationContext.class)))
                .thenReturn(Result.failure("invalid token"));

        assertThatThrownBy(() -> controller().filter(request))
                .isInstanceOf(NotAuthorizedException.class)
                .hasMessage("The participant token could not be verified");
        verifyNoInteractions(catalogService, transformerRegistry);
    }

    @Test
    void filter_withoutToken_isInvalidRequest() {
        var withoutToken = new FilterRequest(null, "did:web:participant", QuerySpec.none());

        assertThatThrownBy(() -> controller().filter(withoutToken))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("tokenRepresentation");
        verifyNoInteractions(identityService, catalogService);
    }

    @Test
    void filter_withoutBody_isInvalidRequest() {
        assertThatThrownBy(() -> controller().filter(null)).isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(identityService, catalogService);
    }

    @Test
    void filter_whenTheServiceThrowsAnEdcApiException_rethrowsIt() throws Exception {
        tokenIsValid();
        var federatedCatalogError = new InvalidRequestException("Cannot sort by 'x': field does not exist in Dataset");
        when(catalogService.fetchAndFilterCatalog(any(), any(), any())).thenThrow(federatedCatalogError);

        assertThatThrownBy(() -> controller().filter(request)).isSameAs(federatedCatalogError);
    }

    @Test
    void filter_whenTheFederatedCatalogCannotBeReached_rethrowsBadGateway() throws Exception {
        tokenIsValid();
        when(catalogService.fetchAndFilterCatalog(any(), any(), any())).thenThrow(new BadGatewayException("Could not reach the federated catalog"));

        assertThatThrownBy(() -> controller().filter(request)).isInstanceOf(BadGatewayException.class);
    }

    @Test
    void filter_whenTheServiceFailsUnexpectedly_throwsEdcException() throws Exception {
        tokenIsValid();
        when(catalogService.fetchAndFilterCatalog(any(), any(), any())).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> controller().filter(request))
                .isExactlyInstanceOf(EdcException.class)
                .isNotInstanceOf(EdcApiException.class)
                .hasMessageContaining("boom")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void filter_whenTheServiceThrowsAnIllegalArgumentException_throwsEdcException() throws Exception {
        tokenIsValid();
        when(catalogService.fetchAndFilterCatalog(any(), any(), any())).thenThrow(new IllegalArgumentException("bad url"));

        // not left to the EDC mapper of IllegalArgumentException, which would answer 400 for a server-side failure
        assertThatThrownBy(() -> controller().filter(request)).isExactlyInstanceOf(EdcException.class);
    }

    @Test
    void filter_whenInterrupted_throwsEdcExceptionAndKeepsTheInterruptFlag() throws Exception {
        tokenIsValid();
        when(catalogService.fetchAndFilterCatalog(any(), any(), any())).thenThrow(new InterruptedException());

        assertThatThrownBy(() -> controller().filter(request)).isExactlyInstanceOf(EdcException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    private void tokenIsValid() {
        when(context.getSetting("edc.participant.id", "default-participant")).thenReturn("participant-1");
        when(identityService.verifyJwtToken(eq("participant-1"), eq(token), any(VerificationContext.class)))
                .thenReturn(Result.success(claimToken));
    }

    private VcCatalogFilterController controller() {
        return new VcCatalogFilterController(context, monitor, catalogService, identityService, transformerRegistry);
    }
}
