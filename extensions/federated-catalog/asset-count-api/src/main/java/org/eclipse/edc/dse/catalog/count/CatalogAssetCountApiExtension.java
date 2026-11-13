package org.eclipse.edc.dse.catalog.count;

import jakarta.json.Json;
import org.eclipse.edc.api.management.schema.ManagementApiJsonSchema;
import org.eclipse.edc.catalog.spi.QueryService;
import org.eclipse.edc.dse.common.DseNamespaceConfig;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.jersey.providers.jsonld.JerseyJsonLdInterceptor;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;

import java.util.List;
import java.util.Map;

import static org.eclipse.edc.api.management.ManagementApi.MANAGEMENT_API_CONTEXT;
import static org.eclipse.edc.api.management.ManagementApi.MANAGEMENT_SCOPE;
import static org.eclipse.edc.api.management.ManagementApi.MANAGEMENT_SCOPE_V4;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

/**
 * Registers the DSE federated catalog asset-count endpoint {@code POST /v4/catalogs/query/count} in the management API of
 * the federated catalog, next to the upstream {@code POST /v4/catalogs/request}.
 *
 * <p>It follows the upstream management v4 conventions: the {@code MANAGEMENT_API:v4} JSON-LD scope (compaction with
 * the {@code management/v2} context), the {@code management-api} transformer context and the v4 request validation
 * (compact {@code @type} checked before expansion), as the upstream {@code CatalogsApiV4Controller} of the same runtime.
 *
 * <p>The {@code dse} prefix is registered on the management v3 and v4 scopes of the federated catalog, so the DSE
 * vocabulary comes back compacted ({@code dse:datasetCount}), as it did before the management v4 migration. The
 * control plane does not register it.
 */
@Extension(value = CatalogAssetCountApiExtension.NAME)
public class CatalogAssetCountApiExtension implements ServiceExtension {

    public static final String NAME = "Federated Catalog Asset Count API";
    static final String DSE_PREFIX = "dse";
    /**
     * Management API v3 and v4 of the federated catalog.
     */
    static final List<String> DSE_PREFIX_SCOPES = List.of(MANAGEMENT_SCOPE, MANAGEMENT_SCOPE_V4);

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
    private TypeManager typeManager;
    @Inject
    private DseNamespaceConfig namespaceConfig;
    @Inject
    private JsonObjectValidatorRegistry validatorRegistry;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        registerDseNamespace(jsonLd, namespaceConfig);
        var managementTransformers = transformerRegistry.forContext(MANAGEMENT_API_CONTEXT);
        var controller = new CatalogAssetCountApiController(queryService, managementTransformers, participantIdMapper,
                Json.createBuilderFactory(Map.of()), namespaceConfig.dseNamespace());
        webService.registerResource(ApiContext.MANAGEMENT, controller);
        webService.registerDynamicResource(ApiContext.MANAGEMENT, CatalogAssetCountApiController.class,
                new JerseyJsonLdInterceptor(jsonLd, typeManager, JSON_LD, MANAGEMENT_SCOPE_V4,
                        validatorRegistry, ManagementApiJsonSchema.V4.version()));
    }

    static void registerDseNamespace(JsonLd jsonLd, DseNamespaceConfig namespaceConfig) {
        DSE_PREFIX_SCOPES.forEach(scope -> jsonLd.registerNamespace(DSE_PREFIX, namespaceConfig.dseNamespace(), scope));
    }
}
