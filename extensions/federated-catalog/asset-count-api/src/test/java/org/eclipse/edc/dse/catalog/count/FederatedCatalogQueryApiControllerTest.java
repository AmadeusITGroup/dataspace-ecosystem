package org.eclipse.edc.dse.catalog.count;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.catalog.spi.QueryService;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.jersey.testfixtures.RestControllerTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.io.StringReader;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.spi.query.QuerySpec.EDC_QUERY_SPEC_TYPE;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FederatedCatalogQueryApiControllerTest extends RestControllerTestBase {

    private final QueryService queryService = mock();
    private final TypeTransformerRegistry transformerRegistry = mock();

    @Test
    void shouldReturnTransformedCatalogs_whenQuerySpecIsValid() {
        var criterion = new Criterion("https://w3id.org/edc/v0.0.1/ns/id", "=", "dataset-a");
        var querySpec = QuerySpec.Builder.newInstance().filter(criterion).limit(5).build();
        var catalog1 = catalog("catalog-1", "participant-a", "dataset-a");
        var catalog2 = catalog("catalog-2", "participant-b", "dataset-b");
        when(transformerRegistry.transform(isA(JsonObject.class), eq(QuerySpec.class))).thenReturn(Result.success(querySpec));
        when(queryService.getCatalog(any())).thenReturn(ServiceResult.success(List.of(catalog1, catalog2)));
        when(transformerRegistry.transform(catalog1, JsonObject.class)).thenReturn(Result.success(catalogJson("catalog-1")));
        when(transformerRegistry.transform(catalog2, JsonObject.class)).thenReturn(Result.success(catalogJson("catalog-2")));

        baseRequest()
                .contentType(ContentType.JSON)
                .body(querySpecJson())
                .post()
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", is(2))
                .body("[0].@id", is("catalog-1"))
                .body("[1].@id", is("catalog-2"));

        var querySpecCaptor = ArgumentCaptor.forClass(QuerySpec.class);
        verify(queryService).getCatalog(querySpecCaptor.capture());
        assertThat(querySpecCaptor.getValue()).isSameAs(querySpec);
        verify(transformerRegistry).transform(catalog1, JsonObject.class);
        verify(transformerRegistry).transform(catalog2, JsonObject.class);
    }

    @Test
    void shouldReturnTransformerOutputKeysUnchanged() {
        // the controller must not re-key/compact what the management-api transformers produce;
        // compaction is the job of the JerseyJsonLdInterceptor registered on the catalog context
        var catalog = catalog("catalog-1", "participant-a", "dataset-a");
        var transformed = Json.createObjectBuilder()
                .add(ID, "catalog-1")
                .add(TYPE, "dcat:Catalog")
                .add("dcat:dataset", Json.createArrayBuilder().add(Json.createObjectBuilder().add(ID, "dataset-a")))
                .add("dspace:participantId", "did:web:participant-a")
                .add("https://w3id.org/dse/v0.0.1/ns/custom", "value")
                .build();
        when(queryService.getCatalog(any())).thenReturn(ServiceResult.success(List.of(catalog)));
        when(transformerRegistry.transform(catalog, JsonObject.class)).thenReturn(Result.success(transformed));

        var body = baseRequest()
                .contentType(ContentType.JSON)
                .post()
                .then()
                .statusCode(200)
                .extract().body().asString();

        var response = Json.createReader(new StringReader(body)).readArray();
        assertThat(response).hasSize(1);
        assertThat(response.getJsonObject(0)).isEqualTo(transformed);
    }

    @Test
    void shouldUseDefaultQuerySpec_whenBodyIsEmpty() {
        when(queryService.getCatalog(any())).thenReturn(ServiceResult.success(List.of()));

        baseRequest()
                .contentType(ContentType.JSON)
                .post()
                .then()
                .statusCode(200)
                .body("$", hasSize(0));

        var querySpecCaptor = ArgumentCaptor.forClass(QuerySpec.class);
        verify(queryService).getCatalog(querySpecCaptor.capture());
        assertThat(querySpecCaptor.getValue()).usingRecursiveComparison().isEqualTo(QuerySpec.none());
        verify(transformerRegistry, never()).transform(any(), eq(QuerySpec.class));
    }

    @Test
    void shouldReturnBadRequest_whenQuerySpecCannotBeTransformed() {
        when(transformerRegistry.transform(isA(JsonObject.class), eq(QuerySpec.class)))
                .thenReturn(Result.failure("invalid query spec"));

        baseRequest()
                .contentType(ContentType.JSON)
                .body(querySpecJson())
                .post()
                .then()
                .statusCode(400);

        verifyNoInteractions(queryService);
    }

    @ParameterizedTest(name = "{index}: expected status {1}")
    @MethodSource("serviceFailures")
    void shouldMapQueryServiceFailure(ServiceResult<Collection<Catalog>> failure, int expectedStatus) {
        when(queryService.getCatalog(any())).thenReturn(failure);

        baseRequest()
                .contentType(ContentType.JSON)
                .post()
                .then()
                .statusCode(expectedStatus);

        verify(transformerRegistry, never()).transform(isA(Catalog.class), eq(JsonObject.class));
    }

    @Test
    void shouldSilentlyDropCatalogs_whenOutputTransformationFails() {
        var good = catalog("catalog-good", "participant-a", "dataset-a");
        var bad = catalog("catalog-bad", "participant-b", "dataset-b");
        when(queryService.getCatalog(any())).thenReturn(ServiceResult.success(List.of(good, bad)));
        when(transformerRegistry.transform(good, JsonObject.class)).thenReturn(Result.success(catalogJson("catalog-good")));
        when(transformerRegistry.transform(bad, JsonObject.class)).thenReturn(Result.failure("cannot transform"));

        baseRequest()
                .contentType(ContentType.JSON)
                .post()
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].@id", is("catalog-good"));
    }

    @Test
    void shouldFlattenNestedCatalogs_whenFlattenIsTrue() {
        var nested = catalog("catalog-nested", "participant-b", "dataset-nested");
        var root = Catalog.Builder.newInstance().id("catalog-root").participantId("participant-a")
                .dataset(Dataset.Builder.newInstance().id("dataset-root").build())
                .dataset(nested)
                .build();
        when(queryService.getCatalog(any())).thenReturn(ServiceResult.success(List.of(root)));
        when(transformerRegistry.transform(isA(Catalog.class), eq(JsonObject.class)))
                .thenReturn(Result.success(catalogJson("catalog-root")));

        baseRequest()
                .contentType(ContentType.JSON)
                .queryParam("flatten", true)
                .post()
                .then()
                .statusCode(200)
                .body("size()", is(1));

        var catalogCaptor = ArgumentCaptor.forClass(Catalog.class);
        verify(transformerRegistry).transform(catalogCaptor.capture(), eq(JsonObject.class));
        var flattened = catalogCaptor.getValue();
        assertThat(flattened.getDatasets()).noneMatch(Catalog.class::isInstance);
        assertThat(flattened.getDatasets()).extracting(Dataset::getId)
                .containsExactlyInAnyOrder("dataset-root", "dataset-nested");
    }

    @Test
    void shouldNotFlattenByDefault() {
        var nested = catalog("catalog-nested", "participant-b", "dataset-nested");
        var root = Catalog.Builder.newInstance().id("catalog-root").participantId("participant-a")
                .dataset(nested)
                .build();
        when(queryService.getCatalog(any())).thenReturn(ServiceResult.success(List.of(root)));
        when(transformerRegistry.transform(root, JsonObject.class)).thenReturn(Result.success(catalogJson("catalog-root")));

        baseRequest()
                .contentType(ContentType.JSON)
                .post()
                .then()
                .statusCode(200)
                .body("size()", is(1));

        verify(transformerRegistry).transform(root, JsonObject.class);
    }

    @Override
    protected Object controller() {
        return new FederatedCatalogQueryApiController(queryService, transformerRegistry);
    }

    private static Stream<Arguments> serviceFailures() {
        return Stream.of(
                Arguments.of(ServiceResult.badRequest("bad"), 400),
                Arguments.of(ServiceResult.notFound("missing"), 404),
                Arguments.of(ServiceResult.conflict("conflict"), 409),
                // EDC maps UNAUTHORIZED to NotAuthorizedException, rendered as 403
                Arguments.of(ServiceResult.unauthorized("unauthorized"), 403),
                Arguments.of(ServiceResult.unexpected("boom"), 500)
        );
    }

    private RequestSpecification baseRequest() {
        return given()
                .baseUri("http://localhost:" + port)
                .basePath("/v1alpha/catalog/query")
                .when();
    }

    private static JsonObject querySpecJson() {
        return Json.createObjectBuilder()
                .add(TYPE, EDC_QUERY_SPEC_TYPE)
                .add("https://w3id.org/edc/v0.0.1/ns/limit", 5)
                .build();
    }

    private static JsonObject catalogJson(String id) {
        return Json.createObjectBuilder().add(ID, id).add(TYPE, "dcat:Catalog").build();
    }

    private static Catalog catalog(String id, String participantId, String... datasetIds) {
        var builder = Catalog.Builder.newInstance().id(id).participantId(participantId);
        for (var datasetId : datasetIds) {
            builder.dataset(Dataset.Builder.newInstance().id(datasetId).build());
        }
        return builder.build();
    }

}
