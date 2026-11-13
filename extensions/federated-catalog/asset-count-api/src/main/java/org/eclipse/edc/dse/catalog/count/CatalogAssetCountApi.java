package org.eclipse.edc.dse.catalog.count;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.eclipse.edc.api.management.schema.ManagementApiJsonSchema;

/**
 * Queries the number of matching datasets in each federated catalog.
 */
@Tag(name = "Federated Catalog Asset Count")
public interface CatalogAssetCountApi {

    @Operation(description = "Returns the number of datasets matching the supplied QuerySpec for each participant catalog. Pagination and sorting are ignored, " +
            "but a sortField that no dataset has is rejected with 400, as on POST /v4/catalogs/request.",
            operationId = "queryFederatedCatalogAssetCount",
            requestBody = @RequestBody(required = false, description = "Optional QuerySpec. A present body must have the compact @type QuerySpec (management v4), otherwise 400 is returned.",
                    content = @Content(schema = @Schema(ref = ManagementApiJsonSchema.V4.QUERY_SPEC))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "The matching dataset count for each participant catalog",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = JsonObject.class)))),
                    @ApiResponse(responseCode = "400", description = "The QuerySpec is invalid (e.g. missing, expanded or wrong @type), or its sortField " +
                            "does not exist in a dataset (ValidationFailure on path sortField: Cannot sort by '<field>': field does not exist in Dataset)")
            })
    JsonArray countAssets(JsonObject querySpec);
}