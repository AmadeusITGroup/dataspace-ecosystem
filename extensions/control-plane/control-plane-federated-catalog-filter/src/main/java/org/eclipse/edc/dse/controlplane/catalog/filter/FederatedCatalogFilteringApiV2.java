package org.eclipse.edc.dse.controlplane.catalog.filter;


import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.api.management.schema.ManagementApiJsonSchema;

@OpenAPIDefinition(security = {@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "apiKeyAuth")})
@Tag(name = "Federated Catalog Filter Endpoint for Participant Control-Plane",
        description = "A service that allows participants to call the filtering of the federated catalog")
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
@SecurityScheme(
        name = "apiKeyAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "x-api-key"
)
public interface FederatedCatalogFilteringApiV2 {

    @Operation(description = "Returns the federated catalog filtered for this participant. DSE endpoint: it is not the upstream federated " +
            "catalog query API (POST /v4/catalogs/request) and has its own path. The control plane sends the QuerySpec to the " +
            "authority catalog filter, which queries the federated catalog and removes the datasets this participant may not see, " +
            "and returns the filter's reply as is: same status, Content-Type and body, no JSON-LD processing. " +
            "The response is not the federated catalog management v4 one: the catalogs are in the DSP 2025-1 form, compacted with " +
            "the DSP 2025-1 context (https://w3id.org/dspace/2025/1/context.jsonld), the EDC dspace context (https://w3id.org/edc/dspace/v0.0.1) " +
            "and the DSE prefixes (dse, dse-policy, skos, prov, adms): Catalog with dataset, service, participantId; Dataset with hasPolicy " +
            "and distribution; Offer with permission, action, constraint, leftOperand, operator, rightOperand.",
            security = {@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "apiKeyAuth")},
            requestBody = @RequestBody(required = false, description = "Optional QuerySpec. A present body must have the compact @type QuerySpec (management v4), otherwise 400 is returned.",
                    content = @Content(schema = @Schema(ref = ManagementApiJsonSchema.V4.QUERY_SPEC))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "The filtered catalogs, as returned by the catalog filter: a JSON array of DSP 2025-1 catalogs",
                            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(type = "object")))),
                    @ApiResponse(responseCode = "400", description = "The request body is not a valid v4 QuerySpec (e.g. missing, expanded or wrong @type), " +
                            "or the catalog filter answered 400 (no participant token, or the federated catalog rejected the query, e.g. a sortField " +
                            "that no dataset has; EDC error body, forwarded as is)"),
                    @ApiResponse(responseCode = "403", description = "The catalog filter rejected the participant token, or the federated catalog answered 403 " +
                            "(EDC error body, forwarded as is). Other statuses of the catalog filter (e.g. 401, 409) are forwarded the same way"),
                    @ApiResponse(responseCode = "500", description = "Unexpected error in the catalog filter (forwarded as is), or the control plane could not " +
                            "obtain its credentials token. No EDC error body: EDC sets no entity for an unexpected error, the body is the Jetty error page"),
                    @ApiResponse(responseCode = "502", description = "The authority catalog filter could not be reached: its URL could not be resolved from the " +
                            "authority DID document, or the connection failed (EDC error body, type BadGateway); or the catalog filter answered 502 (forwarded " +
                            "as is): the federated catalog could not be resolved or reached, or answered a status (e.g. 404, 500, 503) that has no " +
                            "upstream exception that keeps the federated catalog message (ObjectNotFoundException has a fixed text)")
            }
    )
    Response fetchCatalog(JsonObject querySpec);

}
