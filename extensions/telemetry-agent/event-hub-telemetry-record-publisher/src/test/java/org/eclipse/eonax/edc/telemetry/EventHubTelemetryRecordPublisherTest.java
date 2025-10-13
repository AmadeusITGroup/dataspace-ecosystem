package org.eclipse.eonax.edc.telemetry;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.eonax.edc.spi.telemetryagent.TelemetryRecord;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class EventHubTelemetryRecordPublisherTest {

    private final EventHubProducerAsyncClient producer = mock();

    private final TypeManager typeManager = mock();

    private final EventHubTelemetryRecordPublisher publisher = new EventHubTelemetryRecordPublisher(producer, typeManager, mock());

    @Test
    void testSendRecordSuccess() {
        // Arrange
        var record = new TelemetryRecord();
        when(typeManager.writeValueAsString(record)).thenReturn("recordData");
        when(producer.send((Iterable<EventData>) any())).thenReturn(Mono.empty());

        var result = publisher.sendRecord(record);

        assertThat(result).isTrue();
    }

    @Test
    void testSendRecordFailure() {
        // Arrange
        var record = new TelemetryRecord();
        when(typeManager.writeValueAsString(record)).thenReturn("recordData");
        when(producer.send((Iterable<EventData>) any())).thenReturn(Mono.error(new RuntimeException("Error")));

        var result = publisher.sendRecord(record);

        assertThat(result).isFalse();
    }
}