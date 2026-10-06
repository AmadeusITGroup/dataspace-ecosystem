package org.eclipse.edc.issuerservice.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.eclipse.edc.spi.query.QuerySpec;

import java.util.Collection;

@OpenAPIDefinition(info = @Info(description = "This API is used to manage visibility attestations",
        title = "Issuer Service Visibility Attestation API", version = "1"),
        security = {@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "apiKeyAuth")})
@Tag(name = "Visibility Attestation Admin API")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityScheme(name = "apiKeyAuth", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = "x-api-key")
public interface VisibilityAttestationAdminApi {

    @Operation(description = "Adds a visibility attestation.", operationId = "createVisibilityAttestation",
            requestBody = @RequestBody(content = @Content(schema = @Schema(implementation = VisibilityAttestationDto.class))),
            responses = {
                    @ApiResponse(responseCode = "204", description = "The visibility attestation was added successfully."),
                    @ApiResponse(responseCode = "409", description = "A visibility attestation with the same ID already exists.")
            })
    void createVisibilityAttestation(String participantContextId, VisibilityAttestationDto dto);

    @Operation(description = "Updates a visibility attestation.", operationId = "updateVisibilityAttestation")
    void updateVisibilityAttestation(String participantContextId, VisibilityAttestationDto dto);

    @Operation(description = "Deletes a visibility attestation.", operationId = "deleteVisibilityAttestation")
    void deleteVisibilityAttestation(String participantContextId, String id);

    @Operation(description = "Queries visibility attestations.", operationId = "queryVisibilityAttestations",
            responses = @ApiResponse(responseCode = "200", content = @Content(
                    array = @ArraySchema(schema = @Schema(implementation = VisibilityAttestationDto.class)))))
    Collection<VisibilityAttestationDto> queryVisibilityAttestations(String participantContextId, QuerySpec querySpec);
}