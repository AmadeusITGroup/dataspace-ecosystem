package org.eclipse.edc.issuerservice;

import org.eclipse.dse.spi.issuerservice.VisibilityAttestationStore;
import org.eclipse.edc.issuerservice.api.VisibilityAttestationApiController;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;

import static org.eclipse.edc.identityhub.spi.webcontext.IdentityHubApiContext.ISSUERADMIN;

@Extension(value = VisibilityIssuerServiceCoreExtension.NAME)
public class VisibilityIssuerServiceCoreExtension implements ServiceExtension {

    public static final String NAME = "Visibility Attestation API";

    @Inject
    private WebService webService;

    @Inject
    private VisibilityAttestationStore attestationStore;

    @Override
    public void initialize(ServiceExtensionContext context) {
        webService.registerResource(ISSUERADMIN, new VisibilityAttestationApiController(attestationStore));
    }
}