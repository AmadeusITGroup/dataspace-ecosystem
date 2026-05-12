package org.eclipse.dse.common.config;

import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class ParticipantContextConfigSeedExtensionTest {

    private static final String PARTICIPANT_ID = "test-participant";

    private final ParticipantContextConfigService configService = mock();
    private final Monitor monitor = mock();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(ParticipantContextConfigService.class, configService);
        context.registerService(Monitor.class, monitor);
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of(
                "edc.participant.id", PARTICIPANT_ID,
                "edc.participant.something", "value1",
                "edc.iam.issuer.id", "did:web:test",
                "edc.ih.iam.publickey.alias", "did:web:test#key",
                "other.key", "should-be-excluded"
        )));
    }

    @Test
    void start_configAlreadyExists_shouldSkip(ParticipantContextConfigSeedExtension ext, ServiceExtensionContext context) {
        when(configService.get(PARTICIPANT_ID)).thenReturn(ServiceResult.success(mock()));

        ext.initialize(context);
        ext.start();

        verify(configService, never()).save(any());
    }

    @Test
    void start_configNotFound_shouldSeed(ParticipantContextConfigSeedExtension ext, ServiceExtensionContext context) {
        when(configService.get(PARTICIPANT_ID)).thenReturn(ServiceResult.notFound("not found"));
        when(configService.save(any())).thenReturn(ServiceResult.success());

        ext.initialize(context);
        ext.start();

        verify(configService).save(assertArg(cfg -> {
            assertThat(cfg.getParticipantContextId()).isEqualTo(PARTICIPANT_ID);
            assertThat(cfg.getEntries()).containsKey("edc.participant.id");
            assertThat(cfg.getEntries()).containsKey("edc.participant.something");
            assertThat(cfg.getEntries()).containsEntry("edc.iam.issuer.id", "did:web:test");
            assertThat(cfg.getEntries()).containsEntry("edc.ih.iam.publickey.alias", "did:web:test#key");
            assertThat(cfg.getEntries()).doesNotContainKey("other.key");
        }));
    }

    @Test
    void start_transientError_shouldAbortWithoutSaving(ParticipantContextConfigSeedExtension ext, ServiceExtensionContext context) {
        when(configService.get(PARTICIPANT_ID)).thenReturn(ServiceResult.unexpected("db outage"));

        ext.initialize(context);
        ext.start();

        verify(configService, never()).save(any());
    }

    @Test
    void start_saveFails_shouldLogWarning(ParticipantContextConfigSeedExtension ext, ServiceExtensionContext context) {
        when(configService.get(PARTICIPANT_ID)).thenReturn(ServiceResult.notFound("not found"));
        when(configService.save(any())).thenReturn(ServiceResult.conflict("conflict"));

        ext.initialize(context);
        ext.start();

        verify(monitor).warning(any(String.class));
    }

    @Test
    void start_defaultParticipantId_whenNotConfigured(ParticipantContextConfigSeedExtension ext, ServiceExtensionContext context) {
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of()));
        when(configService.get("default-participant")).thenReturn(ServiceResult.notFound("not found"));
        when(configService.save(any())).thenReturn(ServiceResult.success());

        ext.initialize(context);
        ext.start();

        verify(configService).save(assertArg(cfg ->
                assertThat(cfg.getParticipantContextId()).isEqualTo("default-participant")));
    }
}
