package org.eclipse.eonax.edc.telemetry;

import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.eonax.edc.spi.telemetryagent.TelemetryRecordPublisherFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class EventHubTelemetryRecordPublisherFactoryTest {

    private static final String NAMESPACE = "namespace";
    private static final String EVENTHUB_NAME = "eventhub";
    private static final String LOCAL_TOKEN = "Endpoint=sb://local;SharedAccessKeyName=test;SharedAccessKey=SAS_KEY_VALUE;";

    private final TelemetryRecordPublisherFactory factory = new EventHubTelemetryRecordPublisherFactory(new JacksonTypeManager(), NAMESPACE, EVENTHUB_NAME, mock());

    @Test
    void createClient_success_connection_string() {
        var credential = TokenRepresentation.Builder.newInstance()
                .token(LOCAL_TOKEN)
                .expiresIn(100L)
                .additional(Map.of("type", "CONNECTION_STRING"))
                .build();
        var client = factory.createClient(credential);
        assertNotNull(client, "Client should not be null");
    }

    @Test
    void createClient_tokenNull_sas_token() {
        var credential = TokenRepresentation.Builder.newInstance().token(null)
                .additional(Map.of("type", "SAS_TOKEN")).expiresIn(100L).build();
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            factory.createClient(credential); //
        });
        assertEquals("'signature' cannot be null.", exception.getMessage());
    }

    @Test
    void createClient_success_sas_token() {
        var credential = TokenRepresentation.Builder.newInstance()
                .token("token")
                .expiresIn(100L)
                .additional(Map.of("type", "SAS_TOKEN"))
                .build();
        var client = factory.createClient(credential);
        assertNotNull(client, "Client should not be null");
    }

    @Test
    void createClient_tokenNull_connection_string() {
        var credential = TokenRepresentation.Builder.newInstance().token(null)
                .additional(Map.of("type", "CONNECTION_STRING")).expiresIn(100L).build();
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            factory.createClient(credential); //
        });
        assertEquals("'connectionString' cannot be null.", exception.getMessage());
    }

    @Test
    void createClient_TokenRepresentationNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            factory.createClient(null); //
        });
        assertEquals("Cannot invoke \"org.eclipse.edc.spi.iam.TokenRepresentation.getToken()\" because \"credential\" is null", exception.getMessage());
    }

    @Test
    void createClient_TypeNull() {
        var credential = TokenRepresentation.Builder.newInstance().token(null).expiresIn(100L).build();
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            factory.createClient(credential); //
        });
        assertEquals("Cannot invoke \"Object.toString()\" because the return value of \"java.util.Map.get(Object)\" is null", exception.getMessage());
    }
}