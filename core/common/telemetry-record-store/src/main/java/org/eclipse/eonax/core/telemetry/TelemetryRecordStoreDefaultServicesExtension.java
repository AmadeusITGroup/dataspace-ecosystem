package org.eclipse.eonax.core.telemetry;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.eonax.edc.spi.telemetryagent.TelemetryRecordStore;

import java.time.Clock;

@Extension(value = TelemetryRecordStoreDefaultServicesExtension.NAME)
public class TelemetryRecordStoreDefaultServicesExtension implements ServiceExtension {

    public static final String NAME = "Telemetry Record Store Default Services";

    @Inject
    private Clock clock;

    @Inject
    private Monitor monitor;

    @Inject
    private CriterionOperatorRegistry criterionOperatorRegistry;

    @Override
    public String name() {
        return NAME;
    }

    @Provider(isDefault = true)
    public TelemetryRecordStore inMemoryTelemetryRecordStore() {
        criterionOperatorRegistry.registerPropertyLookup(new TelemetryRecordPropertyLookup());
        return new InMemoryTelemetryRecordStore(clock, criterionOperatorRegistry);
    }

}


