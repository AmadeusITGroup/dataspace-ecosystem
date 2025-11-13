package org.eclipse.dse.edc.telemetry;

import org.eclipse.dse.edc.spi.telemetryagent.TelemetryRecordPublisherFactory;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

public class EventHubTelemetryRecordPublisherExtension implements ServiceExtension {

    @Setting(required = true, key = "dse.telemetry-service.eventhub.name")
    private String eventHubNamespace;

    @Setting(required = true, key = "dse.telemetry-service.eventhub.namespace")
    private String eventHubName;

    @Inject
    private TypeManager typeManager;

    @Inject
    private Monitor monitor;

    @Override
    public String name() {
        return "EventHub Telemetry Extension";
    }

    @Provider
    public TelemetryRecordPublisherFactory telemetryRecordPublisherFactory(ServiceExtensionContext context) {
        return new EventHubTelemetryRecordPublisherFactory(typeManager, eventHubName, eventHubNamespace, monitor);
    }
}


