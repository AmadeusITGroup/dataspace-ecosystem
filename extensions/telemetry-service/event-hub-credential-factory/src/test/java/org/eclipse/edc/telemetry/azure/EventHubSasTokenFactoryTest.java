package org.eclipse.edc.telemetry.azure;

import org.eclipse.dse.spi.telemetry.TelemetryServiceConstants;
import org.eclipse.dse.spi.telemetry.TelemetryServiceCredentialType;
import org.eclipse.edc.spi.security.Vault;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EventHubSasTokenFactoryTest {

    private static final String RESOURCE_URI = "https://example.com";
    private static final String KEY_NAME = "testKeyName";
    private static final String KEY_ALIAS = "testKeyAlias";
    private static final long VALIDITY = 3600L;
    private static final String SECRET_KEY = "testSecretKey";

    private final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
    private final Vault vault = mock();
    private final EventHubSasTokenFactory factory = new EventHubSasTokenFactory(clock, vault, VALIDITY, RESOURCE_URI, KEY_NAME, KEY_ALIAS);

    @Test
    void withValidParameters_returnsTokenRepresentation() {
        when(vault.resolveSecret(KEY_ALIAS)).thenReturn(SECRET_KEY);

        var result = factory.get();

        assertThat(result.succeeded()).isTrue();
        var tokenRepresentation = result.getContent();
        assertThat(tokenRepresentation)
                .isNotNull();
        assertThat(tokenRepresentation.getToken()).startsWith("SharedAccessSignature");
        assertThat(tokenRepresentation.getAdditional()).containsEntry(TelemetryServiceConstants.CREDENTIAL_TYPE, TelemetryServiceCredentialType.SAS_TOKEN);
    }

    @Test
    void withMissingSecret_returnsFailure() {
        when(vault.resolveSecret(KEY_ALIAS)).thenReturn(null);

        var result = factory.get();

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).startsWith("Could not find secret with alias %s in vault".formatted(KEY_ALIAS));
    }

}