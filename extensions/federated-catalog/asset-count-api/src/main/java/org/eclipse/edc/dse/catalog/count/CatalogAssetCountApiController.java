package org.eclipse.edc.dse.catalog.count;

import jakarta.json.JsonArray;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.edc.catalog.spi.QueryService;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;
import org.eclipse.edc.web.spi.exception.ServiceResultHandler;

import static jakarta.json.stream.JsonCollectors.toJsonArray;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_CATALOG_TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DSPACE_PROPERTY_PARTICIPANT_ID_TERM;
import static org.eclipse.edc.protocol.dsp.spi.type.Dsp2025Constants.DSP_NAMESPACE_V_2025_1;

/**
 * Exposes a count projection of the standard federated catalog query endpoint.
 */
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/v1alpha/catalog/query/count")
public class CatalogAssetCountApiController implements CatalogAssetCountApi {

    static final String DATASET_COUNT_TERM = "datasetCount";
    private static final String PARTICIPANT_ID_PROPERTY =
            DSP_NAMESPACE_V_2025_1.toIri(DSPACE_PROPERTY_PARTICIPANT_ID_TERM);

    private final QueryService queryService;
    private final TypeTransformerRegistry transformerRegistry;
    private final ParticipantIdMapper participantIdMapper;
    private final JsonBuilderFactory jsonFactory;
    private final String datasetCountProperty;

    public CatalogAssetCountApiController(QueryService queryService, TypeTransformerRegistry transformerRegistry,
                                          ParticipantIdMapper participantIdMapper, JsonBuilderFactory jsonFactory,
                                          String dseNamespace) {
        this.queryService = queryService;
        this.transformerRegistry = transformerRegistry;
        this.participantIdMapper = participantIdMapper;
        this.jsonFactory = jsonFactory;
        datasetCountProperty = dseNamespace + DATASET_COUNT_TERM;
    }

    @POST
    @Override
    public JsonArray countAssets(JsonObject catalogQuery) {
        var requestedSpec = catalogQuery == null
                ? QuerySpec.none()
                : transformerRegistry.transform(catalogQuery, QuerySpec.class)
                .orElseThrow(InvalidRequestException::new);

        var countingSpec = QuerySpec.Builder.newInstance()
                .filter(requestedSpec.getFilterExpression())
                .offset(0)
                .limit(Integer.MAX_VALUE)
                .build();

        var catalogs = queryService.getCatalog(countingSpec)
                .orElseThrow(ServiceResultHandler.exceptionMapper(Catalog.class));

        return catalogs.stream()
                .filter(catalog -> catalog.getDatasets() != null && !catalog.getDatasets().isEmpty())
                .map(this::toCountResponse)
                .collect(toJsonArray());
    }

    private JsonObject toCountResponse(Catalog catalog) {
        var builder = jsonFactory.createObjectBuilder()
                .add(TYPE, DCAT_CATALOG_TYPE)
                .add(datasetCountProperty, catalog.getDatasets().size());
        var participantId = catalog.getParticipantId();
        if (participantId != null) {
            builder.add(PARTICIPANT_ID_PROPERTY, jsonFactory.createObjectBuilder()
                    .add(ID, participantIdMapper.toIri(participantId)));
        }
        return builder.build();
    }
}