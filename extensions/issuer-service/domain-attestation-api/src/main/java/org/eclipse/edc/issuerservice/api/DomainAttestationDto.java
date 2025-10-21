package org.eclipse.edc.issuerservice.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DomainAttestationDto(
        @JsonProperty("id") String id,
        @JsonProperty("holderId") String holderId,
        @JsonProperty("domain") String domain) {
}
