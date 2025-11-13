package org.eclipse.dse.edc.telemetry;

import com.azure.core.credential.AzureSasCredential;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import org.eclipse.dse.edc.spi.telemetryagent.TelemetryRecordPublisher;
import org.eclipse.dse.edc.spi.telemetryagent.TelemetryRecordPublisherFactory;
import org.eclipse.dse.spi.telemetry.TelemetryServiceCredentialType;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.TypeManager;

public class EventHubTelemetryRecordPublisherFactory implements TelemetryRecordPublisherFactory {

    private final TypeManager typeManager;
    private final EventHubClientBuilder clientBuilder;
    private final Monitor monitor;

    public EventHubTelemetryRecordPublisherFactory(TypeManager typeManager, String fullyQualifiedNamespace, String eventHubName, Monitor monitor) {
        this.typeManager = typeManager;
        this.monitor = monitor;
        clientBuilder = new EventHubClientBuilder()
                .fullyQualifiedNamespace(fullyQualifiedNamespace)
                .eventHubName(eventHubName)
                .shareConnection();
    }

    @Override
    public TelemetryRecordPublisher createClient(TokenRepresentation credential) {
        var token = credential.getToken();
        var tokenType = TelemetryServiceCredentialType.valueOf(credential.getAdditional().get("type").toString());
        var producer = createProducerClient(token, tokenType);

        return new EventHubTelemetryRecordPublisher(producer, typeManager, monitor);
    }

    private EventHubProducerAsyncClient createProducerClient(String token, TelemetryServiceCredentialType tokenType) {
        if (tokenType == TelemetryServiceCredentialType.CONNECTION_STRING) {
            monitor.debug("Creating with connection string");
            return clientBuilder.connectionString(token).buildAsyncProducerClient();
        } else {
            monitor.debug("Creating with SAS");
            return clientBuilder.credential(new AzureSasCredential(token)).buildAsyncProducerClient();
        }
    }
}
