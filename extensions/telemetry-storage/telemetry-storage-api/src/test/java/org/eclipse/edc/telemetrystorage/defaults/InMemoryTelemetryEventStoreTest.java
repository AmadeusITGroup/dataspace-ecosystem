package org.eclipse.edc.telemetrystorage.defaults;

import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.eonax.spi.telemetrystorage.TelemetryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTelemetryEventStoreTest {

    private InMemoryTelemetryEventStore store;
    @Inject
    private CriterionOperatorRegistry criterionOperatorRegistry;

    @BeforeEach
    void setUp() {
        store = new InMemoryTelemetryEventStore(criterionOperatorRegistry);
    }

    private TelemetryEvent createTelemetryEvent() {
        return new TelemetryEvent(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                200,
                1024,
                null,
                Timestamp.from(Instant.now())
        );
    }

    @Test
    void save_shouldAddTelemetryEvent() {
        var event = createTelemetryEvent();

        var result = store.save(event);

        assertThat(result.succeeded()).isTrue();
        assertThat(store.findById(event.id())).isEqualTo(event);
    }

    @Test
    void save_shouldFailIfEventAlreadyExists() {
        var event = createTelemetryEvent();
        store.save(event);

        var result = store.save(event);

        assertThat(result.succeeded()).isFalse();
    }

    @Test
    void deleteById_shouldRemoveTelemetryEvent() {
        var event = createTelemetryEvent();
        store.save(event);

        var result = store.deleteById(event.id());

        assertThat(result.succeeded()).isTrue();
        assertThat(store.findById(event.id())).isNull();
    }

    @Test
    void deleteById_shouldFailIfEventDoesNotExist() {
        var result = store.deleteById("non-existent-id");

        assertThat(result.succeeded()).isFalse();
    }
}