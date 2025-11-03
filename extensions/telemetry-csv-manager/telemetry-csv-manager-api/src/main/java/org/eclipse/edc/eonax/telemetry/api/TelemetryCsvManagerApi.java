package org.eclipse.edc.eonax.telemetry.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@OpenAPIDefinition(info = @Info(description = "This API is used to generate billing reports", title = "Telemetry CSV Manager API", version = "1"))
@Tag(name = "Telemetry CSV Manager API")
public interface TelemetryCsvManagerApi {

    @Operation(description = "Retrieves Reports",
            operationId = "getReport",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The telemetry event was processed successfully"),
                    @ApiResponse(responseCode = "401", description = "Invalid JWT token"),
                    @ApiResponse(responseCode = "403", description = "Missing or invalid participant in roles"),
                    @ApiResponse(responseCode = "404", description = "Report not found",
                            content = @Content(schema = @Schema(implementation = String.class), mediaType = "application/json"))
            }
    )
    Response getReport(@Parameter(description = "Authorization header (JWT token)") @HeaderParam("Authorization") String authHeader, @Parameter(description = "Target month") @QueryParam("month") Integer month,
                       @Parameter(description = "Target year") @QueryParam("year") Integer year);
}