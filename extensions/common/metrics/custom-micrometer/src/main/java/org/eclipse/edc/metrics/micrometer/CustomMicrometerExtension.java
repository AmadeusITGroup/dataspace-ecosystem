package org.eclipse.edc.metrics.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.time.Duration;

public class CustomMicrometerExtension implements ServiceExtension {

    private static final double[] SERVICE_LEVEL_OBJECTIVES = {
            Duration.ofMillis(5).toNanos(),
            Duration.ofMillis(25).toNanos(),
            Duration.ofMillis(50).toNanos(),
            Duration.ofSeconds(1).toNanos()
    };

    @Inject
    private MeterRegistry meterRegistry;

    @Override
    public String name() {
        return "Custom Micrometer Service";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        meterRegistry.config().meterFilter(new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                return DistributionStatisticConfig.builder()
                        .serviceLevelObjectives(SERVICE_LEVEL_OBJECTIVES)
                        .build()
                        .merge(config);
            }

            @Override
            public MeterFilterReply accept(Meter.Id id) {
                return MeterFilterReply.NEUTRAL;
            }
        });
    }
}
