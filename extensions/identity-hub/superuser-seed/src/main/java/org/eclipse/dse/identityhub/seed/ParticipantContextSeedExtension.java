package org.eclipse.dse.identityhub.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.identityhub.spi.did.DidDocumentService;
import org.eclipse.edc.identityhub.spi.did.store.DidResourceStore;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityApiScopes;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.IssuerAdminApiScopes;
import org.eclipse.edc.identityhub.spi.participantcontext.model.CreateParticipantContextResponse;
import org.eclipse.edc.identityhub.spi.participantcontext.model.KeyDescriptor;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.participantcontext.spi.config.model.ParticipantContextConfiguration;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceFailure;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Optional.ofNullable;

public class ParticipantContextSeedExtension implements ServiceExtension {

    public static final String NAME = "Root User Seeding";

    private static final TypeReference<List<Service>> SERVICE_LIST = new TypeReference<>() {
    };

    public static final String SUPERUSER_SERVICES_PROPERTY = "edc.ih.api.superuser.services";
    public static final String SUPERUSER_SERVICES_SYNC_PROPERTY = "edc.ih.api.superuser.services.sync";
    public static final String SUPERUSER_FORCE_RECREATE_PROPERTY = "edc.ih.api.superuser.force.recreate";
    public static final String PUBLIC_KEY_ALIAS_PROPERTY = "edc.iam.sts.publickey.alias";
    public static final String PRIVATE_KEY_ALIAS_PROPERTY = "edc.iam.sts.privatekey.alias";

    @Setting(description = "Service endpoints to be defined for the Super-User", key = SUPERUSER_SERVICES_PROPERTY, required = false, defaultValue = "[]")
    private String services;

    @Setting(description = "When the Super-User already exists, update the services of its DID document that differ "
            + "from " + SUPERUSER_SERVICES_PROPERTY + " (matched by id). When false, the differences are only logged.",
            key = SUPERUSER_SERVICES_SYNC_PROPERTY, required = false, defaultValue = "false")
    private String syncServices;

    @Setting(description = "STS public key alias", key = PUBLIC_KEY_ALIAS_PROPERTY)
    private String publicKeyAlias;

    @Setting(description = "STS private key alias", key = PRIVATE_KEY_ALIAS_PROPERTY)
    private String privateKeyAlias;

    @Setting(description = "Force recreate participant context", key = SUPERUSER_FORCE_RECREATE_PROPERTY, required = false, defaultValue = "false")
    private String recreate;

    @Inject
    private Monitor monitor;

    @Inject
    private IdentityHubParticipantContextService participantContextService;

    @Inject
    private ParticipantContextConfigService participantContextConfigService;

    @Inject
    private Vault vault;

    @Inject
    private TypeManager typeManager;

    @Inject
    private DidResourceStore didResourceStore;

    @Inject
    private DidDocumentService didDocumentService;

    @Inject
    private TransactionContext transactionContext;

