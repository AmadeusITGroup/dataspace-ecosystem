package org.eclipse.edc.api;

import com.sun.net.httpserver.HttpServer;
import io.restassured.response.ValidatableResponse;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.VerificationContext;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.util.AuthorityCatalogFilterDidResolver;
import org.eclipse.edc.util.FederatedCatalogService;
import org.eclipse.edc.web.jersey.providers.jsonld.JerseyJsonLdInterceptor;
import org.eclipse.edc.web.jersey.testfixtures.RestControllerTestBase;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.VcCatalogFilterExtension.CATALOG_FILTER_SCOPE;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * HTTP-level test of the catalog filter {@code POST /filter} with the EDC exception mappers of {@code JerseyRestService}
 * and a stub federated catalog: errors come back with the EDC JSON error body, and an error reply of the federated
 * catalog keeps its status and messages when an upstream EDC exception keeps them for that status (400, 401, 403, 409).
 */
class VcCatalogFilterApiTest extends RestControllerTestBase {

    private static final String PARTICIPANT_ID = "authority";
    private static final String PARTICIPANT_DID = "did:web:consumer";
    private static final Map<String, Object> FILTER_REQUEST = Map.of(
            "tokenRepresentation", Map.of("token", "vc-token"),
            "participantDid", PARTICIPANT_DID);

    private final ServiceExtensionContext context = mock();
    private final IdentityService identityService = mock();
    private final AuthorityCatalogFilterDidResolver didResolver = mock();
    private final AtomicReference<FederatedCatalogReply> federatedCatalogReply =
            new AtomicReference<>(new FederatedCatalogReply(200, "application/json", "[]"));

    private HttpServer federatedCatalog;

