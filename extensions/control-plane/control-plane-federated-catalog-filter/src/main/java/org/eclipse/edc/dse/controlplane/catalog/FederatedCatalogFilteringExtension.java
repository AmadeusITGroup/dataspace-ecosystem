package org.eclipse.edc.dse.controlplane.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.dse.controlplane.catalog.filter.AuthorityCatalogDidResolver;
import org.eclipse.edc.dse.controlplane.catalog.filter.CatalogFilteringController;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.transform.transformer.edc.to.JsonObjectToCriterionTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonObjectToQuerySpecTransformer;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;

import java.time.Clock;

@Extension("Extension for participant catalog filtering")
public class FederatedCatalogFilteringExtension implements ServiceExtension {

    @Inject
    IdentityService identityService;

    @Inject
    private WebService webService;

    @Setting(description = "Authority did", key = "dse.authority.did", required = true)
    public String authorityDid;

    @Inject
    private Clock clock;

    @Inject
    private TypeTransformerRegistry transformerRegistry;

    @Override
    public String name() {
        return "Participant Catalog Filtering Extension";
    }

    @Inject
    private DidResolverRegistry didResolverRegistry;

    @Override
    public void initialize(ServiceExtensionContext context) {
        registerTransformers();
        webService.registerResource(ApiContext.MANAGEMENT, new CatalogFilteringController(
                new AuthorityCatalogDidResolver(didResolverRegistry, authorityDid), context.getMonitor(), identityService, context.getParticipantId(), clock, authorityDid, new ObjectMapper(), transformerRegistry));
    }

    private void registerTransformers() {
        transformerRegistry.register(new JsonObjectToQuerySpecTransformer());
        transformerRegistry.register(new JsonObjectToCriterionTransformer());
    }

}