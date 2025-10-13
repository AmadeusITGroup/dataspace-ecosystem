package org.eclipse.eonax.core.telemetry;

import com.nimbusds.jwt.JWTClaimNames;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.http.spi.FallbackFactories;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.spi.iam.TokenParameters;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.token.spi.KeyIdDecorator;
import org.eclipse.edc.token.spi.TokenDecorator;
import org.eclipse.edc.token.spi.TokenGenerationService;
import org.eclipse.eonax.edc.spi.telemetryagent.TelemetryServiceClient;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

import static org.eclipse.eonax.spi.telemetry.TelemetryServiceConstants.CREDENTIAL_URL_TYPE;


public class TelemetryServiceClientImpl implements TelemetryServiceClient {

    private final EdcHttpClient httpClient;
    private final TypeManager typeManager;
    private final TokenGenerationService tokenGenerationService;
    private final String privateKeyId;
    private final DidResolverRegistry didResolverRegistry;
    private final String ownDid;
    private final String authorityDid;

    TelemetryServiceClientImpl(EdcHttpClient httpClient, TypeManager typeManager, TokenGenerationService tokenGenerationService, String privateKeyId, DidResolverRegistry didResolverRegistry, String ownDid, String authorityDid) {
        this.httpClient = httpClient;
        this.typeManager = typeManager;
        this.tokenGenerationService = tokenGenerationService;
        this.privateKeyId = privateKeyId;
        this.didResolverRegistry = didResolverRegistry;
        this.ownDid = ownDid;
        this.authorityDid = authorityDid;
    }

    @Override
    public Result<TokenRepresentation> fetchCredential() {
        return didResolverRegistry.resolve(authorityDid)
                .compose(this::credentialUrl)
                .compose(this::requestCredential);
    }

    private Result<TokenRepresentation> requestCredential(String url) {
        return tokenGenerationService.generate(privateKeyId, new IssuerDecorator(ownDid), new KeyIdDecorator("%s#%s".formatted(ownDid, "my-key")))
                .map(tokenRepresentation -> createRequest(url + "/v1alpha/sas-token", tokenRepresentation.getToken()))
                .compose(request -> httpClient.execute(request, List.of(FallbackFactories.retryWhenStatusIsNotIn(200, 204)), this::handleResponse));
    }

    private Result<String> credentialUrl(DidDocument document) {
        return document.getService().stream()
                .filter(s -> s.getType().equals(CREDENTIAL_URL_TYPE))
                .findFirst()
                .map(value -> Result.success(value.getServiceEndpoint()))
                .orElseGet(() -> Result.failure("Could not find service with type '%s' in DID document".formatted(CREDENTIAL_URL_TYPE)));
    }

    private Request createRequest(String url, String token) {
        return new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .get()
                .build();
    }

    private Result<TokenRepresentation> handleResponse(Response response) {
        return getStringBody(response)
                .map(it -> typeManager.readValue(it, TokenRepresentation.class));
    }

    @NotNull
    private Result<String> getStringBody(Response response) {
        try (var body = response.body()) {
            if (body != null) {
                return Result.success(body.string());
            } else {
                return Result.failure("Body is null");
            }
        } catch (IOException e) {
            return Result.failure("Cannot read response body as String: " + e.getMessage());
        }
    }

    private record IssuerDecorator(String did) implements TokenDecorator {

        @Override
        public TokenParameters.Builder decorate(TokenParameters.Builder builder) {
            return builder.claims(JWTClaimNames.ISSUER, did);
        }
    }

}
