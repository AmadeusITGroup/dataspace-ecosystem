package org.eclipse.eonax.identityhub.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.identityhub.spi.participantcontext.ParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.CreateParticipantContextResponse;
import org.eclipse.edc.identityhub.spi.participantcontext.model.KeyDescriptor;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

import java.util.List;

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
        participantId = context.getParticipantId();
    }

    @Override
    public void start() {
        if (Boolean.parseBoolean(recreate)) {
            participantContextService.deleteParticipantContext(participantId)
                    .orElseThrow(failure -> new EdcException(failure.getFailureDetail()));
            monitor.debug("Force deletion of participant context with ID '%s'".formatted(participantId));
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

    private CreateParticipantContextResponse createParticipant(String publicKeyPem) {
        var tr = new TypeReference<List<Service>>() {
        };
        var builder = ParticipantManifest.Builder.newInstance()
                .participantId(participantId)
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
