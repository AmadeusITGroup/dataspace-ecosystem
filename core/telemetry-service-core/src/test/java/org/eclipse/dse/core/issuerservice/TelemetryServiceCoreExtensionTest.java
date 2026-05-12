package org.eclipse.dse.core.issuerservice;

import org.eclipse.dse.spi.telemetry.TelemetryPolicy;
import org.eclipse.dse.spi.telemetry.TelemetryService;
import org.eclipse.dse.spi.telemetry.TelemetryServiceCredentialFactory;
import org.eclipse.dse.spi.telemetry.TelemetryServiceTokenValidator;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.participant.spi.ParticipantAgentService;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.protocol.spi.DataspaceProfileContextRegistry;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class TelemetryServiceCoreExtensionTest {

    private final PolicyEngine policyEngine = mock();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(Monitor.class, mock());
        context.registerService(TelemetryServiceCredentialFactory.class, mock());
        context.registerService(PolicyEngine.class, policyEngine);
        context.registerService(DataspaceProfileContextRegistry.class, mock());
        context.registerService(IdentityService.class, mock());
        context.registerService(ParticipantAgentService.class, mock());
        context.registerService(TelemetryPolicy.class, mock());
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of("edc.participant.id", "test-participant")));
    }

    @Test
    void initialize_shouldRegisterPolicyScopes(TelemetryServiceCoreExtension ext, ServiceExtensionContext context) {
        ext.initialize(context);

        verify(policyEngine, org.mockito.Mockito.times(2)).registerScope(any(), any());
    }

    @Test
    void telemetryServiceTokenValidator_shouldReturnNonNull(TelemetryServiceCoreExtension ext, ServiceExtensionContext context) {
        ext.initialize(context);

        TelemetryServiceTokenValidator validator = ext.telemetryServiceTokenValidator();

        assertThat(validator).isNotNull();
    }

    @Test
    void telemetryService_shouldCreateService(TelemetryServiceCoreExtension ext, ServiceExtensionContext context) {
        ext.initialize(context);

        TelemetryService service = ext.telemetryService();

        assertThat(service).isNotNull().isInstanceOf(TelemetryServiceImpl.class);
    }
}
