package org.eclipse.dse.spi.telemetrystorage;

import java.sql.Timestamp;

public record TelemetryEvent(
        String id,
        String contractId,
        String participantId,
        int responseStatusCode,
        int responseSize,
        Integer csvId,
        Timestamp timestamp
) {
}