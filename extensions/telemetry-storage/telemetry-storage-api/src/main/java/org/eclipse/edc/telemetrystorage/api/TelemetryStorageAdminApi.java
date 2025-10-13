package org.eclipse.edc.telemetrystorage.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.core.Response;

@OpenAPIDefinition(info = @Info(description = "This API is used to process telemetry events", title = "Telemetry Storage API", version = "1"))
@Tag(name = "Telemetry Storage API")
public interface TelemetryStorageAdminApi {

    @Operation(description = "Processes a telemetry event.",
            operationId = "processTelemetryEvent",
            requestBody = @RequestBody(content = @Content(schema = @Schema(implementation = TelemetryEventDto.class), mediaType = "application/json")),
            responses = {
                    @ApiResponse(responseCode = "200", description = "The telemetry event was processed successfully."),
                    @ApiResponse(responseCode = "400", description = "Request body was malformed, or the request could not be processed",
                            content = @Content(schema = @Schema(implementation = String.class), mediaType = "application/json"))
            }
    )
    Response processTelemetryEvent(TelemetryEventDto telemetryEvent);
}