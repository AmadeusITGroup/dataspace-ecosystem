package org.eclipse.eonax.spi.telemetrystorage;

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