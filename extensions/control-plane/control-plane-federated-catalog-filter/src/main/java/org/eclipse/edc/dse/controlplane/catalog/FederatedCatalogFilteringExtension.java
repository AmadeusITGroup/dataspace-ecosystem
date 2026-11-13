package org.eclipse.edc.dse.controlplane.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.api.management.schema.ManagementApiJsonSchema;
import org.eclipse.edc.dse.controlplane.catalog.filter.AuthorityCatalogDidResolver;
import org.eclipse.edc.dse.controlplane.catalog.filter.CatalogFilteringController;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.jersey.providers.jsonld.JerseyJsonLdInterceptor;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;

import java.time.Clock;

import static org.eclipse.edc.api.management.ManagementApi.MANAGEMENT_API_CONTEXT;
import static org.eclipse.edc.api.management.ManagementApi.MANAGEMENT_SCOPE_V4;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

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
    private DidResolverRegistry didResolverRegistry;

    @Inject
    private TypeTransformerRegistry transformerRegistry;

    @Inject
    private JsonLd jsonLd;

    @Inject
    private TypeManager typeManager;

    @Inject
    private JsonObjectValidatorRegistry validatorRegistry;

    @Override
    public String name() {
        return "Participant Catalog Filtering Extension";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var participantId = context.getSetting("edc.participant.id", "default-participant");
        var didResolver = new AuthorityCatalogDidResolver(didResolverRegistry, authorityDid);
        var controller = new CatalogFilteringController(
                didResolver, context.getMonitor(), identityService,
                participantId, clock, authorityDid, new ObjectMapper(),
                transformerRegistry.forContext(MANAGEMENT_API_CONTEXT));
        webService.registerResource(ApiContext.MANAGEMENT, controller);
        // management v4 request handling (compact @type validation, expansion); the response is the catalog filter's
        // reply as is (byte[] entity), which the interceptor does not compact
        webService.registerDynamicResource(ApiContext.MANAGEMENT, CatalogFilteringController.class,
                new JerseyJsonLdInterceptor(jsonLd, typeManager, JSON_LD, MANAGEMENT_SCOPE_V4,
                        validatorRegistry, ManagementApiJsonSchema.V4.version()));
    }
}
