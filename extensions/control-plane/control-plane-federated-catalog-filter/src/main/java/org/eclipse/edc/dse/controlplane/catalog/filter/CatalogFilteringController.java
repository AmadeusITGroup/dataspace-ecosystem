package org.eclipse.edc.dse.controlplane.catalog.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.FilterRequest;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenParameters;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;
import org.eclipse.edc.web.spi.validation.SchemaType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.UUID;

import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.AUDIENCE;
import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.EXPIRATION_TIME;
import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.ISSUED_AT;
import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.ISSUER;
import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.JWT_ID;
import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.SCOPE;
import static org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames.SUBJECT;
import static org.eclipse.edc.spi.core.CoreConstants.DSE_VC_TYPE_SCOPE_ALIAS;
import static org.eclipse.edc.spi.query.QuerySpec.EDC_QUERY_SPEC_TYPE_TERM;

/**
 * Control-plane catalog endpoint {@code POST /v4/catalogs/request} of the management API. It keeps the same signature as
 * the federated catalog query API so the two stay aligned (when EDC-V lands, this DSE extension can be replaced by the
 * upstream endpoint with no client change). There is no path collision: the control-plane launcher excludes the upstream
 * federated-catalog-api module, so the upstream {@code /v4/catalogs/request} handler is not on its classpath.
 */
@Path("/v4/catalogs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CatalogFilteringController implements FederatedCatalogFilteringApiV2 {

    private static final String READ_ALL_CREDENTIAL_SCOPE = "%s:VerifiableCredential:read".formatted(DSE_VC_TYPE_SCOPE_ALIAS);

    private final AuthorityCatalogDidResolver authorityCatalogDidResolver;

    private final Monitor monitor;

    private final IdentityService identityService;

    private final String ownDid;

    private final Clock clock;

    private final String authorityDid;

    private final ObjectMapper mapper;

    private final TypeTransformerRegistry transformerRegistry;

    private final HttpClient httpClient;

    public CatalogFilteringController(AuthorityCatalogDidResolver authorityCatalogDidResolver, Monitor monitor,
                                      IdentityService identityService, String ownDid, Clock clock, String authorityDid, ObjectMapper mapper,
                                      TypeTransformerRegistry transformerRegistry) {
        this.authorityCatalogDidResolver = authorityCatalogDidResolver;
        this.monitor = monitor;
        this.identityService = identityService;
        this.ownDid = ownDid;
        this.clock = clock;
        this.authorityDid = authorityDid;
        this.mapper = mapper;
        this.transformerRegistry = transformerRegistry;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Forwards the query to the authority catalog filter and returns the filter's reply as is: same HTTP status, same
     * {@code Content-Type}, same body. The body is not parsed: on success it is the JSON array of catalogs compacted by
     * the filter (DSP 2025-1 context). The management v4 JSON-LD interceptor only handles the request (compact
     * {@code @type} validation, expansion); it does not compact the {@code byte[]} entity.
     * Failures before the filter replies use the EDC error mapping: {@code 400} invalid query, {@code 502} the filter URL
     * cannot be resolved or the filter cannot be reached, {@code 500} the credentials token cannot be obtained.
     */
    @POST
    @Path("/request")
    @Override
    public Response fetchCatalog(@SchemaType(EDC_QUERY_SPEC_TYPE_TERM) JsonObject querySpecJson) {
        var querySpec = toQuerySpec(querySpecJson);
        var catalogFilterUrl = authorityCatalogDidResolver.fetchCatalogFilterUrl()
                .orElseThrow(failure -> new BadGatewayException("Could not resolve the authority catalog filter url: " + failure.getFailureDetail()));
        var token = identityService.obtainClientCredentials(ownDid, tokenParameters())
                .orElseThrow(failure -> new EdcException("Could not obtain the token for the authority catalog filter: " + failure.getFailureDetail()));

        var filterReply = send(catalogFilterUrl, new FilterRequest(token, ownDid, querySpec));

        return Response.status(filterReply.statusCode())
                .type(filterReply.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(MediaType.APPLICATION_JSON))
                .entity(filterReply.body())
                .build();
    }

    private QuerySpec toQuerySpec(JsonObject querySpecJson) {
        if (querySpecJson == null) {
            return QuerySpec.none();
        }
        return transformerRegistry.transform(querySpecJson, QuerySpec.class)
                .orElseThrow(InvalidRequestException::new);
    }

    private TokenParameters tokenParameters() {
        var now = clock.instant();
        return TokenParameters.Builder.newInstance()
                .claims(ISSUER, ownDid)
                .claims(SUBJECT, ownDid)
                .claims(JWT_ID, UUID.randomUUID().toString())
                .claims(ISSUED_AT, now.getEpochSecond())
                .claims(EXPIRATION_TIME, now.plusSeconds(3600).getEpochSecond())
                .claims(SCOPE, READ_ALL_CREDENTIAL_SCOPE)
                .claims(AUDIENCE, authorityDid)
                .build();
    }

    private HttpResponse<byte[]> send(String catalogFilterUrl, FilterRequest filterRequest) {
        byte[] body;
        try {
            body = mapper.writeValueAsBytes(filterRequest);
        } catch (JsonProcessingException e) {
            throw new EdcException("Could not serialize the catalog filter request", e);
        }
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(catalogFilterUrl))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException | IllegalArgumentException e) {
            var message = "Could not reach the authority catalog filter %s: %s".formatted(catalogFilterUrl, e);
            monitor.warning(message, e);
            throw new BadGatewayException(message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EdcException("Interrupted while calling the authority catalog filter", e);
        }
    }
}
