package org.eclipse.edc.dse.telemetry.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReportGenerationRequest(@JsonProperty("participantName") String participantName,
                                      @JsonProperty("year") Integer year,
                                      @JsonProperty("month") Integer month,
                                      @JsonProperty("generateCounterpartyReport") Boolean generateCounterpartyReport) {

    public ReportGenerationRequest {
        if (generateCounterpartyReport == null) {
            generateCounterpartyReport = true;
        }
    }
}
