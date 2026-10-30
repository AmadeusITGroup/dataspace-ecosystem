package org.eclipse.dse.core.telemetry;

import org.eclipse.dse.spi.telemetry.TelemetryPolicy;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.protocol.spi.discovery.DiscoveryRequest;
import org.eclipse.edc.protocol.spi.discovery.DiscoveryResponse;
import org.eclipse.edc.protocol.spi.discovery.DiscoveryService;
import org.eclipse.edc.protocol.spi.discovery.DiscoveryUrlResolver;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.system.ServiceExtension;

import java.util.List;

public class TelemetryAgentDefaultExtension implements ServiceExtension {

    /**
     * Since EDC 0.18 (eclipse-edc/Connector#5768) {@code DcpCoreExtension} requires a {@link DiscoveryService}, whose
     * only implementation lives in {@code control-plane-core}. The telemetry agent is not a connector and never runs
     * DSP version discovery, so it only needs a service that accepts resolver registrations.
     *
     * <p>This default provider exists solely to satisfy {@code DcpCoreExtension}'s injection; discovery is never
     * actually used in the telemetry agent.
     */
    @Provider(isDefault = true)
    public DiscoveryService discoveryService() {
        return new DiscoveryService() {
            @Override
            public ServiceResult<List<DiscoveryResponse>> discover(String participantContextId, DiscoveryRequest request) {
                return ServiceResult.unexpected("Dataspace discovery is not supported by the telemetry agent");
            }

            @Override
            public void registerResolver(DiscoveryUrlResolver resolver) {
                // no discovery in this runtime: resolvers are not used
            }
        };
    }

    @Provider(isDefault = true)
    public TelemetryPolicy telemetryAgentPolicy() {
        return () -> Policy.Builder.newInstance().build();
    }

}
