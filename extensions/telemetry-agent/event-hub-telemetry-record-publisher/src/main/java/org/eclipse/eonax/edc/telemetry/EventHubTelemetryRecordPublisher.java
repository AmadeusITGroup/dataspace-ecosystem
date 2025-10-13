package org.eclipse.eonax.edc.telemetry;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.eonax.edc.spi.telemetryagent.TelemetryRecord;
import org.eclipse.eonax.edc.spi.telemetryagent.TelemetryRecordPublisher;

import java.util.Collections;

public class EventHubTelemetryRecordPublisher implements TelemetryRecordPublisher {

    private final EventHubProducerAsyncClient producer;
    private final TypeManager typeManager;
    private final Monitor monitor;


    public EventHubTelemetryRecordPublisher(EventHubProducerAsyncClient producer, TypeManager typeManager, Monitor monitor) {
        this.producer = producer;
        this.typeManager = typeManager;
        this.monitor = monitor;
    }


    @Override
    public Boolean sendRecord(TelemetryRecord record) {
        var data = typeManager.writeValueAsString(record);
        try {
            producer.send(Collections.singletonList(new EventData(data))).block();
            return Boolean.TRUE;
        } catch (Exception e) {
            monitor.severe("Failed to publish record: " + e.getMessage());
            return Boolean.FALSE;
        }
    }

}
