package org.eclipse.eonax.core.telemetry;

import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.eonax.edc.spi.telemetryagent.TelemetryRecordStore;
import org.eclipse.eonax.edc.spi.telemetryagent.TelemetryStoreTestBase;

import java.time.Duration;

class InMemoryTelemetryRecordStoreTest extends TelemetryStoreTestBase {

    private final InMemoryTelemetryRecordStore store;

    InMemoryTelemetryRecordStoreTest() {
        var criterionOperatorRegistry = CriterionOperatorRegistryImpl.ofDefaults();
        criterionOperatorRegistry.registerPropertyLookup(new TelemetryRecordPropertyLookup());
        this.store = new InMemoryTelemetryRecordStore(CONNECTOR_NAME, clock, criterionOperatorRegistry);
    }

    @Override
    protected TelemetryRecordStore getTelemetryStore() {
        return store;
    }

    @Override
    protected void leaseEntity(String participantId, String owner, Duration duration) {
        store.acquireLease(participantId, owner, duration);
    }

    @Override
    protected boolean isLeasedBy(String participantId, String owner) {
        return store.isLeasedBy(participantId, owner);
    }

}
