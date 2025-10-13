package org.eclipse.edc.issuerservice;

import org.eclipse.edc.issuerservice.api.MembershipAttestationApiController;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.eonax.spi.issuerservice.MembershipAttestationStore;

import static org.eclipse.edc.identityhub.spi.webcontext.IdentityHubApiContext.ISSUERADMIN;

@Extension(value = IssuerServiceCoreExtension.NAME)
public class IssuerServiceCoreExtension implements ServiceExtension {

    public static final String NAME = "Issuer Service Core";

    @Inject
    private WebService webService;
    @Inject
    private MembershipAttestationStore attestationStore;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        webService.registerResource(ISSUERADMIN, new MembershipAttestationApiController(attestationStore));
    }

}