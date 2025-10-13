package org.eclipse.eonax.core.telemetry;

import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.token.spi.TokenGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

import java.util.List;
import java.util.UUID;

import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.http.client.testfixtures.HttpTestUtils.testHttpClient;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultTelemetryServiceClientTest {

    private static final String PRIVATE_KEY_ID = "privateKeyId";
    private static final String OWN_DID = "ownDid";
    private static final String AUTHORITY_DID = "authorityDid";

    private final int port = getFreePort();
    private final ClientAndServer server = ClientAndServer.startClientAndServer(port);
    private final TypeManager typeManager = new JacksonTypeManager();
    private final TokenGenerationService tokenGenerationService = mock();
    private final DidResolverRegistry didResolverRegistry = mock();
    private TelemetryServiceClientImpl client;


    @BeforeEach
    public void setUp() {
        client = new TelemetryServiceClientImpl(testHttpClient(), typeManager, tokenGenerationService, PRIVATE_KEY_ID, didResolverRegistry, OWN_DID, AUTHORITY_DID);
    }

    private static DidDocument createDidDocument(Service... services) {
        return DidDocument.Builder.newInstance()
                .service(List.of(services))
                .build();
    }

    private static TokenRepresentation createToken() {
        return TokenRepresentation.Builder.newInstance()
                .token(UUID.randomUUID().toString())
                .build();
    }

    @Nested
    class FetchCredential {

        @Test
        void success() {
            var service = new Service("test", "TelemetryServiceCredential", "http://localhost:" + port);
            var didDocument = createDidDocument(service);
            when(didResolverRegistry.resolve(AUTHORITY_DID)).thenReturn(Result.success(didDocument));

            var accessToken = createToken();
            var sasToken = createToken();
            when(tokenGenerationService.generate(eq(PRIVATE_KEY_ID), any(), any())).thenReturn(Result.success(accessToken));

            var expectedRequest = HttpRequest.request().withHeader(AUTHORIZATION, accessToken.getToken());
            server.when(expectedRequest).respond(HttpResponse.response().withBody(typeManager.writeValueAsString(sasToken), MediaType.APPLICATION_JSON));

            var result = client.fetchCredential();

            assertThat(result.succeeded()).isTrue();
            assertThat(result.getContent().getToken()).isEqualTo(sasToken.getToken());
        }

        @Test
        void didResolutionFails_shouldFail() {
            when(didResolverRegistry.resolve(AUTHORITY_DID)).thenReturn(Result.failure("DID resolution failed"));

            Result<TokenRepresentation> result = client.fetchCredential();

            assertThat(result.succeeded()).isFalse();
            assertThat(result.getFailureDetail()).isEqualTo("DID resolution failed");
        }


        @Test
        void didDocumentDoesNotContains_TelemetryServiceCredential_shouldFail() {
            var service = new Service("test", "unknown", "http://localhost:" + port);
            var didDocument = createDidDocument(service);
            when(didResolverRegistry.resolve(AUTHORITY_DID)).thenReturn(Result.success(didDocument));

            var result = client.fetchCredential();

            assertThat(result.succeeded()).isFalse();
            assertThat(result.getFailureDetail()).contains("TelemetryServiceCredential");
        }

        @Test
        void httpCallFails_shouldReturnError() {
            var service = new Service("test", "TelemetryServiceCredential", "http://localhost:" + port);
            var didDocument = createDidDocument(service);
            when(didResolverRegistry.resolve(AUTHORITY_DID)).thenReturn(Result.success(didDocument));

            var accessToken = createToken();
            when(tokenGenerationService.generate(eq(PRIVATE_KEY_ID), any(), any())).thenReturn(Result.success(accessToken));

            var expectedRequest = HttpRequest.request().withHeader(AUTHORIZATION, accessToken.getToken());
            server.when(expectedRequest).respond(HttpResponse.response().withStatusCode(400));

            var result = client.fetchCredential();

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureDetail()).contains("Server response");
        }

    }

}