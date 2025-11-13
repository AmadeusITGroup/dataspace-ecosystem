package org.eclipse.dse.edc.spi.telemetryagent;

import org.eclipse.edc.runtime.metamodel.annotation.ExtensionPoint;
import org.eclipse.edc.spi.persistence.StateEntityStore;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

@ExtensionPoint
public interface TelemetryRecordStore extends StateEntityStore<TelemetryRecord> {

    String RECORD_NOT_FOUND_TEMPLATE = "Telemetry Record with ID %s not found";

    StoreResult<TelemetryRecord> deleteById(String recordId);

    TelemetryRecord findById(String id);

    @NotNull
    Stream<TelemetryRecord> queryTelemetryRecords(QuerySpec querySpec);

}
