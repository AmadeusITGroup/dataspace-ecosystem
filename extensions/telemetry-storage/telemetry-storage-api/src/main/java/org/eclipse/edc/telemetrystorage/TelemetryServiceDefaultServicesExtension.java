package org.eclipse.edc.telemetrystorage;

import org.eclipse.dse.spi.telemetrystorage.TelemetryEventStore;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.telemetrystorage.defaults.InMemoryTelemetryEventStore;


@Extension(value = TelemetryServiceDefaultServicesExtension.NAME)
public class TelemetryServiceDefaultServicesExtension implements ServiceExtension {

    public static final String NAME = "Telemetry Storage Default Services";

    @Inject
    private CriterionOperatorRegistry criterionOperatorRegistry;


    @Override
    public String name() {
        return NAME;
    }

    @Provider(isDefault = true)
    public TelemetryEventStore inMemoryTelemetryEventStore() {
        return new InMemoryTelemetryEventStore(criterionOperatorRegistry);
    }
}