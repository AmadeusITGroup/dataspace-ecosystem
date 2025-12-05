package org.eclipse.edc.dse.controlplane.catalog.filter;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.core.Response;

@OpenAPIDefinition
@Tag(name = "Federated Catalog Filter Endpoint for Participant Control-Plane",
        description = "A service that participants to call the filtering of the federated catalog")
public interface FederatedCatalogFilteringApiV2 {

    @Operation(description = "Creates a Token Representation from Identity Hub and sends it to the filtering of the federated catalog for asset filtering",
            responses = {
                    @ApiResponse(responseCode = "500", description = "Failed to fetch the filtered catalog")
            }
    )
    Response getCatalog();

}