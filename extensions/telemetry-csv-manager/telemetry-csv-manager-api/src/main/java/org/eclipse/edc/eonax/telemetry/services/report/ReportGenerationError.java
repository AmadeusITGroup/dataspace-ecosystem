package org.eclipse.edc.eonax.telemetry.services.report;

import java.time.LocalDateTime;

public record ReportGenerationError(LocalDateTime generationTimespanTarget, String contractId, String participantId,
                                    String counterpartyId,
                                    Long participantMsgSize, Long counterpartyMsgSize,
                                    Long participantEventCount, Long counterpartyEventCount,
                                    String errorMessage) {
}
