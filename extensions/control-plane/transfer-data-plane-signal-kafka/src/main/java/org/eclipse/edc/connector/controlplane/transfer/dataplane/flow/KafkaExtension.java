package org.eclipse.edc.connector.controlplane.transfer.dataplane.flow;

import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowManager;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

/**
 * Service extension that registers the Kafka flow controller for handling Kafka-based asset transfers.
 */
public class KafkaExtension implements ServiceExtension {

    public static final String KAFKA_STREAM_EXTENSION = "Kafka stream extension";

    /**
     * Priority value for the KafkaFlowController.
     * The value 10 was chosen to ensure the Kafka controller is registered with a moderate priority
     * to make sure Kafka controller is used to handle Kafka-based asset instead of
     * default controller has priority 0 which can handle HTTP based asset.
     */
    public static final int KAFKA_CONTROLLER_PRIORITY = 10;

    @Override
    public String name() {
        return KAFKA_STREAM_EXTENSION;
    }

    @Inject
    private DataFlowManager dataFlowManager;

    @Override
    public void initialize(ServiceExtensionContext context) {
        dataFlowManager.register(KAFKA_CONTROLLER_PRIORITY, new KafkaFlowController());
    }
}
