package org.eclipse.dse.core.telemetry;

import org.eclipse.dse.edc.spi.telemetryagent.TelemetryServiceClient;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TelemetryServiceCredentialManager {

    private final long initialRetryDelaySeconds;
    private final long maxRetryDelaySeconds;
    private final int backoffMultiplier;

    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final int shutdownTimeout = 10;
    private final String name = getClass().toString();

    private final Monitor monitor;
    private final TelemetryServiceClient telemetryServiceClient;
    private final TokenCache cache;
    private final ScheduledExecutorService executor;


    public TelemetryServiceCredentialManager(Monitor monitor,
                                             TelemetryServiceClient telemetryServiceClient,
                                             TokenCache cache,
                                             ExecutorInstrumentation instrumentation) {
        this(monitor, telemetryServiceClient, cache, instrumentation, 1L, 300L, 2);
    }

    public TelemetryServiceCredentialManager(Monitor monitor,
                                             TelemetryServiceClient telemetryServiceClient,
                                             TokenCache cache,
                                             ExecutorInstrumentation instrumentation,
                                             long initialRetryDelaySeconds,
                                             long maxRetryDelaySeconds,
                                             int backoffMultiplier) {
        if (backoffMultiplier <= 1) {
            throw new IllegalArgumentException("backoffMultiplier must be greater than 1, got: " + backoffMultiplier);
        }
        if (initialRetryDelaySeconds <= 0) {
            throw new IllegalArgumentException("initialRetryDelaySeconds must be positive, got: " + initialRetryDelaySeconds);
        }
        if (maxRetryDelaySeconds <= 0) {
            throw new IllegalArgumentException("maxRetryDelaySeconds must be positive, got: " + maxRetryDelaySeconds);
        }
        if (maxRetryDelaySeconds < initialRetryDelaySeconds) {
            throw new IllegalArgumentException("maxRetryDelaySeconds (" + maxRetryDelaySeconds +
                    ") must be >= initialRetryDelaySeconds (" + initialRetryDelaySeconds + ")");
        }

        this.monitor = monitor;
        this.telemetryServiceClient = telemetryServiceClient;
        this.cache = cache;
        this.initialRetryDelaySeconds = initialRetryDelaySeconds;
        this.maxRetryDelaySeconds = maxRetryDelaySeconds;
        this.backoffMultiplier = backoffMultiplier;
        executor = instrumentation.instrument(
                Executors.newSingleThreadScheduledExecutor(r -> {
                    var thread = Executors.defaultThreadFactory().newThread(r);
                    thread.setName("TelemetryCredentialsManager-" + name);
                    return thread;
                }), name);
    }


    public Future<?> start() {
        active.set(true);
        return scheduleNextIterationIn(0L);
    }

    public CompletableFuture<Boolean> stop() {
        active.set(false);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executor.awaitTermination(shutdownTimeout, SECONDS);
            } catch (InterruptedException e) {
                monitor.severe(format("TelemetryCredentialsManager [%s] await termination failed", name), e);
                return false;
            }
        });
    }

    private Runnable loop() {
        return () -> {
            if (active.get()) {
                performLogic();
            }
        };
    }

    private void performLogic() {
        try {
            monitor.debug("Performing logic to get token");
            var result = telemetryServiceClient.fetchCredential();
            long delay = 0;
            if (result.succeeded()) {
                var credential = result.getContent();
                cache.save(credential);
                delay = credential.getExpiresIn() / 2; // Renew credential when it reaches half its TTL

                int previousFailures = consecutiveFailures.getAndSet(0);
                if (previousFailures > 0) {
                    monitor.debug(format("Successfully fetched credential after %d consecutive failures", previousFailures));
                } else {
                    monitor.debug("Successfully fetched credential");
                }
            } else {
                int failureCount = consecutiveFailures.updateAndGet(current ->
                        current < Integer.MAX_VALUE ? current + 1 : Integer.MAX_VALUE);
                monitor.warning(format("Failed to fetch credential from Telemetry Service (attempt #%d): %s",
                        failureCount, result.getFailureDetail()));
                delay = computeBackoffDelay(failureCount);
            }

            long finalDelay = delay > 0 ? delay : initialRetryDelaySeconds;
            monitor.debug(format("Scheduling next iteration in: %d seconds", finalDelay));
            scheduleNextIterationIn(finalDelay);
        } catch (Error e) {
            active.set(false);
            monitor.severe(format("Credential manager [%s] unrecoverable error", name), e);
        } catch (Throwable e) {
            int failureCount = consecutiveFailures.updateAndGet(current ->
                    current < Integer.MAX_VALUE ? current + 1 : Integer.MAX_VALUE);
            monitor.severe(format("Credential manager [%s] error caught (attempt #%d)", name, failureCount), e);
            long backoffDelay = computeBackoffDelay(failureCount);
            scheduleNextIterationIn(backoffDelay);
        }
    }

    long computeBackoffDelay(int failureCount) {
        double exponent = failureCount - 1;
        double delayDouble = initialRetryDelaySeconds * Math.pow(backoffMultiplier, exponent);

        long delay = (delayDouble >= maxRetryDelaySeconds) ? maxRetryDelaySeconds : (long) delayDouble;

        monitor.debug(format("Retry in %d seconds (failure count: %d)", delay, failureCount));
        return delay;
    }

    @NotNull
    private Future<?> scheduleNextIterationIn(long delaySeconds) {
        return executor.schedule(loop(), delaySeconds, SECONDS);
    }
}
