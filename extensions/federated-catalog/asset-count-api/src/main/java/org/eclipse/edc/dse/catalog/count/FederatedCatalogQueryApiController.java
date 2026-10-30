package org.eclipse.edc.dse.catalog.count;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.edc.catalog.spi.QueryService;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.federatedcatalog.util.FederatedCatalogUtil;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.AbstractResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;
import org.eclipse.edc.web.spi.exception.ServiceResultHandler;

import static jakarta.json.stream.JsonCollectors.toJsonArray;

/**
 * Federated catalog query endpoint at its pre-0.17 location ({@code POST <catalog context>/v1alpha/catalog/query}).
 *
 * <p>EDC 0.17 (eclipse-edc/Connector#5549) moved the upstream query API into the management context as
 * {@code /v3/catalogs/request}. DSE keeps serving it on the {@code catalog} context because its address is
 * published as the {@code FederatedCatalogService} endpoint in the authority DID document and consumed by the
 * federated catalog filter. Behaviour is identical to the upstream 0.16 controller.
 */
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/v1alpha/catalog/query")
public class FederatedCatalogQueryApiController {

    private final QueryService queryService;
    private final TypeTransformerRegistry transformerRegistry;

    public FederatedCatalogQueryApiController(QueryService queryService, TypeTransformerRegistry transformerRegistry) {
        this.queryService = queryService;
        this.transformerRegistry = transformerRegistry;
    }

    @POST
    public JsonArray getCachedCatalog(JsonObject catalogQuery, @DefaultValue("false") @QueryParam("flatten") boolean flatten) {
        var querySpec = catalogQuery == null
                ? QuerySpec.none()
                : transformerRegistry.transform(catalogQuery, QuerySpec.class)
                .orElseThrow(InvalidRequestException::new);

        var catalogs = queryService.getCatalog(querySpec)
                .orElseThrow(ServiceResultHandler.exceptionMapper(Catalog.class));

        return catalogs.stream()
                .map(c -> flatten ? FederatedCatalogUtil.flatten(c) : c)
                .map(c -> transformerRegistry.transform(c, JsonObject.class))
                .filter(Result::succeeded)
                .map(AbstractResult::getContent)
                .collect(toJsonArray());
    }
}
