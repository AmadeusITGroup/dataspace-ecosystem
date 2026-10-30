package org.eclipse.edc.dse.catalog.count;

import jakarta.json.Json;
import org.eclipse.edc.catalog.spi.QueryService;
import org.eclipse.edc.dse.common.DseNamespaceConfig;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.Settings;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.transform.transformer.edc.to.JsonObjectToCriterionTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonObjectToQuerySpecTransformer;
import org.eclipse.edc.web.jersey.providers.jsonld.JerseyJsonLdInterceptor;
import org.eclipse.edc.web.jersey.providers.jsonld.ObjectMapperProvider;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;

import java.util.Map;

import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_CONTEXT_2025_1;
import static org.eclipse.edc.jsonld.spi.Namespaces.EDC_DSPACE_CONTEXT;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

/**
 * Registers the DSE federated catalog endpoints (asset count and the legacy {@code /v1alpha/catalog/query})
 * on a DSE-owned {@code catalog} API context.
 *
 * <p>Up to EDC 0.16 this context was provided by the upstream federated catalog query API. Since EDC 0.17
 * (eclipse-edc/Connector#5549) that API lives in the management context and the {@code catalog} context is no
 * longer registered, so DSE registers the port mapping, JSON-LD handling and query transformers itself. The
 * configuration keys ({@code web.http.catalog.port/path}) and the resulting URL are unchanged.
 */
@Extension(value = CatalogAssetCountApiExtension.NAME)
public class CatalogAssetCountApiExtension implements ServiceExtension {

    public static final String NAME = "Federated Catalog Asset Count API";
    private static final String CATALOG_CONTEXT = "catalog";
    private static final String CATALOG_SCOPE = "DSE_CATALOG_API";
    private static final String MANAGEMENT_API_TRANSFORMER_CONTEXT = "management-api";
    private static final String DSE_PREFIX = "dse";

    @Configuration
    private CatalogApiConfiguration apiConfiguration;
    @Inject
    private PortMappingRegistry portMappingRegistry;
    @Inject
    private TypeManager typeManager;
    @Inject
    private CriterionOperatorRegistry criterionOperatorRegistry;

    @Inject
    private WebService webService;
    @Inject
    private QueryService queryService;
    @Inject
    private TypeTransformerRegistry transformerRegistry;
    @Inject
    private ParticipantIdMapper participantIdMapper;
    @Inject
    private JsonLd jsonLd;
    @Inject
    private DseNamespaceConfig namespaceConfig;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        portMappingRegistry.register(new PortMapping(CATALOG_CONTEXT, apiConfiguration.port(), apiConfiguration.path()));
        jsonLd.registerNamespace(DSE_PREFIX, namespaceConfig.dseNamespace());
        jsonLd.registerContext(DSPACE_CONTEXT_2025_1, CATALOG_SCOPE);
        jsonLd.registerContext(EDC_DSPACE_CONTEXT, CATALOG_SCOPE);
        jsonLd.registerNamespace(DSE_PREFIX, namespaceConfig.dseNamespace(), CATALOG_SCOPE);
        transformerRegistry.register(new JsonObjectToQuerySpecTransformer());
        transformerRegistry.register(new JsonObjectToCriterionTransformer(criterionOperatorRegistry));
        var controller = new CatalogAssetCountApiController(queryService, transformerRegistry, participantIdMapper,
                Json.createBuilderFactory(Map.of()), namespaceConfig.dseNamespace());
        webService.registerResource(CATALOG_CONTEXT, controller);
        // the catalog -> JSON-LD transformers are registered by the upstream FC API extension in this context
        webService.registerResource(CATALOG_CONTEXT, new FederatedCatalogQueryApiController(queryService,
                transformerRegistry.forContext(MANAGEMENT_API_TRANSFORMER_CONTEXT)));
        webService.registerResource(CATALOG_CONTEXT, new ObjectMapperProvider(typeManager, JSON_LD));
        webService.registerResource(CATALOG_CONTEXT, new JerseyJsonLdInterceptor(jsonLd, typeManager, JSON_LD, CATALOG_SCOPE));
    }

    @Settings
    record CatalogApiConfiguration(
            @Setting(key = "web.http." + CATALOG_CONTEXT + ".port",
                    description = "Port for the " + CATALOG_CONTEXT + " api context", defaultValue = "8383")
            int port,
            @Setting(key = "web.http." + CATALOG_CONTEXT + ".path",
                    description = "Path for the " + CATALOG_CONTEXT + " api context", defaultValue = "/api/catalog")
            String path
    ) {
    }
}