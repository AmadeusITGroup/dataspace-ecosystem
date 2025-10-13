package org.eclipse.edc.issuerservice.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MembershipAttestationDto(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("holderId") String holderId,
        @JsonProperty("membershipType") String membershipType
) {
}
