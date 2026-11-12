package org.eclipse.edc.dse.catalog.count;

import jakarta.json.Json;
import org.eclipse.edc.catalog.spi.QueryService;
import org.eclipse.edc.dse.common.DseNamespaceConfig;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.spi.WebService;

import java.util.Map;

import static org.eclipse.edc.catalog.spi.FccApiContexts.CATALOG_QUERY;

/**
 * Registers the federated catalog asset-count endpoint in the existing catalog API context.
 */
@Extension(value = CatalogAssetCountApiExtension.NAME)
public class CatalogAssetCountApiExtension implements ServiceExtension {

    public static final String NAME = "Federated Catalog Asset Count API";
    private static final String DSE_PREFIX = "dse";

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
        jsonLd.registerNamespace(DSE_PREFIX, namespaceConfig.dseNamespace());
        var controller = new CatalogAssetCountApiController(queryService, transformerRegistry, participantIdMapper,
                Json.createBuilderFactory(Map.of()), namespaceConfig.dseNamespace());
        webService.registerResource(CATALOG_QUERY, controller);
    }
}