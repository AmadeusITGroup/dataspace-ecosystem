package org.eclipse.eonax.core.telemetry;

import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.statemachine.AbstractStateEntityManager;
import org.eclipse.edc.statemachine.Processor;
import org.eclipse.edc.statemachine.ProcessorImpl;
import org.eclipse.edc.statemachine.StateMachineManager;
import org.eclipse.eonax.edc.spi.telemetryagent.TelemetryRecord;
import org.eclipse.eonax.edc.spi.telemetryagent.TelemetryRecordPublisher;
import org.eclipse.eonax.edc.spi.telemetryagent.TelemetryRecordPublisherFactory;
import org.eclipse.eonax.edc.spi.telemetryagent.TelemetryRecordStates;
import org.eclipse.eonax.edc.spi.telemetryagent.TelemetryRecordStore;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static java.util.function.Predicate.isEqual;
import static org.eclipse.edc.spi.persistence.StateEntityStore.hasState;

public class TelemetryAgent extends AbstractStateEntityManager<TelemetryRecord, TelemetryRecordStore> {

    private TelemetryRecordPublisherFactory publisherFactory;
    private TokenCache cache;

    private TelemetryAgent() {
    }

    @Override
    protected StateMachineManager.Builder configureStateMachineManager(StateMachineManager.Builder builder) {
        return builder.processor(receivedRecordsProcessor());
        // we can activate the other processor in case we want to remove sent record from the DB
        // return builder.processor(receivedRecordsProcessor()).processor(completedRecordsProcessor());
    }

    private Processor receivedRecordsProcessor() {
        return () -> {
            var records = getRecordsWithType(TelemetryRecordStates.RECEIVED);
            if (!records.isEmpty()) {
                return createPublisher()
                        .map(client -> sendRecords(client, records))
                        .orElse(0L);
            }
            return 0L;
        };
    }


    private Optional<TelemetryRecordPublisher> createPublisher() {
        return Optional.ofNullable(cache.get())
                .map(c -> Optional.of(publisherFactory.createClient(c)))
                .orElseGet(() -> {
                    monitor.warning("Failed to get credentials from cache");
                    return Optional.empty();
                });
    }

    private Long sendRecords(TelemetryRecordPublisher publisher, Collection<TelemetryRecord> records) {
        Function<TelemetryRecord, Boolean> function = telemetry.contextPropagationMiddleware(publisher::sendRecord);
        return records.stream()
                .map(record -> {
                    var hasBeenProcessed = function.apply(record);
                    if (hasBeenProcessed) {
                        transitionToCompleted(record);
                    } else {
                        breakLease(record);
                    }
                    return hasBeenProcessed;
                })
                .filter(isEqual(true))
                .count();
    }

    private Processor completedRecordsProcessor() {
        return ProcessorImpl.Builder.newInstance(() -> this.getRecordsWithType(TelemetryRecordStates.SENT))
                .process(telemetry.contextPropagationMiddleware(this::deleteRecord))
                .onNotProcessed(this::breakLease)
                .build();
    }

    private Collection<TelemetryRecord> getRecordsWithType(TelemetryRecordStates state) {
        var criteria = new Criterion[] {hasState(state.code())};
        return store.nextNotLeased(batchSize, criteria);
    }

    private Boolean deleteRecord(TelemetryRecord record) {
        store.deleteById(record.getId());
        return Boolean.TRUE;
    }

    private void transitionToCompleted(TelemetryRecord record) {
        record.transitionToCompleted();
        update(record);
    }

    public static class Builder extends AbstractStateEntityManager.Builder<TelemetryRecord, TelemetryRecordStore, TelemetryAgent, Builder> {

        private Builder() {
            super(new TelemetryAgent());
        }

        @Override
        public Builder self() {
            return this;
        }

        public Builder publisherFactory(TelemetryRecordPublisherFactory publisherFactory) {
            manager.publisherFactory = publisherFactory;
            return this;
        }


        public Builder credentialsCache(TokenCache cache) {
            manager.cache = cache;
            return this;
        }

        public TelemetryAgent build() {
            Objects.requireNonNull(manager.publisherFactory, "publisherFactory");
            Objects.requireNonNull(manager.cache, "cache");
            return manager;
        }

        public static Builder newInstance() {
            return new Builder();
        }
    }
}
