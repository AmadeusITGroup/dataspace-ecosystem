package org.eclipse.edc.issuerservice.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.dse.spi.issuerservice.VisibilityScope;

public record VisibilityAttestationDto(
        @JsonProperty("id") String id,
        @JsonProperty("holderId") String holderId,
        @JsonProperty("visibility_scope") VisibilityScope visibilityScope
) {
}