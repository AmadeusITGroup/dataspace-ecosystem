package org.eclipse.edc.issuerservice.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record MembershipAttestationDto(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("holderId") String holderId,
        @JsonProperty("membershipType") String membershipType,
        @JsonProperty("properties") Map<String, Object> properties
) {
}
