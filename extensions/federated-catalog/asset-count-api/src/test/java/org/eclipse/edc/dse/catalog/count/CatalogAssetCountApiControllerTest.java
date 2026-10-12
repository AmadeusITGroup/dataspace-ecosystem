package org.eclipse.edc.dse.catalog.count;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.catalog.spi.QueryService;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.jsonld.JsonLdExtension;
import org.eclipse.edc.junit.extensions.TestServiceExtensionContext;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.query.SortOrder;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.jsonld.spi.Namespaces.DCAT_SCHEMA;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_CATALOG_TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DSPACE_PROPERTY_PARTICIPANT_ID_TERM;
import static org.eclipse.edc.protocol.dsp.spi.type.Dsp2025Constants.DSP_NAMESPACE_V_2025_1;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogAssetCountApiControllerTest {

    private static final String DSE_NAMESPACE = "https://w3id.org/dse/v0.0.1/ns/";
    private static final String PARTICIPANT_ID_PROPERTY =
            DSP_NAMESPACE_V_2025_1.toIri(DSPACE_PROPERTY_PARTICIPANT_ID_TERM);

    private final QueryService queryService = mock();
    private final TypeTransformerRegistry transformerRegistry = mock();
    private final ParticipantIdMapper participantIdMapper = mock();
    private CatalogAssetCountApiController controller;

    @BeforeEach
    void setUp() {
        controller = new CatalogAssetCountApiController(queryService, transformerRegistry, participantIdMapper,
                Json.createBuilderFactory(Map.of()), DSE_NAMESPACE);
    }

    @Test
    void shouldApplyOnlyFiltersAndReturnCountPerCatalog() {
        var request = Json.createObjectBuilder().build();
        var criterion = new Criterion("properties.publisher", "=", "publisher-alpha");
        var requestedSpec = QuerySpec.Builder.newInstance()
                .filter(criterion)
                .offset(7)
                .limit(1)
                .sortField("properties.identifier")
                .sortOrder(SortOrder.DESC)
                .build();
        var catalog = catalog("participant-a", "dataset-a", "dataset-b");

        when(transformerRegistry.transform(request, QuerySpec.class)).thenReturn(Result.success(requestedSpec));
        when(queryService.getCatalog(any())).thenReturn(ServiceResult.success(List.of(catalog)));
        when(participantIdMapper.toIri("participant-a")).thenReturn("did:web:participant-a");

        var response = controller.countAssets(request);

        var queryCaptor = ArgumentCaptor.forClass(QuerySpec.class);
        verify(queryService).getCatalog(queryCaptor.capture());
        var countingSpec = queryCaptor.getValue();
        assertThat(countingSpec.getFilterExpression()).containsExactly(criterion);
        assertThat(countingSpec.getOffset()).isZero();
        assertThat(countingSpec.getLimit()).isEqualTo(Integer.MAX_VALUE);
        assertThat(countingSpec.getSortField()).isNull();

        assertThat(response).hasSize(1);
        var count = response.getJsonObject(0);
        assertThat(count.getString(TYPE)).isEqualTo(DCAT_CATALOG_TYPE);
        assertThat(count.getInt(DSE_NAMESPACE + CatalogAssetCountApiController.DATASET_COUNT_TERM)).isEqualTo(2);
        assertThat(count.getJsonObject(PARTICIPANT_ID_PROPERTY).getString(ID)).isEqualTo("did:web:participant-a");
        verify(transformerRegistry, never()).transform(eq(catalog), eq(JsonObject.class));
        verify(participantIdMapper).toIri("participant-a");
    }

    @Test
    void shouldOmitCatalogsWithoutMatchingDatasets() {
        var emptyCatalog = Catalog.Builder.newInstance().id("catalog-empty").participantId("participant-a").build();
        when(queryService.getCatalog(any())).thenReturn(ServiceResult.success(List.of(emptyCatalog)));

        var response = controller.countAssets(null);

        assertThat(response).isEmpty();
        verify(transformerRegistry, never()).transform(eq(emptyCatalog), eq(JsonObject.class));
    }

    @Test
    void shouldCompactDatasetCountAsDseInteger() {
        var catalog = catalog("participant-a", "dataset-a", "dataset-b");
        when(queryService.getCatalog(any())).thenReturn(ServiceResult.success(List.of(catalog)));
        when(participantIdMapper.toIri("participant-a")).thenReturn("did:web:participant-a");
        var expanded = controller.countAssets(null).getJsonObject(0);
        var jsonLd = new JsonLdExtension().createJsonLdService(TestServiceExtensionContext.testServiceExtensionContext());
        jsonLd.registerNamespace(VOCAB, DCAT_SCHEMA);
        jsonLd.registerNamespace("dspace", DSP_NAMESPACE_V_2025_1.toIri(""));
        jsonLd.registerNamespace("dse", DSE_NAMESPACE);

        var compacted = jsonLd.compact(expanded).getContent();

        assertThat(compacted.getString(TYPE)).isEqualTo("Catalog");
        assertThat(compacted.getInt("dse:datasetCount")).isEqualTo(2);
    }

    @Test
    void shouldOmitMissingParticipantId() {
        var catalog = catalog(null, "dataset-a");
        when(queryService.getCatalog(any())).thenReturn(ServiceResult.success(List.of(catalog)));

        var count = controller.countAssets(null).getJsonObject(0);

        assertThat(count).doesNotContainKey(PARTICIPANT_ID_PROPERTY);
        verify(participantIdMapper, never()).toIri(any());
    }

    private static Catalog catalog(String participantId, String... datasetIds) {
        var builder = Catalog.Builder.newInstance().id("catalog-" + participantId).participantId(participantId);
        for (var datasetId : datasetIds) {
            builder.dataset(Dataset.Builder.newInstance().id(datasetId).build());
        }
        return builder.build();
    }

}