    @BeforeEach
    void setUp() throws IOException {
        federatedCatalog = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        federatedCatalog.createContext("/fc", exchange -> {
            exchange.getRequestBody().readAllBytes();
            var reply = federatedCatalogReply.get();
            var bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", reply.contentType());
            exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        federatedCatalog.start();

        when(didResolver.fetchCatalogFilterUrl())
                .thenReturn(Result.success("http://127.0.0.1:%d/fc".formatted(federatedCatalog.getAddress().getPort())));
        when(identityService.verifyJwtToken(eq(PARTICIPANT_ID), any(), any(VerificationContext.class)))
                .thenReturn(Result.success(ClaimToken.Builder.newInstance().claim("vc", List.of()).build()));
    }

    @AfterEach
    void tearDown() {
        federatedCatalog.stop(0);
    }

    @Test
    void filter_returnsTheFilteredCatalogs() {
        given().port(port).contentType(JSON).body(FILTER_REQUEST)
                .post("/filter")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("size()", is(0));
    }

    @Test
    void filter_withoutToken_isBadRequestWithEdcErrorBody() {
        var response = post(Map.of("participantDid", PARTICIPANT_DID))
                .statusCode(400);

        assertEdcError(response, "InvalidRequest", containsString("tokenRepresentation"));
    }

    @Test
    void filter_withInvalidToken_isForbiddenWithEdcErrorBody() {
        when(identityService.verifyJwtToken(eq(PARTICIPANT_ID), any(), any(VerificationContext.class)))
                .thenReturn(Result.failure("token expired"));

        var response = post(FILTER_REQUEST).statusCode(403);

        assertEdcError(response, "NotAuthorized", is("The participant token could not be verified"));
    }

    @Test
    void filter_whenTheFederatedCatalogRejectsAnUnknownSortField_isBadRequestWithItsMessage() {
        // the federated catalog 400 body for an unknown sort field (DatasetSortFieldValidator, federated-catalog-cache)
        federatedCatalogReply.set(new FederatedCatalogReply(400, "application/json",
                "[{\"message\":\"Cannot sort by 'doesNotExist': field does not exist in Dataset\",\"type\":\"ValidationFailure\",\"path\":\"sortField\",\"invalidValue\":\"doesNotExist\"}]"));

        var response = post(FILTER_REQUEST).statusCode(400);

        assertEdcError(response, "InvalidRequest", is("Cannot sort by 'doesNotExist': field does not exist in Dataset"));
        // the body the control plane forwards as is (CatalogFilteringControllerTest)
        assertThat(response.extract().body().asString()).isEqualTo(
                "[{\"message\":\"Cannot sort by 'doesNotExist': field does not exist in Dataset\",\"type\":\"InvalidRequest\",\"path\":null,\"invalidValue\":null}]");
    }

    @ParameterizedTest(name = "federated catalog {0} -> filter {0} {1}")
    @CsvSource({
            "400, InvalidRequest",
            "401, AuthenticationFailed",
            "403, NotAuthorized",
            "409, ObjectConflict"
    })
    void filter_keepsTheFederatedCatalogStatusAndMessage(int status, String type) {
        federatedCatalogReply.set(new FederatedCatalogReply(status, "application/json",
                "[{\"message\":\"federated catalog says no\",\"type\":\"Whatever\",\"path\":null,\"invalidValue\":null}]"));

        var response = post(FILTER_REQUEST).statusCode(status);

        assertEdcError(response, type, is("federated catalog says no"));
    }

    @ParameterizedTest(name = "federated catalog {0} -> filter 502")
    @CsvSource(delimiter = '|', value = {
            "404 | text/plain       | ''                                                           | The federated catalog query failed with HTTP 404",
            "500 | text/html        | <html><h2>HTTP ERROR 500 Internal Server Error</h2></html>   | The federated catalog query failed with HTTP 500",
            "503 | text/plain       | upstream unavailable                                         | The federated catalog query failed with HTTP 503",
            "504 | application/json | [{\"message\":\"timeout\",\"type\":\"X\"}]                   | The federated catalog query failed with HTTP 504: timeout"
    })
    void filter_whenTheFederatedCatalogFailsWithStatusWithoutEdcException_isBadGateway(int status, String contentType, String body, String message) {
        federatedCatalogReply.set(new FederatedCatalogReply(status, contentType, body));

        var response = post(FILTER_REQUEST).statusCode(502);

        assertEdcError(response, "BadGateway", is(message));
    }

    @Test
    void filter_whenTheFederatedCatalogCannotBeReached_isBadGateway() throws IOException {
        try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            when(didResolver.fetchCatalogFilterUrl()).thenReturn(Result.success("http://127.0.0.1:%d/fc".formatted(socket.getLocalPort())));
        }

        var response = post(FILTER_REQUEST).statusCode(502);

        assertEdcError(response, "BadGateway", containsString("Could not reach the federated catalog"));
    }

    @Test
    void filter_whenTheFederatedCatalogUrlCannotBeResolved_isBadGateway() {
        when(didResolver.fetchCatalogFilterUrl()).thenReturn(Result.failure("Could not find service with type 'FederatedCatalogService' in DID document"));

        var response = post(FILTER_REQUEST).statusCode(502);

        assertEdcError(response, "BadGateway", allOf(containsString("Could not resolve the federated catalog url"),
                containsString("FederatedCatalogService")));
    }

    @Test
    void filter_whenTheFederatedCatalogReplyCannotBeRead_isInternalServerErrorWithoutEdcErrorBody() {
        federatedCatalogReply.set(new FederatedCatalogReply(200, "application/json", "this is not a catalog array"));

        // unexpected failure: the EDC UnexpectedExceptionMapper answers 500 without an entity, so Jetty writes its error page
        var response = post(FILTER_REQUEST)
                .statusCode(500)
                .extract();

        assertThat(response.contentType()).startsWith("text/html");
        assertThat(response.body().asString()).contains("HTTP ERROR 500").doesNotContain("\"type\"");
    }

    @Override
    protected Object controller() {
        when(context.getSetting("edc.participant.id", "default-participant")).thenReturn(PARTICIPANT_ID);
        var catalogService = new FederatedCatalogService(mock(PolicyEngine.class), monitor, didResolver,
                mock(TypeTransformerRegistry.class), new TitaniumJsonLd(monitor), HttpClient.newHttpClient());
        return new VcCatalogFilterController(context, monitor, catalogService, identityService, mock(TypeTransformerRegistry.class));
    }

    @Override
    protected Object additionalResource() {
        // registered on the catalog filter context as in VcCatalogFilterExtension
        return new JerseyJsonLdInterceptor(new TitaniumJsonLd(monitor), typeManager, "test", CATALOG_FILTER_SCOPE);
    }

    private ValidatableResponse post(Map<String, Object> body) {
        return given().port(port).contentType(JSON).body(body)
                .post("/filter")
                .then()
                .log().body();
    }

    private static void assertEdcError(ValidatableResponse response, String type, Matcher<String> message) {
        response.contentType(JSON)
                .body("size()", is(1))
                .body("[0].type", is(type))
                .body("[0].message", message)
                .body("[0]", allOf(hasKey("message"), hasKey("type"), hasKey("path"), hasKey("invalidValue")));
    }

    private record FederatedCatalogReply(int status, String contentType, String body) {
    }
}