    private String participantId;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        participantId = context.getSetting("edc.participant.id", "default-participant");
    }

    @Override
    public void start() {
        // Pre-seed empty config so the vault falls through to the global fallback client on restart.
        seedEmptyParticipantContextConfig();

        if (Boolean.parseBoolean(recreate)) {
            participantContextService.deleteParticipantContext(participantId)
                    .orElseThrow(failure -> new EdcException(failure.getFailureDetail()));
            monitor.debug("Force deletion of participant context with ID '%s'".formatted(participantId));
            // Re-seed after deletion so vault fallback remains reachable during re-creation.
            seedEmptyParticipantContextConfig();
        }

        // create super-user
        if (participantContextService.getParticipantContext(participantId).succeeded()) { // already exists
            monitor.debug("Super-user already exists with ID '%s', will not re-create".formatted(participantId));
            syncDidServices();
            return;
        }

        ofNullable(vault.resolveSecret(publicKeyAlias))
                .map(this::createParticipant)
                .orElseThrow(() -> new EdcException("Failed to find public key with is '%s' in vault".formatted(publicKeyAlias)));
    }

    private void seedEmptyParticipantContextConfig() {
        var existing = participantContextConfigService.get(participantId);
        if (existing.succeeded()) {
            monitor.debug("ParticipantContextConfiguration already exists for '%s', skipping seed".formatted(participantId));
            return;
        }
        if (existing.getFailure().getReason() != ServiceFailure.Reason.NOT_FOUND) {
            throw new EdcException("Failed to check ParticipantContextConfiguration for '%s': %s"
                    .formatted(participantId, existing.getFailureDetail()));
        }
        var emptyConfig = ParticipantContextConfiguration.Builder.newInstance()
                .participantContextId(participantId)
                .entries(Map.of())
                .privateEntries(Map.of())
                .build();
        participantContextConfigService.save(emptyConfig)
                .orElseThrow(f -> new EdcException("Failed to seed ParticipantContextConfiguration for '%s': %s"
                        .formatted(participantId, f.getFailureDetail())));
        monitor.debug("Seeded in-memory ParticipantContextConfiguration for '%s'".formatted(participantId));
    }

    private CreateParticipantContextResponse createParticipant(String publicKeyPem) {
        var builder = ParticipantManifest.Builder.newInstance()
                .participantContextId(participantId)
                .did(participantId)
                .active(true)
                .key(KeyDescriptor.Builder.newInstance()
                        .keyId("%s#%s".formatted(participantId, "my-key"))
                        .privateKeyAlias(privateKeyAlias)
                        .publicKeyPem(publicKeyPem)
                        .build())
                .scopes(List.of(IdentityApiScopes.ADMIN, IssuerAdminApiScopes.ADMIN));
        configuredServices().forEach(builder::serviceEndpoint);
        return participantContextService.createParticipantContext(builder.build())
                .orElseThrow(failure -> new EdcException(failure.getFailureDetail()));
    }

    private List<Service> configuredServices() {
        // a literal "null" (e.g. an empty chart value) is read as no services
        return ofNullable(typeManager.readValue(services, SERVICE_LIST)).orElse(List.of());
    }

    /**
     * Brings the services of the existing Super-User DID document in line with {@link #SUPERUSER_SERVICES_PROPERTY}.
     *
     * <p>The configuration is otherwise only applied when the participant is created, so a changed endpoint (e.g. the
     * {@code FederatedCatalogService} URL) never reaches an existing environment. The upstream
     * {@code DidDocumentService.replaceService} of IdentityHub 0.18.1 appends instead of replacing, so the document is
     * updated here directly, in one transaction: every entry with a configured id is replaced by the configured one, at
     * the position of the first entry (duplicates are removed). Services whose id is not configured are never touched.
     *
     * <p>With {@link #SUPERUSER_SERVICES_SYNC_PROPERTY} {@code false} (default) the differences are only logged. An
     * invalid configuration (empty entry, missing id, type or endpoint, duplicated id) is logged and skips the check.
     */
    private void syncDidServices() {
        var configured = configuredServices();
        if (configured.isEmpty()) {
            return;
        }
        var invalid = validate(configured);
        if (invalid != null) {
            monitor.warning("%s: %s, DID document services of '%s' are not checked"
                    .formatted(SUPERUSER_SERVICES_PROPERTY, invalid, participantId));
            return;
        }
        var apply = Boolean.parseBoolean(syncServices);
        monitor.info("DID document services sync for '%s': %s=%s".formatted(participantId, SUPERUSER_SERVICES_SYNC_PROPERTY, apply));
        var updated = transactionContext.execute(() -> {
            var resource = didResourceStore.findById(participantId);
            if (resource == null) {
                monitor.warning("DID document '%s' not found: cannot check its services".formatted(participantId));
                return false;
            }
            var current = resource.getDocument().getService();
            var outdated = configured.stream().filter(service -> !isUpToDate(current, service)).toList();
            if (outdated.isEmpty()) {
                monitor.debug("DID document '%s' services match the configuration".formatted(participantId));
                warnAboutSharedTypes(current, configured);
                return false;
            }
            outdated.forEach(service -> monitor.warning("DID document '%s': service '%s' %s from %s to %s".formatted(
                    participantId, service.getId(), apply ? "updated" : "differs (not updated)",
                    describe(current, service.getId()), describe(service))));
            if (!apply) {
                monitor.warning("Set %s=true to apply the configured services to DID document '%s'"
                        .formatted(SUPERUSER_SERVICES_SYNC_PROPERTY, participantId));
                warnAboutSharedTypes(current, configured);
                return false;
            }
            // computed on a copy, the stored list is only changed once the result is complete
            var synced = replace(current, outdated);
            current.clear();
            current.addAll(synced);
            warnAboutSharedTypes(current, configured);
            didResourceStore.update(resource)
                    .orElseThrow(f -> new EdcException("Failed to update the services of DID document '%s': %s"
                            .formatted(participantId, f.getFailureDetail())));
            return true;
        });
        if (updated) {
            republish();
        }
    }

    /**
     * The stored document is already served if it was published: publishing again only notifies the listeners, so a
     * failure (result or exception) is a warning and does not stop the startup.
     */
    private void republish() {
        try {
            didDocumentService.publish(participantId)
                    .onFailure(f -> monitor.warning("DID document '%s' updated but not re-published: %s"
                            .formatted(participantId, f.getFailureDetail())));
        } catch (RuntimeException e) {
            monitor.warning("DID document '%s' updated but not re-published: %s".formatted(participantId, e.getMessage()), e);
        }
    }

    private static String validate(List<Service> configured) {
        var ids = new HashSet<String>();
        for (var service : configured) {
            if (service == null) {
                return "a service entry is empty (null)";
            }
            if (isBlank(service.getId()) || isBlank(service.getType()) || isBlank(service.getServiceEndpoint())) {
                return "service %s has no id, type or serviceEndpoint".formatted(describe(service));
            }
            if (!ids.add(service.getId())) {
                return "service id '%s' is configured more than once".formatted(service.getId());
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * The DID resolvers pick a service by type ({@code findFirst}): another entry with the same type as a configured
     * service but a different id is never touched by the sync and may still be the one that is used.
     */
    private void warnAboutSharedTypes(List<Service> current, List<Service> configured) {
        for (var service : configured) {
            var others = current.stream()
                    .filter(entry -> Objects.equals(entry.getType(), service.getType()) && !Objects.equals(entry.getId(), service.getId()))
                    .map(Service::getId)
                    .toList();
            if (!others.isEmpty()) {
                monitor.warning("DID document '%s': services %s have the same type '%s' as configured service '%s' and are not managed by the sync"
                        .formatted(participantId, others, service.getType(), service.getId()));
            }
        }
    }

    private static boolean isUpToDate(List<Service> current, Service configured) {
        var matching = current.stream().filter(service -> Objects.equals(service.getId(), configured.getId())).toList();
        return matching.size() == 1
                && Objects.equals(matching.get(0).getType(), configured.getType())
                && Objects.equals(matching.get(0).getServiceEndpoint(), configured.getServiceEndpoint());
    }

    /**
     * One pass over the stored services: the first entry with an outdated id is replaced by the configured service,
     * further entries with that id are dropped, everything else is kept in order. Outdated services that are not
     * stored at all are appended.
     */
    private static List<Service> replace(List<Service> current, List<Service> outdated) {
        var byId = new LinkedHashMap<String, Service>();
        outdated.forEach(service -> byId.put(service.getId(), service));
        var written = new HashSet<String>();
        var result = new ArrayList<Service>(current.size() + outdated.size());
        for (var service : current) {
            var replacement = service.getId() == null ? null : byId.get(service.getId());
            if (replacement == null) {
                result.add(service);
            } else if (written.add(replacement.getId())) {
                result.add(replacement);
            }
        }
        byId.values().stream().filter(service -> !written.contains(service.getId())).forEach(result::add);
        return result;
    }

    private static String describe(List<Service> current, String id) {
        var matching = current.stream()
                .filter(service -> Objects.equals(service.getId(), id))
                .map(ParticipantContextSeedExtension::describe)
                .toList();
        return matching.isEmpty() ? "<absent>" : String.join(", ", matching);
    }

    private static String describe(Service service) {
        return "%s %s".formatted(service.getType(), service.getServiceEndpoint());
    }

}
