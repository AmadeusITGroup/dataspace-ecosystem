package org.eclipse.edc.dse.catalog.count;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

/**
 * Queries the number of matching datasets in each federated catalog.
 */
@Tag(name = "Federated Catalog Asset Count")
public interface CatalogAssetCountApi {

    @Operation(description = "Returns the number of datasets matching the supplied QuerySpec for each participant catalog. Pagination and sorting are ignored.",
            operationId = "queryFederatedCatalogAssetCount",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The matching dataset count for each participant catalog",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = JsonObject.class)))),
                    @ApiResponse(responseCode = "400", description = "The QuerySpec is invalid")
            })
    JsonArray countAssets(JsonObject querySpec);
}