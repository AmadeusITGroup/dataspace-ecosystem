package org.eclipse.edc.eonax.telemetry.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReportGenerationRequest(@JsonProperty("participantName") String participantName,
                                      @JsonProperty("year") Integer year,
                                      @JsonProperty("month") Integer month) {
}
