package org.eclipse.dse.core.telemetry;

import org.eclipse.dse.edc.spi.telemetryagent.TelemetryServiceClient;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TelemetryServiceCredentialManagerTest {

    private final TokenCache cache = mock();
    private final TelemetryServiceClient telemetryServiceClient = mock();
    private TelemetryServiceCredentialManager manager;


    @BeforeEach
    void setUp() {
        manager = new TelemetryServiceCredentialManager(
                mock(),
                telemetryServiceClient,
                cache,
                ExecutorInstrumentation.noop(),
                1L,
                300L,
                2
        );
    }

    @AfterEach
    void tearDown() {
        manager.stop();
    }

    @Test
    void save_success() {
        var token = TokenRepresentation.Builder.newInstance()
                .token("test")
                .expiresIn(100L)
                .build();

        when(telemetryServiceClient.fetchCredential()).thenReturn(Result.success(token));

        manager.start();

        await().untilAsserted(() -> verify(cache).save(token));
    }

    @Test
    void save_clientFails_noCredentialStored() {
        when(telemetryServiceClient.fetchCredential()).thenReturn(Result.failure("error"));

        manager.start();

        await().untilAsserted(() -> verifyNoInteractions(cache));
    }

    @Test
    void computeBackoffDelay_shouldStartWithInitialDelay() {
        long delay = manager.computeBackoffDelay(1);
        assertThat(delay).isEqualTo(1L);
    }

    @Test
    void computeBackoffDelay_shouldDoubleDelayWithEachFailure() {
        assertThat(manager.computeBackoffDelay(1)).isEqualTo(1L);
        assertThat(manager.computeBackoffDelay(2)).isEqualTo(2L);
        assertThat(manager.computeBackoffDelay(3)).isEqualTo(4L);
        assertThat(manager.computeBackoffDelay(4)).isEqualTo(8L);
        assertThat(manager.computeBackoffDelay(5)).isEqualTo(16L);
        assertThat(manager.computeBackoffDelay(6)).isEqualTo(32L);
        assertThat(manager.computeBackoffDelay(7)).isEqualTo(64L);
        assertThat(manager.computeBackoffDelay(8)).isEqualTo(128L);
    }

    @Test
    void computeBackoffDelay_shouldCapAtMaximumDelay() {
        assertThat(manager.computeBackoffDelay(9)).isEqualTo(256L);
        assertThat(manager.computeBackoffDelay(10)).isEqualTo(300L);
        assertThat(manager.computeBackoffDelay(11)).isEqualTo(300L);
        assertThat(manager.computeBackoffDelay(20)).isEqualTo(300L);
        assertThat(manager.computeBackoffDelay(50)).isEqualTo(300L);
    }

    @Test
    void computeBackoffDelay_shouldHandleOverflowProtection() {
        assertThat(manager.computeBackoffDelay(100)).isEqualTo(300L);
        assertThat(manager.computeBackoffDelay(1000)).isEqualTo(300L);
        assertThat(manager.computeBackoffDelay(Integer.MAX_VALUE)).isEqualTo(300L);
    }

    @Test
    void computeBackoffDelay_shouldRespectCustomConfiguration() {
        var customManager = new TelemetryServiceCredentialManager(
                mock(),
                telemetryServiceClient,
                cache,
                ExecutorInstrumentation.noop(),
                5L,
                60L,
                3
        );

        assertThat(customManager.computeBackoffDelay(1)).isEqualTo(5L);
        assertThat(customManager.computeBackoffDelay(2)).isEqualTo(15L);
        assertThat(customManager.computeBackoffDelay(3)).isEqualTo(45L);
        assertThat(customManager.computeBackoffDelay(4)).isEqualTo(60L);
        assertThat(customManager.computeBackoffDelay(5)).isEqualTo(60L);
    }


    @Test
    void constructor_shouldThrowException_whenBackoffMultiplierIsZero() {
        assertThatThrownBy(() -> new TelemetryServiceCredentialManager(
                mock(),
                telemetryServiceClient,
                cache,
                ExecutorInstrumentation.noop(),
                1L,
                300L,
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backoffMultiplier must be greater than 1");
    }

    @Test
    void constructor_shouldThrowException_whenBackoffMultiplierIsNegative() {
        assertThatThrownBy(() -> new TelemetryServiceCredentialManager(
                mock(),
                telemetryServiceClient,
                cache,
                ExecutorInstrumentation.noop(),
                1L,
                300L,
                -1
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backoffMultiplier must be greater than 1");
    }

    @Test
    void exponentialBackoff_shouldRetryAfterFailure() {
        when(telemetryServiceClient.fetchCredential()).thenReturn(Result.failure("Connectivity issue"));

        manager.start();

        await().atMost(2, java.util.concurrent.TimeUnit.SECONDS)
                .untilAsserted(() -> verify(telemetryServiceClient, org.mockito.Mockito.times(1)).fetchCredential());

        await().pollDelay(1, java.util.concurrent.TimeUnit.SECONDS)
                .atMost(4, java.util.concurrent.TimeUnit.SECONDS)
                .untilAsserted(() -> verify(telemetryServiceClient, org.mockito.Mockito.times(2)).fetchCredential());

        verifyNoInteractions(cache);
    }

    @Test
    void constructor_shouldThrowException_whenBackoffMultiplierIsOne() {
        assertThatThrownBy(() -> new TelemetryServiceCredentialManager(
                mock(),
                telemetryServiceClient,
                cache,
                ExecutorInstrumentation.noop(),
                1L,
                300L,
                1
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backoffMultiplier must be greater than 1");
    }

    @Test
    void constructor_shouldThrowException_whenInitialRetryDelayIsZero() {
        assertThatThrownBy(() -> new TelemetryServiceCredentialManager(
                mock(),
                telemetryServiceClient,
                cache,
                ExecutorInstrumentation.noop(),
                0L,
                300L,
                2
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialRetryDelaySeconds must be positive");
    }

    @Test
    void constructor_shouldThrowException_whenInitialRetryDelayIsNegative() {
        assertThatThrownBy(() -> new TelemetryServiceCredentialManager(
                mock(),
                telemetryServiceClient,
                cache,
                ExecutorInstrumentation.noop(),
                -1L,
                300L,
                2
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialRetryDelaySeconds must be positive");
    }

    @Test
    void constructor_shouldThrowException_whenMaxRetryDelayIsZero() {
        assertThatThrownBy(() -> new TelemetryServiceCredentialManager(
                mock(),
                telemetryServiceClient,
                cache,
                ExecutorInstrumentation.noop(),
                1L,
                0L,
                2
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRetryDelaySeconds must be positive");
    }

    @Test
    void constructor_shouldThrowException_whenMaxRetryDelayIsNegative() {
        assertThatThrownBy(() -> new TelemetryServiceCredentialManager(
                mock(),
                telemetryServiceClient,
                cache,
                ExecutorInstrumentation.noop(),
                1L,
                -1L,
                2
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRetryDelaySeconds must be positive");
    }

    @Test
    void constructor_shouldThrowException_whenMaxRetryDelayIsLessThanInitialDelay() {
        assertThatThrownBy(() -> new TelemetryServiceCredentialManager(
                mock(),
                telemetryServiceClient,
                cache,
                ExecutorInstrumentation.noop(),
                100L,
                50L,
                2
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRetryDelaySeconds")
                .hasMessageContaining("must be >= initialRetryDelaySeconds");
    }

}
