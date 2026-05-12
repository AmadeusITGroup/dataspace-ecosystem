package org.eclipse.edc.api;

import org.eclipse.edc.FilterRequest;
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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void filter_shouldUseConfiguredParticipantContextId() throws Exception {
        var token = TokenRepresentation.Builder.newInstance().token("jwt").build();
        var claimToken = ClaimToken.Builder.newInstance().build();
        var request = new FilterRequest(token, "did:web:participant", QuerySpec.none());
        when(context.getSetting("edc.participant.id", "default-participant")).thenReturn("participant-1");
        when(identityService.verifyJwtToken(eq("participant-1"), eq(token), any(VerificationContext.class)))
                .thenReturn(Result.success(claimToken));
        when(catalogService.fetchAndFilterCatalog(claimToken, "did:web:participant", QuerySpec.none()))
                .thenReturn(List.of());

        var controller = new VcCatalogFilterController(context, monitor, catalogService, identityService, transformerRegistry);

        var response = controller.filter(request);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isEqualTo(List.of());
        verify(identityService).verifyJwtToken(eq("participant-1"), eq(token), any(VerificationContext.class));
    }

    @Test
    void filter_whenValidationFails_shouldReturnForbidden() {
        var token = TokenRepresentation.Builder.newInstance().token("jwt").build();
        var request = new FilterRequest(token, "did:web:participant", QuerySpec.none());
        when(context.getSetting("edc.participant.id", "default-participant")).thenReturn("participant-1");
        when(identityService.verifyJwtToken(eq("participant-1"), eq(token), any(VerificationContext.class)))
                .thenReturn(Result.failure("invalid token"));

        var controller = new VcCatalogFilterController(context, monitor, catalogService, identityService, transformerRegistry);

        var response = controller.filter(request);

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(transformerRegistry);
    }
}
