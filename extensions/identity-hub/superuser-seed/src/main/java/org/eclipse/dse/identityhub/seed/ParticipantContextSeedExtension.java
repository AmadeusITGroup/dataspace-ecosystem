package org.eclipse.dse.identityhub.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.identityhub.spi.participantcontext.ParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.CreateParticipantContextResponse;
import org.eclipse.edc.identityhub.spi.participantcontext.model.KeyDescriptor;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.participantcontext.spi.config.model.ParticipantContextConfiguration;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.result.ServiceFailure;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

import java.util.List;
import java.util.Map;

import static java.util.Optional.ofNullable;
import static org.eclipse.edc.identityhub.spi.authentication.ServicePrincipal.ROLE_ADMIN;

public class ParticipantContextSeedExtension implements ServiceExtension {

    public static final String NAME = "Root User Seeding";

    public static final String SUPERUSER_SERVICES_PROPERTY = "edc.ih.api.superuser.services";
    public static final String SUPERUSER_FORCE_RECREATE_PROPERTY = "edc.ih.api.superuser.force.recreate";
    public static final String PUBLIC_KEY_ALIAS_PROPERTY = "edc.iam.sts.publickey.alias";
    public static final String PRIVATE_KEY_ALIAS_PROPERTY = "edc.iam.sts.privatekey.alias";

    @Setting(description = "Service endpoints to be defined for the Super-User", key = SUPERUSER_SERVICES_PROPERTY, required = false, defaultValue = "[]")
    private String services;

    @Setting(description = "STS public key alias", key = PUBLIC_KEY_ALIAS_PROPERTY)
    private String publicKeyAlias;

    @Setting(description = "STS private key alias", key = PRIVATE_KEY_ALIAS_PROPERTY)
    private String privateKeyAlias;

    @Setting(description = "Force recreate participant context", key = SUPERUSER_FORCE_RECREATE_PROPERTY, required = false, defaultValue = "false")
    private String recreate;

    @Inject
    private Monitor monitor;

    @Inject
    private ParticipantContextService participantContextService;

    @Inject
    private ParticipantContextConfigService participantContextConfigService;

    @Inject
    private Vault vault;

    @Inject
    private TypeManager typeManager;

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
        var tr = new TypeReference<List<Service>>() {
        };
        var builder = ParticipantManifest.Builder.newInstance()
                .participantContextId(participantId)
                .did(participantId)
                .active(true)
                .key(KeyDescriptor.Builder.newInstance()
                        .keyId("%s#%s".formatted(participantId, "my-key"))
                        .privateKeyAlias(privateKeyAlias)
                        .publicKeyPem(publicKeyPem)
                        .build())
                .roles(List.of(ROLE_ADMIN));
        typeManager.readValue(services, tr).forEach(builder::serviceEndpoint);
        return participantContextService.createParticipantContext(builder.build())
                .orElseThrow(failure -> new EdcException(failure.getFailureDetail()));
    }

}
