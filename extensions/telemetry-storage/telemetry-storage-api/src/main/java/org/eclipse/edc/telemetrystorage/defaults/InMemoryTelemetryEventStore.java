package org.eclipse.edc.telemetrystorage.defaults;

import org.eclipse.dse.spi.telemetrystorage.TelemetryEvent;
import org.eclipse.dse.spi.telemetrystorage.TelemetryEventStore;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.query.QueryResolver;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.store.ReflectionBasedQueryResolver;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * An in-memory, threadsafe telemetry record store. This implementation is intended for testing purposes only.
 */
public class InMemoryTelemetryEventStore implements TelemetryEventStore {
    private final Map<String, TelemetryEvent> telemetryevents = new ConcurrentHashMap<>();

    private final QueryResolver<TelemetryEvent> queryResolver;

    public InMemoryTelemetryEventStore(CriterionOperatorRegistry criterionOperatorRegistry) {
        queryResolver = new ReflectionBasedQueryResolver<>(TelemetryEvent.class, criterionOperatorRegistry);
    }


    @Override
    public Stream<TelemetryEvent> query(QuerySpec querySpec) {
        return queryResolver.query(telemetryevents.values().stream(), querySpec);
    }

    @Override
    public TelemetryEvent findById(String id) {
        return telemetryevents.get(id);
    }

    @Override
    public StoreResult<Void> save(TelemetryEvent telemetryevent) {
        if (telemetryevents.get(telemetryevent.id()) != null) {
            return StoreResult.alreadyExists("Telemetry Event with id '%s' already exist".formatted(telemetryevent.id()));
        }
        telemetryevents.put(telemetryevent.id(), telemetryevent);
        return StoreResult.success();
    }

    @Override
    public StoreResult<TelemetryEvent> deleteById(String id) {
        return Optional.ofNullable(telemetryevents.remove(id))
                .map(StoreResult::success)
                .orElse(StoreResult.notFound("Telemetry Event with id '%s' does not exist".formatted(id)));
    }

}
