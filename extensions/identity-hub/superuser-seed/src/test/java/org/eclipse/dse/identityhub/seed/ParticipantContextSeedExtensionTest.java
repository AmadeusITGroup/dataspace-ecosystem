package org.eclipse.dse.identityhub.seed;

import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.identityhub.spi.did.DidDocumentService;
import org.eclipse.edc.identityhub.spi.did.model.DidResource;
import org.eclipse.edc.identityhub.spi.did.store.DidResourceStore;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.CreateParticipantContextResponse;
import org.eclipse.edc.identityhub.spi.participantcontext.model.IdentityHubParticipantContext;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.eclipse.dse.identityhub.seed.ParticipantContextSeedExtension.PRIVATE_KEY_ALIAS_PROPERTY;
import static org.eclipse.dse.identityhub.seed.ParticipantContextSeedExtension.PUBLIC_KEY_ALIAS_PROPERTY;
import static org.eclipse.dse.identityhub.seed.ParticipantContextSeedExtension.SUPERUSER_SERVICES_PROPERTY;
import static org.eclipse.dse.identityhub.seed.ParticipantContextSeedExtension.SUPERUSER_SERVICES_SYNC_PROPERTY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class ParticipantContextSeedExtensionTest {

    private static final String PUBLIC_KEY_ALIAS = UUID.randomUUID().toString();
    private static final String PRIVATE_KEY_ALIAS = UUID.randomUUID().toString();
    private static final String PARTICIPANT_ID = "test-participant-id";

    private final TypeManager typeManager = new JacksonTypeManager();
    private final List<Service> services = List.of(createService(), createService());
    private final IdentityHubParticipantContextService participantContextService = mock();
    private final ParticipantContextConfigService participantContextConfigService = mock();
    private final Vault vault = mock();
    private final Monitor monitor = mock();
    private final DidResourceStore didResourceStore = mock();
    private final DidDocumentService didDocumentService = mock();

    @BeforeEach
    void setup(ServiceExtensionContext context) {
        context.registerService(IdentityHubParticipantContextService.class, participantContextService);
        context.registerService(ParticipantContextConfigService.class, participantContextConfigService);
        context.registerService(Vault.class, vault);
        context.registerService(Monitor.class, monitor);
        context.registerService(TypeManager.class, typeManager);
        context.registerService(DidResourceStore.class, didResourceStore);
        context.registerService(DidDocumentService.class, didDocumentService);
        context.registerService(TransactionContext.class, new NoopTransactionContext());
        useConfig(context, services, false);
        when(participantContextConfigService.get(PARTICIPANT_ID)).thenReturn(ServiceResult.notFound("not found"));
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.success());
    }

    @Test
    void superUserAlreadyExists_shouldDoNothing(ParticipantContextSeedExtension ext, ServiceExtensionContext context) {
        when(participantContextService.getParticipantContext(PARTICIPANT_ID))
                .thenReturn(ServiceResult.success(mock()));

        ext.initialize(context);
        ext.start();

        verifyNoMoreInteractions(vault);
        verify(participantContextService, never()).createParticipantContext(any());
    }

    @Test
    void createSuperUser(ParticipantContextSeedExtension ext, ServiceExtensionContext context) {
        var apiTokenAlias = UUID.randomUUID().toString();
        when(participantContextService.getParticipantContext(PARTICIPANT_ID))
                .thenReturn(ServiceResult.notFound("not found"))
                .thenReturn(ServiceResult.success(IdentityHubParticipantContext.Builder.newInstance()
                        .participantContextId(PARTICIPANT_ID)
                        .apiTokenAlias(apiTokenAlias)
                        .did(PARTICIPANT_ID)
                        .build()));
        var publicKeyPem = UUID.randomUUID().toString();
        when(vault.resolveSecret(PUBLIC_KEY_ALIAS)).thenReturn(publicKeyPem);
        when(participantContextService.createParticipantContext(
                assertArg(participantManifest -> {
                    assertThat(participantManifest.getServiceEndpoints()).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(services);
                    assertThat(participantManifest.getKeys().stream().findFirst().get().getPublicKeyPem()).isEqualTo(publicKeyPem);
                }))
        ).thenReturn(ServiceResult.success(new CreateParticipantContextResponse("some-key", null, null)));

        ext.initialize(context);
        ext.start();

        verify(participantContextService, never()).deleteParticipantContext(any());
        verify(participantContextService).createParticipantContext(any());
    }

    @Test
    void createParticipantFails_shouldThrow(ParticipantContextSeedExtension ext, ServiceExtensionContext context) {
        when(participantContextService.getParticipantContext(PARTICIPANT_ID))
                .thenReturn(ServiceResult.notFound("not found"));
        var publicKeyPem = UUID.randomUUID().toString();
        when(vault.resolveSecret(PUBLIC_KEY_ALIAS)).thenReturn(publicKeyPem);
        when(participantContextService.createParticipantContext(any())).thenReturn(ServiceResult.conflict("some error"));

        ext.initialize(context);
        assertThatExceptionOfType(EdcException.class).isThrownBy(ext::start)
                .withMessageContaining("some error");
    }

    @Test
    void start_seedsEmptyConfig_whenNotFound(ParticipantContextSeedExtension ext, ServiceExtensionContext context) {
        when(participantContextService.getParticipantContext(PARTICIPANT_ID))
                .thenReturn(ServiceResult.success(mock()));

        ext.initialize(context);
        ext.start();

        verify(participantContextConfigService).save(assertArg(cfg -> {
            assertThat(cfg.getParticipantContextId()).isEqualTo(PARTICIPANT_ID);
            assertThat(cfg.getEntries()).isEmpty();
        }));
    }

    @Test
    void start_configAlreadySeeded_skipsReseed(ParticipantContextSeedExtension ext, ServiceExtensionContext context) {
        when(participantContextConfigService.get(PARTICIPANT_ID)).thenReturn(ServiceResult.success(mock()));
        when(participantContextService.getParticipantContext(PARTICIPANT_ID))
                .thenReturn(ServiceResult.success(mock()));

        ext.initialize(context);
        ext.start();

        verify(participantContextConfigService, never()).save(any());
    }

    @Test
    void start_seedConfigFails_shouldThrow(ParticipantContextSeedExtension ext, ServiceExtensionContext context) {
        when(participantContextConfigService.save(any())).thenReturn(ServiceResult.conflict("store conflict"));

        ext.initialize(context);
        assertThatExceptionOfType(EdcException.class).isThrownBy(ext::start)
                .withMessageContaining("store conflict");
    }

    @Test
    void start_seedCheckFails_shouldThrow(ParticipantContextSeedExtension ext, ServiceExtensionContext context) {
        when(participantContextConfigService.get(PARTICIPANT_ID)).thenReturn(ServiceResult.unexpected("db outage"));

        ext.initialize(context);
        assertThatExceptionOfType(EdcException.class).isThrownBy(ext::start)
                .withMessageContaining("db outage");
        verify(participantContextConfigService, never()).save(any());
    }

    private static Service createService() {
        return new Service(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    private void useConfig(ServiceExtensionContext context, List<Service> configuredServices, boolean sync) {
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of(PUBLIC_KEY_ALIAS_PROPERTY, PUBLIC_KEY_ALIAS,
                PRIVATE_KEY_ALIAS_PROPERTY, PRIVATE_KEY_ALIAS,
                SUPERUSER_SERVICES_PROPERTY, typeManager.writeValueAsString(configuredServices),
                SUPERUSER_SERVICES_SYNC_PROPERTY, String.valueOf(sync),
                "edc.participant.id", PARTICIPANT_ID))
        );
    }

    private static DidResource didResource(Service... storedServices) {
        var document = DidDocument.Builder.newInstance()
                .id(PARTICIPANT_ID)
                .service(new ArrayList<>(List.of(storedServices)))
                .build();
        return DidResource.Builder.newInstance()
                .did(PARTICIPANT_ID)
                .participantContextId(PARTICIPANT_ID)
                .document(document)
                .build();
    }

    /**
     * The Super-User already exists: its DID document services are compared with the configuration.
     */
    @Nested
    class SyncDidServices {

        private static final Service CATALOG_OLD = new Service("federated-catalog", "FederatedCatalogService",
                "https://authority-federatedcatalog:8383/api/catalog/v1alpha/catalog/query");
        private static final Service CATALOG_NEW = new Service("federated-catalog", "FederatedCatalogService",
                "https://authority-federatedcatalog:8181/api/management/v4/catalogs/request");
        private static final Service CREDENTIALS = new Service("credential-service-url", "CredentialService",
                "https://authority-identityhub:8282/api/credentials/v1/participants/abc");
        private static final Service UNCONFIGURED = new Service("added-through-api", "SomeService", "https://example.com");

        @BeforeEach
        void existingSuperUser() {
            when(participantContextService.getParticipantContext(PARTICIPANT_ID)).thenReturn(ServiceResult.success(mock()));
            when(didResourceStore.update(any())).thenReturn(StoreResult.success());
            when(didDocumentService.publish(anyString())).thenReturn(ServiceResult.success());
        }

        private ParticipantContextSeedExtension extension(ObjectFactory factory, ServiceExtensionContext context,
                                                          List<Service> configuredServices, boolean sync) {
            useConfig(context, configuredServices, sync);
            var ext = factory.constructInstance(ParticipantContextSeedExtension.class);
            ext.initialize(context);
            return ext;
        }

        @Test
        void syncDisabled_shouldOnlyLogDifferences(ObjectFactory factory, ServiceExtensionContext context) {
            var ext = extension(factory, context, List.of(CATALOG_NEW), false);
            when(didResourceStore.findById(PARTICIPANT_ID)).thenReturn(didResource(CATALOG_OLD));

            ext.start();

            verify(didResourceStore, never()).update(any());
            verify(didDocumentService, never()).publish(any());
            verify(monitor, times(2)).warning(anyString());
            verify(participantContextService, never()).createParticipantContext(any());
        }

        @Test
        void syncEnabled_upToDate_shouldNotUpdate(ObjectFactory factory, ServiceExtensionContext context) {
            var ext = extension(factory, context, List.of(CATALOG_NEW, CREDENTIALS), true);
            when(didResourceStore.findById(PARTICIPANT_ID)).thenReturn(didResource(CREDENTIALS, CATALOG_NEW));

            ext.start();

            verify(didResourceStore, never()).update(any());
            verify(didDocumentService, never()).publish(any());
        }

        @Test
        void syncEnabled_changedEndpoint_shouldReplaceInPlaceAndPublish(ObjectFactory factory, ServiceExtensionContext context) {
            var ext = extension(factory, context, List.of(CATALOG_NEW, CREDENTIALS), true);
            when(didResourceStore.findById(PARTICIPANT_ID)).thenReturn(didResource(CREDENTIALS, CATALOG_OLD, UNCONFIGURED));

            ext.start();

            verify(didResourceStore).update(assertArg(resource -> assertThat(resource.getDocument().getService())
                    .extracting(Service::getId, Service::getServiceEndpoint)
                    .containsExactly(
                            tuple(CREDENTIALS.getId(), CREDENTIALS.getServiceEndpoint()),
                            tuple(CATALOG_NEW.getId(), CATALOG_NEW.getServiceEndpoint()),
                            tuple(UNCONFIGURED.getId(), UNCONFIGURED.getServiceEndpoint()))));
            verify(didDocumentService).publish(PARTICIPANT_ID);
        }

        @Test
        void syncEnabled_missingService_shouldAppend(ObjectFactory factory, ServiceExtensionContext context) {
            var ext = extension(factory, context, List.of(CATALOG_NEW, CREDENTIALS), true);
            when(didResourceStore.findById(PARTICIPANT_ID)).thenReturn(didResource(CREDENTIALS));

            ext.start();

            verify(didResourceStore).update(assertArg(resource -> assertThat(resource.getDocument().getService())
                    .extracting(Service::getId)
                    .containsExactly(CREDENTIALS.getId(), CATALOG_NEW.getId())));
        }

        @Test
        void syncEnabled_duplicatedId_shouldKeepOnlyConfiguredEntry(ObjectFactory factory, ServiceExtensionContext context) {
            // what IdentityHub 0.18.1 replaceService (PATCH .../dids/{did}/endpoints) leaves behind: old entry first
            var ext = extension(factory, context, List.of(CATALOG_NEW), true);
            when(didResourceStore.findById(PARTICIPANT_ID)).thenReturn(didResource(CATALOG_OLD, CATALOG_NEW));

            ext.start();

            verify(didResourceStore).update(assertArg(resource -> assertThat(resource.getDocument().getService())
                    .extracting(Service::getServiceEndpoint)
                    .containsExactly(CATALOG_NEW.getServiceEndpoint())));
        }

        @Test
        void syncEnabled_changedType_shouldUpdate(ObjectFactory factory, ServiceExtensionContext context) {
            var retyped = new Service(CATALOG_NEW.getId(), "OtherType", CATALOG_NEW.getServiceEndpoint());
            var ext = extension(factory, context, List.of(retyped), true);
            when(didResourceStore.findById(PARTICIPANT_ID)).thenReturn(didResource(CATALOG_NEW));

            ext.start();

            verify(didResourceStore).update(assertArg(resource -> assertThat(resource.getDocument().getService())
                    .extracting(Service::getType)
                    .containsExactly("OtherType")));
        }

        @Test
        void noConfiguredServices_shouldNotReadDidDocument(ObjectFactory factory, ServiceExtensionContext context) {
            var ext = extension(factory, context, List.of(), true);

            ext.start();

            verifyNoInteractions(didResourceStore, didDocumentService);
        }

        @Test
        void didDocumentNotFound_shouldWarnAndContinue(ObjectFactory factory, ServiceExtensionContext context) {
            var ext = extension(factory, context, List.of(CATALOG_NEW), true);
            when(didResourceStore.findById(PARTICIPANT_ID)).thenReturn(null);

            ext.start();

            verify(didResourceStore, never()).update(any());
            verify(monitor).warning(anyString());
        }

        @Test
        void updateFails_shouldThrow(ObjectFactory factory, ServiceExtensionContext context) {
            var ext = extension(factory, context, List.of(CATALOG_NEW), true);
            when(didResourceStore.findById(PARTICIPANT_ID)).thenReturn(didResource(CATALOG_OLD));
            when(didResourceStore.update(any())).thenReturn(StoreResult.generalError("db down"));

            assertThatExceptionOfType(EdcException.class).isThrownBy(ext::start).withMessageContaining("db down");
            verify(didDocumentService, never()).publish(any());
        }

        @Test
        void publishFails_shouldWarnAndNotThrow(ObjectFactory factory, ServiceExtensionContext context) {
            var ext = extension(factory, context, List.of(CATALOG_NEW), true);
            when(didResourceStore.findById(PARTICIPANT_ID)).thenReturn(didResource(CATALOG_OLD));
            when(didDocumentService.publish(anyString())).thenReturn(ServiceResult.badRequest("not activated"));

            ext.start();

            verify(didResourceStore).update(any());
            verify(monitor).warning(contains("not re-published"));
        }

        @Test
        void publishThrows_shouldWarnAndNotThrow(ObjectFactory factory, ServiceExtensionContext context) {
            var ext = extension(factory, context, List.of(CATALOG_NEW), true);
            when(didResourceStore.findById(PARTICIPANT_ID)).thenReturn(didResource(CATALOG_OLD));
            when(didDocumentService.publish(anyString())).thenThrow(new EdcPersistenceException("db down"));

            ext.start();

            verify(didResourceStore).update(any());
            verify(monitor).warning(contains("not re-published"), any(EdcPersistenceException.class));
        }

        @Test
        void invalidConfiguredService_shouldWarnAndNotReadDidDocument(ObjectFactory factory, ServiceExtensionContext context) {
            var withoutType = new Service(CATALOG_NEW.getId(), null, CATALOG_NEW.getServiceEndpoint());
            var ext = extension(factory, context, List.of(withoutType, CREDENTIALS), false);

            ext.start();

            verifyNoInteractions(didResourceStore, didDocumentService);
            verify(monitor).warning(contains("has no id, type or serviceEndpoint"));
        }

        @Test
        void nullConfiguredService_shouldWarnAndNotReadDidDocument(ObjectFactory factory, ServiceExtensionContext context) {
            var configured = new ArrayList<Service>();
            configured.add(CATALOG_NEW);
            configured.add(null);
            var ext = extension(factory, context, configured, false);

            ext.start();

            verifyNoInteractions(didResourceStore, didDocumentService);
            verify(monitor).warning(contains("a service entry is empty (null)"));
        }

        @Test
        void nullServicesValue_shouldNotReadDidDocument(ObjectFactory factory, ServiceExtensionContext context) {
            var ext = extension(factory, context, null, true);

            ext.start();

            verifyNoInteractions(didResourceStore, didDocumentService);
        }

        @Test
        void duplicatedConfiguredId_shouldWarnAndNotReadDidDocument(ObjectFactory factory, ServiceExtensionContext context) {
            var ext = extension(factory, context, List.of(CATALOG_OLD, CATALOG_NEW), true);

            ext.start();

            verifyNoInteractions(didResourceStore, didDocumentService);
            verify(monitor).warning(contains("configured more than once"));
        }

        @Test
        void otherServiceWithConfiguredType_shouldWarnAndKeepIt(ObjectFactory factory, ServiceExtensionContext context) {
            var leftover = new Service("old-federated-catalog", CATALOG_NEW.getType(), CATALOG_OLD.getServiceEndpoint());
            var ext = extension(factory, context, List.of(CATALOG_NEW), true);
            when(didResourceStore.findById(PARTICIPANT_ID)).thenReturn(didResource(leftover, CATALOG_NEW));

            ext.start();

            verify(didResourceStore, never()).update(any());
            verify(monitor).warning(contains("[old-federated-catalog] have the same type 'FederatedCatalogService'"));
        }

        @Test
        void storedServiceWithoutId_shouldBeKept(ObjectFactory factory, ServiceExtensionContext context) {
            var withoutId = new Service(null, "SomeService", "https://example.com/no-id");
            var ext = extension(factory, context, List.of(CATALOG_NEW), true);
            when(didResourceStore.findById(PARTICIPANT_ID)).thenReturn(didResource(withoutId, CATALOG_OLD));

            ext.start();

            verify(didResourceStore).update(assertArg(resource -> assertThat(resource.getDocument().getService())
                    .extracting(Service::getServiceEndpoint)
                    .containsExactly(withoutId.getServiceEndpoint(), CATALOG_NEW.getServiceEndpoint())));
        }
    }

}