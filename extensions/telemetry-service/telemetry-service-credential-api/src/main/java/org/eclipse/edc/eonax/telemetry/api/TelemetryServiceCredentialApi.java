package org.eclipse.edc.eonax.telemetry.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.eclipse.edc.spi.iam.TokenRepresentation;

@OpenAPIDefinition(
        info = @Info(description = "Telemetry Service Credential APIs.", title = "Credential API", version = "v1alpha"))
public interface TelemetryServiceCredentialApi {

    @Operation(description = "Return a SAS token for publishing telemetry records.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Token was generated successfully."),
                    @ApiResponse(responseCode = "400", description = "Request body was malformed"),
                    @ApiResponse(responseCode = "500", description = "Server failed to generate token")
            }
    )
    TokenRepresentation generateToken(String token);

}
