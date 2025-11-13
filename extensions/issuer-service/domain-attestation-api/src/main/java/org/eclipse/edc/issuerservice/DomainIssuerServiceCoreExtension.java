package org.eclipse.edc.issuerservice;

import org.eclipse.dse.spi.issuerservice.DomainAttestationStore;
import org.eclipse.edc.issuerservice.api.DomainAttestationApiController;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;

import static org.eclipse.edc.identityhub.spi.webcontext.IdentityHubApiContext.ISSUERADMIN;

@Extension(value = DomainIssuerServiceCoreExtension.NAME)
public class DomainIssuerServiceCoreExtension implements ServiceExtension {

    public static final String NAME = "Issuer Service Core";

    @Inject
    private WebService webService;
    @Inject
    private DomainAttestationStore attestationStore;

    @Override
    public String name() {
        return NAME;
    }

    @Setting(key = "authorized.domain.issuance", description = "The authorized domain the issuer service can issue to a participant", required = false)
    private String authorizedDomain;

    @Override
    public void initialize(ServiceExtensionContext context) {
        webService.registerResource(ISSUERADMIN, new DomainAttestationApiController(attestationStore, authorizedDomain));
    }

}