package org.eclipse.dse.core.telemetry;

import org.eclipse.dse.edc.spi.telemetryagent.TelemetryRecord;
import org.eclipse.dse.edc.spi.telemetryagent.TelemetryRecordStates;
import org.eclipse.dse.edc.spi.telemetryagent.TelemetryRecordStore;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.query.QueryResolver;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.store.InMemoryStatefulEntityStore;
import org.eclipse.edc.store.ReflectionBasedQueryResolver;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * An in-memory, threadsafe telemetry record store. This implementation is intended for testing purposes only.
 */
public class InMemoryTelemetryRecordStore extends InMemoryStatefulEntityStore<TelemetryRecord> implements TelemetryRecordStore {
    private final QueryResolver<TelemetryRecord> telemetryQueryResolver;

    public InMemoryTelemetryRecordStore(Clock clock, CriterionOperatorRegistry criterionOperatorRegistry) {
        this(UUID.randomUUID().toString(), clock, criterionOperatorRegistry);
    }

    public InMemoryTelemetryRecordStore(String leaseHolder, Clock clock, CriterionOperatorRegistry criterionOperatorRegistry) {
        super(TelemetryRecord.class, leaseHolder, clock, criterionOperatorRegistry, s -> TelemetryRecordStates.valueOf(s).code());
        telemetryQueryResolver = new ReflectionBasedQueryResolver<>(TelemetryRecord.class, criterionOperatorRegistry);
    }

    @Override
    public StoreResult<TelemetryRecord> deleteById(String recordId) {
        var entity = findById(recordId);

        if (entity == null) {
            return StoreResult.notFound(String.format(TelemetryRecordStore.RECORD_NOT_FOUND_TEMPLATE, recordId));
        }

        delete(recordId);
        return StoreResult.success(entity);
    }

    @NotNull
    @Override
    public Stream<TelemetryRecord> queryTelemetryRecords(QuerySpec querySpec) {
        return telemetryQueryResolver.query(findAll(), querySpec);
    }
}
