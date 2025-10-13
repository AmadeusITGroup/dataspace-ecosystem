package org.eclipse.edc.telemetrystorage;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.telemetrystorage.api.TelemetryStorageApiController;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.eonax.spi.telemetrystorage.TelemetryEventStore;


@Extension(value = TelemetryStorageCoreExtension.NAME)
public class TelemetryStorageCoreExtension implements ServiceExtension {

    public static final String NAME = "Telemetry Storage Service Core";

    @Inject
    private WebService webService;
    @Inject
    private TelemetryEventStore attestationStore;
    @Inject
    private Monitor monitor;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {

        webService.registerResource(new TelemetryStorageApiController(attestationStore, monitor));
    }

}