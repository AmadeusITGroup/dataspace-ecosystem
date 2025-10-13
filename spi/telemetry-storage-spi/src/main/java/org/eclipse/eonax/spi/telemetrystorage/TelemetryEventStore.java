package org.eclipse.eonax.spi.telemetrystorage;

import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;

import java.util.stream.Stream;

public interface TelemetryEventStore {

    String TELEMETRY_EVENT_NOT_FOUND = "Telemetry Event with ID %s could not be found";
    String TELEMETRY_EVENT_ALREADY_EXISTS = "Telemetry Event with ID %s already exists";

    Stream<TelemetryEvent> query(QuerySpec querySpec);

    TelemetryEvent findById(String id);

    StoreResult<Void> save(TelemetryEvent attestation);

    StoreResult<TelemetryEvent> deleteById(String id);

}
