package org.eclipse.edc.dse.controlplane.catalog.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.restassured.specification.RequestSpecification;
import jakarta.json.JsonObject;
import org.eclipse.edc.api.management.schema.ManagementApiJsonSchema;
import org.eclipse.edc.jsonld.JsonLdExtension;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.junit.extensions.TestServiceExtensionContext;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.web.jersey.providers.jsonld.JerseyJsonLdInterceptor;
import org.eclipse.edc.web.jersey.testfixtures.RestControllerTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.api.management.ManagementApi.MANAGEMENT_SCOPE_V4;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.query.QuerySpec.EDC_QUERY_SPEC_TYPE;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HTTP-level test of the control-plane {@code POST /v4/catalogs/request} with the management v4 JSON-LD interceptor
 * registered as in {@code FederatedCatalogFilteringExtension}: the compact {@code @type} of a present body is validated
 * before expansion, an absent body means {@link QuerySpec#none()}, and the catalog filter's reply goes back to the
 * caller as is (status, {@code Content-Type}, body), without JSON-LD compaction.
 */
class CatalogFilteringApiValidationTest extends RestControllerTestBase {

    private static final String OWN_DID = "did:web:consumer";
    /**
     * Shaped like a real catalog filter reply (DSP 2025-1 compaction), with a non-ASCII value (U+00E9). Compacting it with
     * the management v4 scope would change {@code @context}, {@code @type} and the keys, so an unchanged body shows that
     * nothing post-processed it.
     */
    private static final String FILTER_CATALOGS = """
            [{"@id":"catalog-1","@type":"Catalog","participantId":"did:web:provider",\
            "dataset":[{"@id":"asset-1","@type":"Dataset","hasPolicy":[{"@id":"offer-1","@type":"Offer",\
            "permission":[{"action":"use","constraint":[{"leftOperand":"dse-policy:Membership","operator":"eq","rightOperand":"active"}]}]}],\
            "distribution":[{"@type":"Distribution","format":"HttpData-PULL"}],"edc:name":"Caf%s"}],\
            "@context":["https://w3id.org/dspace/2025/1/context.jsonld","https://w3id.org/edc/dspace/v0.0.1",\
            {"dse-policy":"https://w3id.org/dse/policy/"}]}]""".formatted(Character.toString(0xE9));

    private final AuthorityCatalogDidResolver resolver = mock();
    private final IdentityService identityService = mock();
    private final TypeTransformerRegistry transformerRegistry = mock();
    private final JsonObjectValidatorRegistry validatorRegistry = mock();
    private final JsonLd jsonLd = new JsonLdExtension().createJsonLdService(TestServiceExtensionContext.testServiceExtensionContext());
    private final AtomicReference<String> filterRequest = new AtomicReference<>();
    private final AtomicReference<FilterReply> filterReply = new AtomicReference<>(new FilterReply(200, "application/json", "[]"));

    private HttpServer filterServer;

    @BeforeEach
    void setUp() throws IOException {
        filterServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        filterServer.createContext("/filter", exchange -> {
            filterRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var reply = filterReply.get();
            var bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", reply.contentType());
            exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        filterServer.start();

        when(resolver.fetchCatalogFilterUrl())
                .thenReturn(Result.success("http://127.0.0.1:%d/filter".formatted(filterServer.getAddress().getPort())));
        when(identityService.obtainClientCredentials(eq(OWN_DID), any()))
                .thenReturn(Result.success(TokenRepresentation.Builder.newInstance().token("vc-token").build()));
        when(validatorRegistry.validate(any(), any())).thenReturn(ValidationResult.success());
        when(transformerRegistry.transform(any(JsonObject.class), eq(QuerySpec.class)))
                .thenReturn(Result.success(QuerySpec.Builder.newInstance().limit(7).build()));
    }

    @AfterEach
    void tearDown() {
        filterServer.stop(0);
    }

    @Test
    void fetchCatalog_withCompactQuerySpecType_isAccepted() throws Exception {
        baseRequest()
                .body(querySpec("QuerySpec"))
                .post()
                .then()
                .statusCode(200);

        verify(validatorRegistry).validate(eq("v4:QuerySpec"), any());
        var captor = ArgumentCaptor.forClass(JsonObject.class);
        verify(transformerRegistry).transform(captor.capture(), eq(QuerySpec.class));
        assertThat(captor.getValue().getJsonArray(TYPE).getString(0)).isEqualTo(EDC_QUERY_SPEC_TYPE);
        assertThat(new ObjectMapper().readTree(filterRequest.get()).get("query").get("limit").asInt()).isEqualTo(7);
    }

    @Test
    void fetchCatalog_withExpandedQuerySpecType_isBadRequest() {
        baseRequest()
                .body(querySpec(EDC_QUERY_SPEC_TYPE))
                .post()
                .then()
                .statusCode(400);

        verifyNoInteractions(transformerRegistry, resolver, identityService);
    }

    @Test
    void fetchCatalog_withWrongType_isBadRequest() {
        baseRequest()
                .body(querySpec("Asset"))
                .post()
                .then()
                .statusCode(400);

        verifyNoInteractions(transformerRegistry, resolver, identityService);
    }

    @Test
    void fetchCatalog_withoutType_isBadRequest() {
        baseRequest()
                .body(Map.of(CONTEXT, Map.of(VOCAB, EDC_NAMESPACE), "limit", 10))
                .post()
                .then()
                .statusCode(400);

        verifyNoInteractions(transformerRegistry, resolver, identityService);
    }

    @Test
    void fetchCatalog_withoutBody_usesDefaultQuery() throws Exception {
        baseRequest()
                .post()
                .then()
                .statusCode(200);

        verifyNoInteractions(transformerRegistry, validatorRegistry);
        assertThat(new ObjectMapper().readTree(filterRequest.get()).get("query").get("limit").asInt())
                .isEqualTo(QuerySpec.none().getLimit());
    }

    @Test
    void fetchCatalog_returnsTheFilterBodyUnchanged() {
        filterReply.set(new FilterReply(200, "application/json", FILTER_CATALOGS));

        var body = baseRequest()
                .body(querySpec("QuerySpec"))
                .post()
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().asByteArray();

        assertThat(body).isEqualTo(FILTER_CATALOGS.getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // EDC error bodies of the catalog filter (InvalidRequest, NotAuthorized, BadGateway), its 500 Jetty error page, and an empty body
            "400 | application/json             | [{\"message\":\"The catalog filter request must contain the participant token (tokenRepresentation)\",\"type\":\"InvalidRequest\",\"path\":null,\"invalidValue\":null}]",
            "403 | application/json             | [{\"message\":\"The participant token could not be verified\",\"type\":\"NotAuthorized\",\"path\":null,\"invalidValue\":null}]",
            "502 | application/json             | [{\"message\":\"The federated catalog query failed with HTTP 500\",\"type\":\"BadGateway\",\"path\":null,\"invalidValue\":null}]",
            "500 | text/html;charset=iso-8859-1 | <html><body><h2>HTTP ERROR 500 Internal Server Error</h2></body></html>",
            "503 | application/json             | ''"
    })
    void fetchCatalog_forwardsTheFilterErrorStatusAndBody(int status, String contentType, String body) {
        filterReply.set(new FilterReply(status, contentType, body));

        var response = baseRequest()
                .post()
                .then()
                .statusCode(status)
                .header("Content-Type", contentType)
                .extract();

        assertThat(response.body().asByteArray()).isEqualTo(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void fetchCatalog_whenFilterIsUnreachable_isBadGateway() throws IOException {
        try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            when(resolver.fetchCatalogFilterUrl()).thenReturn(Result.success("http://127.0.0.1:%d/filter".formatted(socket.getLocalPort())));
        }

        baseRequest()
                .post()
                .then()
                .statusCode(502)
                .contentType(JSON)
                .body("[0].type", is("BadGateway"))
                .body("[0].message", containsString("Could not reach the authority catalog filter"));
    }

    @Test
    void fetchCatalog_whenFilterUrlCannotBeResolved_isBadGateway() {
        when(resolver.fetchCatalogFilterUrl()).thenReturn(Result.failure("Could not find service with type 'FederatedCatalogFilterService' in DID document"));

        baseRequest()
                .post()
                .then()
                .statusCode(502)
                .contentType(JSON)
                .body("[0].type", is("BadGateway"))
                .body("[0].message", containsString("FederatedCatalogFilterService"));

        verifyNoInteractions(identityService);
    }

    @Test
    void fetchCatalog_isNoLongerServedOnTheOldDsePath() {
        // The control-plane catalog endpoint moved from the DSE-specific /v4/dse/catalogs/request to
        // /v4/catalogs/request (aligned with the federated catalog signature). The old path must 404.
        given()
                .port(port)
                .contentType(JSON)
                .basePath("/v4/dse/catalogs/request")
                .when()
                .post()
                .then()
                .statusCode(404);

        verifyNoInteractions(transformerRegistry, resolver, identityService);
    }

    @Override
    protected Object controller() {
        return new CatalogFilteringController(resolver, monitor, identityService, OWN_DID, Clock.systemUTC(),
                "did:web:authority", new ObjectMapper(), transformerRegistry);
    }

    @Override
    protected Object additionalResource() {
        return new JerseyJsonLdInterceptor(jsonLd, typeManager, "test", MANAGEMENT_SCOPE_V4, validatorRegistry, ManagementApiJsonSchema.V4.version());
    }

    private static Map<String, Object> querySpec(String type) {
        return Map.of(CONTEXT, Map.of(VOCAB, EDC_NAMESPACE), TYPE, type, "filterExpression", List.of());
    }

    private RequestSpecification baseRequest() {
        return given()
                .port(port)
                .contentType(JSON)
                .basePath("/v4/catalogs/request")
                .when();
    }

    private record FilterReply(int status, String contentType, String body) {
    }
}
