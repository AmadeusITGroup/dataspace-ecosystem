package org.eclipse.edc.telemetry.azure;

import org.eclipse.edc.spi.security.Vault;
import org.eclipse.eonax.spi.telemetry.TelemetryServiceConstants;
import org.eclipse.eonax.spi.telemetry.TelemetryServiceCredentialType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EventHubConnectionStringFactoryTest {

    private static final String CONNECTION_STRING_ALIAS = UUID.randomUUID().toString();
    private static final String CONNECTION_STRING = "Endpoint=sb://example.servicebus.windows.net/;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=testKey";

    private final Vault vault = mock();
    private final EventHubConnectionStringFactory factory = new EventHubConnectionStringFactory(vault, CONNECTION_STRING_ALIAS);

    @Test
    void get_withValidConnectionString_returnsTokenRepresentation() {
        when(vault.resolveSecret(CONNECTION_STRING_ALIAS)).thenReturn(CONNECTION_STRING);

        var result = factory.get();

        assertThat(result.succeeded()).isTrue();
        var tokenRepresentation = result.getContent();
        assertThat(tokenRepresentation).isNotNull();
        assertThat(tokenRepresentation.getToken()).isEqualTo(CONNECTION_STRING);
        assertThat(tokenRepresentation.getAdditional()).containsEntry(TelemetryServiceConstants.CREDENTIAL_TYPE, TelemetryServiceCredentialType.CONNECTION_STRING);
    }

    @Test
    void get_withMissingConnectionString_returnsFailure() {
        when(vault.resolveSecret(CONNECTION_STRING_ALIAS)).thenReturn(null);

        var result = factory.get();

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureMessages()).containsExactly("Failed to find connection string with alias '%s' in vault".formatted(CONNECTION_STRING_ALIAS));
    }
}