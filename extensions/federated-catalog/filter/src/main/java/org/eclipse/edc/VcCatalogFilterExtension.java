package org.eclipse.edc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.eclipse.edc.api.VcCatalogFilterController;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.util.AuthorityCatalogFilterDidResolver;
import org.eclipse.edc.util.FederatedCatalogService;
import org.eclipse.edc.web.spi.WebService;

@Extension("VC-based Catalogue Filter Extension")
public class VcCatalogFilterExtension implements ServiceExtension {

    public static final String NAME = "Federated Catalog Filter API";

    @Inject
    private Monitor monitor;

    @Inject
    private WebService webService;

    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private IdentityService identityService;

    @Inject
    private DidResolverRegistry didResolverRegistry;

    @Setting(description = "Authority did", key = "dse.authority.did", required = true)
    public String authorityDid;

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor.info("Initializing VC Catalogue Filter Extension");
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        AuthorityCatalogFilterDidResolver didresolver = new AuthorityCatalogFilterDidResolver(didResolverRegistry, authorityDid);
        FederatedCatalogService catalogService = new FederatedCatalogService(policyEngine, objectMapper, monitor, didresolver);
        var controller = new VcCatalogFilterController(context, monitor, catalogService, identityService);
        webService.registerResource(controller);
        monitor.info("Registered Federated Catalog Filter");
    }

}
