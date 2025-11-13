package org.eclipse.dse.core.issuerservice;

import org.eclipse.dse.spi.telemetry.TelemetryService;
import org.eclipse.dse.spi.telemetry.TelemetryServiceCredentialFactory;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.issuerservice.spi.holder.model.Holder;
import org.eclipse.edc.issuerservice.spi.holder.store.HolderStore;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.token.spi.TokenValidationService;
import org.eclipse.edc.transaction.spi.TransactionContext;

import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.ISSUER;

public class TelemetryServiceImpl implements TelemetryService {

    private final TokenValidationService tokenValidationService;
    private final DidPublicKeyResolver publicKeyResolver;
    private final HolderStore holderStore;
    private final TransactionContext transactionContext;
    private final TelemetryServiceCredentialFactory credentialFactory;

    public TelemetryServiceImpl(TokenValidationService tokenValidationService,
                                DidPublicKeyResolver publicKeyResolver,
                                HolderStore holderStore,
                                TransactionContext transactionContext,
                                TelemetryServiceCredentialFactory sasTokenFactory) {
        this.tokenValidationService = tokenValidationService;
        this.publicKeyResolver = publicKeyResolver;
        this.holderStore = holderStore;
        this.transactionContext = transactionContext;
        this.credentialFactory = sasTokenFactory;
    }

    @Override
    public ServiceResult<TokenRepresentation> createSasToken(TokenRepresentation tokenRepresentation) {
        var validationResult = tokenValidationService.validate(tokenRepresentation, publicKeyResolver);
        if (validationResult.failed()) {
            return ServiceResult.from(validationResult.mapEmpty());
        }

        var issuer = validationResult.getContent().getStringClaim(ISSUER);
        if (issuer == null) {
            return ServiceResult.badRequest("Missing '%s' claim in token".formatted(ISSUER));
        }

        return fetchHolder(issuer)
                .map(participant -> ServiceResult.from(credentialFactory.get()))
                .orElse(failure -> ServiceResult.unauthorized(failure.getFailureDetail()));
    }

    private ServiceResult<Holder> fetchHolder(String did) {
        var query = QuerySpec.Builder.newInstance()
                .filter(Criterion.Builder.newInstance()
                        .operandLeft("did")
                        .operator("=")
                        .operandRight(did)
                        .build())
                .build();

        return transactionContext.execute(() -> ServiceResult.from(holderStore.query(query)))
                .compose(participants -> participants.stream().findFirst()
                        .map(ServiceResult::success)
                        .orElse(ServiceResult.notFound("No holder with did: " + did)));
    }

}
