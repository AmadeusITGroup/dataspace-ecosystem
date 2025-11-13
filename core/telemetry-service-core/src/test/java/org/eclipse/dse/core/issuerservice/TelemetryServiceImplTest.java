package org.eclipse.dse.core.issuerservice;

import org.eclipse.dse.spi.telemetry.TelemetryService;
import org.eclipse.dse.spi.telemetry.TelemetryServiceCredentialFactory;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.issuerservice.spi.holder.model.Holder;
import org.eclipse.edc.issuerservice.spi.holder.store.HolderStore;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.token.spi.TokenValidationService;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.ISSUER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelemetryServiceImplTest {

    private final TokenValidationService tokenValidationService = mock();
    private final DidPublicKeyResolver didPublicKeyResolver = mock();
    private final HolderStore holderStore = mock();
    private final TransactionContext transactionContext = new NoopTransactionContext();
    private final TelemetryServiceCredentialFactory sasTokenFactory = mock();

    private final TelemetryService telemetryService = new TelemetryServiceImpl(tokenValidationService, didPublicKeyResolver, holderStore, transactionContext, sasTokenFactory);

    private static Holder createHolder(String did) {
        return Holder.Builder.newInstance()
                .holderId(UUID.randomUUID().toString())
                .did(did)
                .participantContextId("participant-id")
                .holderName("name")
                .build();
    }

    private static TokenRepresentation createToken() {
        return TokenRepresentation.Builder.newInstance()
                .token(UUID.randomUUID().toString())
                .build();
    }

    @Nested
    class CreateSasToken {

        @Test
        void success() {
            var holder = createHolder("did:web:" + UUID.randomUUID());
            var claims = ClaimToken.Builder.newInstance()
                    .claim(ISSUER, holder.getDid())
                    .build();
            var tr = createToken();
            var sasToken = createToken();

            when(tokenValidationService.validate(tr, didPublicKeyResolver)).thenReturn(Result.success(claims));
            when(holderStore.query(any())).thenReturn(StoreResult.success(List.of(holder)));
            when(sasTokenFactory.get()).thenReturn(Result.success(sasToken));

            var result = telemetryService.createSasToken(tr);

            assertThat(result.succeeded()).isTrue();
            assertThat(result.getContent().getToken()).isEqualTo(sasToken.getToken());
        }

        @Test
        void tokenValidationFails_shouldReturnUnauthorized() {
            var tr = createToken();

            when(tokenValidationService.validate(tr, didPublicKeyResolver)).thenReturn(Result.failure("Invalid token"));

            var result = telemetryService.createSasToken(tr);

            assertThat(result.succeeded()).isFalse();
            assertThat(result.getFailureDetail()).isEqualTo("Invalid token");
        }

        @Test
        void noHolderWithDid_shouldReturnUnauthorized() {
            var holder = createHolder("did:web:" + UUID.randomUUID());
            var claims = ClaimToken.Builder.newInstance()
                    .claim(ISSUER, holder.getDid())
                    .build();
            var tr = createToken();

            when(tokenValidationService.validate(tr, didPublicKeyResolver)).thenReturn(Result.success(claims));
            when(holderStore.query(any())).thenReturn(StoreResult.success(List.of()));

            var result = telemetryService.createSasToken(tr);

            assertThat(result.succeeded()).isFalse();
            assertThat(result.getFailureDetail()).isEqualTo("No holder with did: " + holder.getDid());
        }

    }

}