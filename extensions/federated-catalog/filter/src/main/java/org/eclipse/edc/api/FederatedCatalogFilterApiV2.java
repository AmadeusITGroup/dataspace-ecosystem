/*
 *  Copyright (c) 2022 Amadeus
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus - Initial implementation
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - handle HEAD requests
 *
 */

package org.eclipse.edc.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.FilterRequest;
import org.eclipse.edc.web.spi.ApiErrorDetail;


@OpenAPIDefinition(security = {@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "apiKeyAuth")})
@Tag(name = "Federated Catalog Filter",
        description = "A service that allows the filtering of the assets in the federated catalog based on participant vcs")
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
public interface FederatedCatalogFilterApiV2 {


    @Operation(description = "Filters federated catalog assets based on participant vcs. Errors have the EDC JSON error body " +
            "(an array of {message, type, path, invalidValue}), except 500: EDC sets no entity for an unexpected error, so the " +
            "body is the Jetty error page.",
            security = {@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "apiKeyAuth")},
            responses = {
                    @ApiResponse(responseCode = "200", description = "The filtered catalogs: a JSON array of DSP 2025-1 catalogs (empty when there are none)",
                            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(type = "object")))),
                    @ApiResponse(responseCode = "400", description = "The request has no participant token (tokenRepresentation), or the federated catalog " +
                            "rejected the query with 400, e.g. a sortField that no dataset has (its messages are kept)", content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ApiErrorDetail.class)))),
                    @ApiResponse(responseCode = "401", description = "The federated catalog replied 401 (its messages are kept)",
                            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = ApiErrorDetail.class)))),
                    @ApiResponse(responseCode = "403", description = "The participant token is expired or invalid, or the federated catalog replied 403 " +
                            "(its messages are kept)", content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ApiErrorDetail.class)))),
                    @ApiResponse(responseCode = "409", description = "The federated catalog replied 409 (its messages are kept)",
                            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = ApiErrorDetail.class)))),
                    @ApiResponse(responseCode = "500", description = "Unexpected error in the catalog filter (no EDC error body: the Jetty error page)"),
                    @ApiResponse(responseCode = "502", description = "The federated catalog URL cannot be resolved from the authority DID document, the " +
                            "federated catalog cannot be reached, or it replied with another error status (e.g. 404, 500, 503), which has no upstream " +
                            "exception that keeps the federated catalog message (ObjectNotFoundException has a fixed text): type BadGateway, " +
                            "the message has the federated catalog status and messages", content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ApiErrorDetail.class))))
            }
    )
    Response filter(FilterRequest req);

}
