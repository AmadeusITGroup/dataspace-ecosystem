package org.eclipse.edc.dse.telemetry;

import org.eclipse.edc.dse.telemetry.api.TelemetryCsvManagerApiController;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;


@Extension(value = TelemetryCsvManagerCoreExtension.NAME)
public class TelemetryCsvManagerCoreExtension implements ServiceExtension {

    public static final String NAME = "Telemetry CSV Manager Service Core";

    @Inject
    private WebService webService;

    @Inject
    private Monitor monitor;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        webService.registerResource(new TelemetryCsvManagerApiController(monitor));
    }

}