package org.eclipse.dse.core.telemetry;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.eclipse.dse.spi.telemetry.TelemetryPolicy;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenParameters;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.TypeManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.http.client.testfixtures.HttpTestUtils.testHttpClient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultTelemetryServiceClientTest {

    private static final String AUTHORITY_DID = "authorityDid";
    private static final String OWN_DID = "ownDid";

    private MockWebServer server;
    private String serverUrl;
    private final TypeManager typeManager = new JacksonTypeManager();
    private final DidResolverRegistry didResolverRegistry = mock();
    private final IdentityService identityService = mock();
    private final PolicyEngine policyEngine = mock();
    private final TelemetryPolicy telemetryPolicy = mock();
    private TelemetryServiceClientImpl client;
    private final Clock clock = Clock.systemUTC();

    @BeforeEach
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        serverUrl = "http://" + server.getHostName() + ":" + server.getPort();

        when(telemetryPolicy.get()).thenReturn(Policy.Builder.newInstance().build());
        when(policyEngine.evaluate(any(), any())).thenReturn(Result.success());

        client = new TelemetryServiceClientImpl(testHttpClient(), typeManager, didResolverRegistry,
                AUTHORITY_DID, identityService, policyEngine, telemetryPolicy, OWN_DID, clock);
    }

    @AfterEach
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private DidDocument createDidDocument(Service... services) {
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
        void success() throws Exception {
            var service = new Service("test", "TelemetryServiceCredential", serverUrl);
            when(didResolverRegistry.resolve(AUTHORITY_DID)).thenReturn(Result.success(createDidDocument(service)));

            var accessToken = createToken();
            var sasToken = createToken();

            server.enqueue(new MockResponse()
                    .setBody(typeManager.writeValueAsString(sasToken))
                    .addHeader("Content-Type", "application/json"));

            when(identityService.obtainClientCredentials(any(), any(TokenParameters.class)))
                    .thenReturn(Result.success(TokenRepresentation.Builder.newInstance().token(accessToken.getToken()).build()));

            var result = client.fetchCredential();

            assertThat(result.succeeded()).isTrue();
            assertThat(result.getContent().getToken()).isEqualTo(sasToken.getToken());

            RecordedRequest request = server.takeRequest();
            assertThat(request.getHeader("Authorization")).isEqualTo(accessToken.getToken());
        }

        @Test
        void didResolutionFails_shouldFail() {
            when(didResolverRegistry.resolve(AUTHORITY_DID)).thenReturn(Result.failure("DID resolution failed"));

            var result = client.fetchCredential();

            assertThat(result.succeeded()).isFalse();
            assertThat(result.getFailureDetail()).isEqualTo("DID resolution failed");
        }

        @Test
        void didDocumentDoesNotContains_TelemetryServiceCredential_shouldFail() {
            var service = new Service("test", "unknown", serverUrl);
            when(didResolverRegistry.resolve(AUTHORITY_DID)).thenReturn(Result.success(createDidDocument(service)));

            var result = client.fetchCredential();

            assertThat(result.succeeded()).isFalse();
            assertThat(result.getFailureDetail()).contains("TelemetryServiceCredential");
        }

        @Test
        void httpCallFails_shouldReturnError() {
            var service = new Service("test", "TelemetryServiceCredential", serverUrl);
            when(didResolverRegistry.resolve(AUTHORITY_DID)).thenReturn(Result.success(createDidDocument(service)));

            server.enqueue(new MockResponse().setResponseCode(400));

            when(identityService.obtainClientCredentials(any(), any(TokenParameters.class)))
                    .thenReturn(Result.success(TokenRepresentation.Builder.newInstance().token("token").build()));

            var result = client.fetchCredential();

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureDetail()).contains("Server response");
        }

        @Test
        void obtainClientCredentialsFails_shouldPropagateFailure() {
            var service = new Service("test", "TelemetryServiceCredential", serverUrl);
            when(didResolverRegistry.resolve(AUTHORITY_DID)).thenReturn(Result.success(createDidDocument(service)));

            when(identityService.obtainClientCredentials(any(), any(TokenParameters.class)))
                    .thenReturn(Result.failure("identity service failure"));

            var result = client.fetchCredential();

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureDetail()).contains("identity service failure");
        }

        @Test
        void serverReturnsEmptyBody_shouldFail() {
            var service = new Service("test", "TelemetryServiceCredential", serverUrl);
            when(didResolverRegistry.resolve(AUTHORITY_DID)).thenReturn(Result.success(createDidDocument(service)));

            server.enqueue(new MockResponse().setResponseCode(200));

            when(identityService.obtainClientCredentials(any(), any(TokenParameters.class)))
                    .thenReturn(Result.success(TokenRepresentation.Builder.newInstance().token("token").build()));

            var result = client.fetchCredential();

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureDetail()).contains("Failed to parse response as TokenRepresentation");
        }

        @Test
        void audienceAndScopeAreSetOnTokenRequest() throws Exception {
            var service = new Service("test", "TelemetryServiceCredential", serverUrl);
            when(didResolverRegistry.resolve(AUTHORITY_DID)).thenReturn(Result.success(createDidDocument(service)));

            var accessToken = createToken();
            var sasToken = createToken();

            server.enqueue(new MockResponse()
                    .setBody(typeManager.writeValueAsString(sasToken))
                    .addHeader("Content-Type", "application/json"));

            ArgumentCaptor<TokenParameters> captor = ArgumentCaptor.forClass(TokenParameters.class);
            when(identityService.obtainClientCredentials(any(), captor.capture()))
                    .thenReturn(Result.success(TokenRepresentation.Builder.newInstance().token(accessToken.getToken()).build()));

            var result = client.fetchCredential();

            assertThat(result.succeeded()).isTrue();
            assertThat(captor.getValue().getStringClaim("aud")).isEqualTo(AUTHORITY_DID);
        }

        @Test
        void serverReturnsMalformedJson_shouldFail() {
            var service = new Service("test", "TelemetryServiceCredential", serverUrl);
            when(didResolverRegistry.resolve(AUTHORITY_DID)).thenReturn(Result.success(createDidDocument(service)));

            server.enqueue(new MockResponse().setBody("not-json").addHeader("Content-Type", "text/plain"));

            when(identityService.obtainClientCredentials(any(), any(TokenParameters.class)))
                    .thenReturn(Result.success(TokenRepresentation.Builder.newInstance().token("token").build()));

            var result = client.fetchCredential();

            assertThat(result.failed()).isTrue();
        }

        @Test
        void serverReturnsNotFound_shouldFail() {
            var service = new Service("test", "TelemetryServiceCredential", serverUrl);
            when(didResolverRegistry.resolve(AUTHORITY_DID)).thenReturn(Result.success(createDidDocument(service)));

            server.enqueue(new MockResponse().setResponseCode(404));

            when(identityService.obtainClientCredentials(any(), any(TokenParameters.class)))
                    .thenReturn(Result.success(TokenRepresentation.Builder.newInstance().token("token").build()));

            var result = client.fetchCredential();

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureDetail()).contains("Server response");
        }

        @Test
        void serverReturnsInternalServerError_shouldFail() {
            var service = new Service("test", "TelemetryServiceCredential", serverUrl);
            when(didResolverRegistry.resolve(AUTHORITY_DID)).thenReturn(Result.success(createDidDocument(service)));

            server.enqueue(new MockResponse().setResponseCode(500));

            when(identityService.obtainClientCredentials(any(), any(TokenParameters.class)))
                    .thenReturn(Result.success(TokenRepresentation.Builder.newInstance().token("token").build()));

            var result = client.fetchCredential();

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureDetail()).contains("Server response");
        }

        @Test
        void correctUrlIsUsed_shouldAppendSasTokenPath() throws Exception {
            var service = new Service("test", "TelemetryServiceCredential", serverUrl);
            when(didResolverRegistry.resolve(AUTHORITY_DID)).thenReturn(Result.success(createDidDocument(service)));

            var accessToken = createToken();
            var sasToken = createToken();

            server.enqueue(new MockResponse()
                    .setBody(typeManager.writeValueAsString(sasToken))
                    .addHeader("Content-Type", "application/json"));

            when(identityService.obtainClientCredentials(any(), any(TokenParameters.class)))
                    .thenReturn(Result.success(TokenRepresentation.Builder.newInstance().token(accessToken.getToken()).build()));

            var result = client.fetchCredential();

            assertThat(result.succeeded()).isTrue();
            RecordedRequest request = server.takeRequest();
            assertThat(request.getPath()).isEqualTo("/v1alpha/sas-token");
        }

        @Test
        void didDocumentWithMultipleServices_shouldSelectCorrectOne() throws Exception {
            var wrongService = new Service("wrong", "WrongServiceType", "http://wrong.example.com");
            var correctService = new Service("test", "TelemetryServiceCredential", serverUrl);
            var anotherWrongService = new Service("another", "AnotherType", "http://another.example.com");

            when(didResolverRegistry.resolve(AUTHORITY_DID))
                    .thenReturn(Result.success(createDidDocument(wrongService, correctService, anotherWrongService)));

            var accessToken = createToken();
            var sasToken = createToken();

            server.enqueue(new MockResponse()
                    .setBody(typeManager.writeValueAsString(sasToken))
                    .addHeader("Content-Type", "application/json"));

            when(identityService.obtainClientCredentials(any(), any(TokenParameters.class)))
                    .thenReturn(Result.success(TokenRepresentation.Builder.newInstance().token(accessToken.getToken()).build()));

            var result = client.fetchCredential();

            assertThat(result.succeeded()).isTrue();
            assertThat(result.getContent().getToken()).isEqualTo(sasToken.getToken());
        }

        @Test
        void emptyDidDocument_shouldFail() {
            when(didResolverRegistry.resolve(AUTHORITY_DID)).thenReturn(Result.success(createDidDocument()));

            var result = client.fetchCredential();

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureDetail()).contains("TelemetryServiceCredential");
        }
    }
}
