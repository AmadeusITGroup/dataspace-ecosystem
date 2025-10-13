package org.eclipse.edc.telemetrystorage.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.sql.Timestamp;

public record TelemetryEventDto(
        @JsonProperty("id") String id,
        @JsonProperty("contractId") String contractId,
        @JsonProperty("participantId") String participantDid,
        @JsonProperty("responseStatusCode") int responseStatusCode,
        @JsonProperty("msgSize") int msgSize,
        @JsonProperty("csvId") Integer csvId,
        @JsonProperty("timestamp") Timestamp timestamp
) {
}