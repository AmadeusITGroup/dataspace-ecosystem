package org.eclipse.edc.dse.controlplane.catalog.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.json.Json;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenParameters;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.spi.query.QuerySpec.EDC_QUERY_SPEC_TYPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CatalogFilteringControllerTest {

    private static final String OWN_DID = "did:web:consumer";
    private static final String AUTHORITY_DID = "did:web:authority";
    /**
     * A catalog as the authority catalog filter compacts it (DSP 2025-1 context), with a non-ASCII value (U+00E9, U+2615):
     * returned byte for byte.
     */
    private static final String FILTER_CATALOGS = """
            [ {"@id":"catalog-1","@type":"Catalog","participantId":"did:web:provider",
               "dataset":[{"@id":"asset-1","@type":"Dataset","edc:name":"Caf%s","hasPolicy":[{"@id":"offer-1","@type":"Offer"}]}],
               "@context":["https://w3id.org/dspace/2025/1/context.jsonld","https://w3id.org/edc/dspace/v0.0.1"]} ]
            """.formatted(Character.toString(0xE9) + " " + Character.toString(0x2615));

    private final AuthorityCatalogDidResolver resolver = mock();
    private final IdentityService identityService = mock();
    private final TypeTransformerRegistry transformerRegistry = mock();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);

    private final AtomicReference<FilterReply> filterReply = new AtomicReference<>(new FilterReply(200, "application/json", "[]"));
    private final AtomicReference<String> receivedBody = new AtomicReference<>();

    private HttpServer server;
    private CatalogFilteringController controller;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/filter", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var reply = filterReply.get();
            var bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            if (reply.contentType() != null) {
                exchange.getResponseHeaders().add("Content-Type", reply.contentType());
            }
            exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();

        when(resolver.fetchCatalogFilterUrl()).thenReturn(Result.success(filterUrl(server)));
        when(identityService.obtainClientCredentials(eq(OWN_DID), any()))
                .thenReturn(Result.success(TokenRepresentation.Builder.newInstance().token("vc-token").build()));

        controller = new CatalogFilteringController(resolver, mock(Monitor.class), identityService, OWN_DID, clock,
                AUTHORITY_DID, new ObjectMapper(), transformerRegistry);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void fetchCatalog_withoutBody_sendsDefaultQuery() throws Exception {
        controller.fetchCatalog(null);

        var sent = new ObjectMapper().readTree(receivedBody.get());
        assertThat(sent.get("participantDid").asText()).isEqualTo(OWN_DID);
        assertThat(sent.get("tokenRepresentation").get("token").asText()).isEqualTo("vc-token");
        assertThat(sent.get("query").get("limit").asInt()).isEqualTo(QuerySpec.none().getLimit());
        verifyNoInteractions(transformerRegistry);
    }

    @Test
    void fetchCatalog_requestsTokenForAuthorityWithReadAllScope() {
        controller.fetchCatalog(null);

        var captor = ArgumentCaptor.forClass(TokenParameters.class);
        verify(identityService).obtainClientCredentials(eq(OWN_DID), captor.capture());
        var claims = captor.getValue().getClaims();
        assertThat(claims.get("iss")).isEqualTo(OWN_DID);
        assertThat(claims.get("sub")).isEqualTo(OWN_DID);
        assertThat(claims.get("aud")).isEqualTo(AUTHORITY_DID);
        assertThat(claims.get("scope").toString()).endsWith(":VerifiableCredential:read");
        assertThat(claims.get("exp")).isEqualTo(clock.instant().plusSeconds(3600).getEpochSecond());
    }

    @Test
    void fetchCatalog_withQuerySpec_usesTransformedQuery() throws Exception {
        var body = Json.createObjectBuilder().add("@type", Json.createArrayBuilder().add(EDC_QUERY_SPEC_TYPE)).build();
        when(transformerRegistry.transform(body, QuerySpec.class))
                .thenReturn(Result.success(QuerySpec.Builder.newInstance().offset(5).limit(7).build()));

        controller.fetchCatalog(body);

        var sent = new ObjectMapper().readTree(receivedBody.get());
        assertThat(sent.get("query").get("offset").asInt()).isEqualTo(5);
        assertThat(sent.get("query").get("limit").asInt()).isEqualTo(7);
    }

    @Test
    void fetchCatalog_whenQuerySpecTransformationFails_isInvalidRequest() {
        var body = Json.createObjectBuilder().add("@type", EDC_QUERY_SPEC_TYPE).build();
        when(transformerRegistry.transform(body, QuerySpec.class)).thenReturn(Result.failure("bad query"));

        assertThatThrownBy(() -> controller.fetchCatalog(body)).isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(resolver);
    }

    @Test
    void fetchCatalog_returnsTheFilterReplyUnchanged() {
        filterReply.set(new FilterReply(200, "application/json", FILTER_CATALOGS));

        var response = controller.fetchCatalog(null);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMediaType()).hasToString("application/json");
        assertThat(bodyOf(response)).isEqualTo(FILTER_CATALOGS);
    }

    @Test
    void fetchCatalog_doesNotParseTheFilterReply() {
        var notCatalogs = "{\"error\":\"boom\"} [\"noise\", 42";
        filterReply.set(new FilterReply(200, "application/json", notCatalogs));

        var response = controller.fetchCatalog(null);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(bodyOf(response)).isEqualTo(notCatalogs);
    }

    @Test
    void fetchCatalog_whenFilterReturnsEmptyBody_returnsItAsIs() {
        filterReply.set(new FilterReply(200, "application/json", ""));

        var response = controller.fetchCatalog(null);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(bodyOf(response)).isEmpty();
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
    void fetchCatalog_propagatesTheFilterErrorStatusAndBody(int status, String contentType, String body) {
        filterReply.set(new FilterReply(status, contentType, body));

        var response = controller.fetchCatalog(null);

        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getHeaderString("Content-Type")).isEqualTo(contentType);
        assertThat(bodyOf(response)).isEqualTo(body);
    }

    @Test
    void fetchCatalog_whenTheFederatedCatalogRejectsTheSortField_returnsTheFilter400AsIs() {
        // the catalog filter reply to a federated catalog 400 for an unknown sort field (VcCatalogFilterApiTest)
        var filter400 = "[{\"message\":\"Cannot sort by 'doesNotExist': field does not exist in Dataset\",\"type\":\"InvalidRequest\",\"path\":null,\"invalidValue\":null}]";
        filterReply.set(new FilterReply(400, "application/json", filter400));
        var body = Json.createObjectBuilder().add("@type", EDC_QUERY_SPEC_TYPE).build();
        when(transformerRegistry.transform(body, QuerySpec.class))
                .thenReturn(Result.success(QuerySpec.Builder.newInstance().sortField("doesNotExist").build()));

        var response = controller.fetchCatalog(body);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getHeaderString("Content-Type")).isEqualTo("application/json");
        assertThat(bodyOf(response)).isEqualTo(filter400);
        assertThat(receivedBody.get()).contains("\"sortField\":\"doesNotExist\"");
    }

    @Test
    void fetchCatalog_whenFilterSendsNoContentType_isJson() {
        filterReply.set(new FilterReply(200, null, "[]"));

        var response = controller.fetchCatalog(null);

        assertThat(response.getMediaType()).hasToString("application/json");
    }

    @Test
    void fetchCatalog_whenFilterUrlCannotBeResolved_isBadGateway() {
        when(resolver.fetchCatalogFilterUrl()).thenReturn(Result.failure("no service endpoint"));

        assertThatThrownBy(() -> controller.fetchCatalog(null))
                .isInstanceOf(BadGatewayException.class)
                .hasMessageContaining("Could not resolve the authority catalog filter url")
                .hasMessageContaining("no service endpoint");
        verifyNoInteractions(identityService);
    }

    @Test
    void fetchCatalog_whenFilterIsUnreachable_isBadGateway() throws IOException {
        String unreachableUrl;
        try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            unreachableUrl = "http://127.0.0.1:%d/filter".formatted(socket.getLocalPort());
        }
        when(resolver.fetchCatalogFilterUrl()).thenReturn(Result.success(unreachableUrl));

        assertThatThrownBy(() -> controller.fetchCatalog(null))
                .isInstanceOf(BadGatewayException.class)
                .hasMessageContaining("Could not reach the authority catalog filter " + unreachableUrl);
    }

    @Test
    void fetchCatalog_whenFilterUrlIsInvalid_isBadGateway() {
        when(resolver.fetchCatalogFilterUrl()).thenReturn(Result.success("ftp://authority/filter"));

        assertThatThrownBy(() -> controller.fetchCatalog(null))
                .isInstanceOf(BadGatewayException.class)
                .hasMessageContaining("Could not reach the authority catalog filter ftp://authority/filter");
    }

    @Test
    void fetchCatalog_whenTokenCannotBeObtained_throwsWithoutCallingFilter() {
        when(identityService.obtainClientCredentials(eq(OWN_DID), any())).thenReturn(Result.failure("no token"));

        assertThatThrownBy(() -> controller.fetchCatalog(null))
                .isInstanceOf(EdcException.class)
                .isNotInstanceOf(BadGatewayException.class)
                .hasMessageContaining("Could not obtain the token for the authority catalog filter")
                .hasMessageContaining("no token");
        assertThat(receivedBody.get()).isNull();
    }

    private static String filterUrl(HttpServer server) {
        return "http://127.0.0.1:%d/filter".formatted(server.getAddress().getPort());
    }

    private static String bodyOf(Response response) {
        return new String((byte[]) response.getEntity(), StandardCharsets.UTF_8);
    }

    private record FilterReply(int status, String contentType, String body) {
    }
}
