package org.eclipse.dse.identityhub.legacypath;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;

import java.util.Set;

/**
 * Keeps the pre-EDC-0.17 participant-scoped URLs of the IdentityHub and IssuerService APIs working
 * (base64url participant ids, {@code /v1alpha}). See {@link LegacyParticipantPathRewriter}.
 *
 * <p>Temporary compatibility shim (PSMP-5859): remove this module once all clients have migrated to
 * the {@code /v1beta} paths.
 */
@Extension(value = LegacyParticipantPathExtension.EXTENSION_NAME)
public class LegacyParticipantPathExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "DSE Legacy Participant Path Compatibility";

    /** IdentityHub ("identity", "credentials") and IssuerService ("issuance", "issueradmin") API contexts. */
    private static final Set<String> CONTEXTS = Set.of("identity", "credentials", "issuance", "issueradmin");

    @Inject
    private WebService webService;
    @Inject
    private PortMappingRegistry portMappingRegistry;
    @Inject
    private Monitor monitor;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    /**
     * Registers the filter in prepare(), which runs after every initialize(): by then all API contexts of this
     * runtime have their port mapping, and registering a resource for an unmapped context would make the web
     * server fail at start-up.
     */
    @Override
    public void prepare() {
        portMappingRegistry.getAll().stream()
                .map(PortMapping::name)
                .filter(CONTEXTS::contains)
                .distinct()
                .forEach((String context) -> {
                    webService.registerResource(context, new LegacyParticipantPathFilter());
                    monitor.debug("Registered legacy participant path filter on API context '%s'".formatted(context));
                });
    }
}
