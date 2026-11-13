package org.eclipse.edc.dse.catalog.count;

import io.restassured.specification.RequestSpecification;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.api.management.schema.ManagementApiJsonSchema;
import org.eclipse.edc.catalog.spi.QueryService;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.dse.common.DseNamespaceConfig;
import org.eclipse.edc.jsonld.JsonLdExtension;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.junit.extensions.TestServiceExtensionContext;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.web.jersey.providers.jsonld.JerseyJsonLdInterceptor;
import org.eclipse.edc.web.jersey.testfixtures.RestControllerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.api.management.ManagementApi.MANAGEMENT_SCOPE_V4;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.query.QuerySpec.EDC_QUERY_SPEC_TYPE;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HTTP-level test of {@code POST /v4/catalogs/query/count} with the management v4 JSON-LD interceptor registered as in
 * {@link CatalogAssetCountApiExtension}: the compact {@code @type} of a present body is validated before expansion.
 */
class CatalogAssetCountApiValidationTest extends RestControllerTestBase {

    private static final String DSE_NAMESPACE = "https://w3id.org/dse/v0.0.1/ns/";

    private final QueryService queryService = mock();
    private final TypeTransformerRegistry transformerRegistry = mock();
    private final JsonObjectValidatorRegistry validatorRegistry = mock();
    private final JsonLd jsonLd = new JsonLdExtension().createJsonLdService(TestServiceExtensionContext.testServiceExtensionContext());

    @BeforeEach
    void setUp() {
        when(validatorRegistry.validate(any(), any())).thenReturn(ValidationResult.success());
        when(transformerRegistry.transform(any(JsonObject.class), eq(QuerySpec.class)))
                .thenReturn(Result.success(QuerySpec.none()));
        when(queryService.getCatalog(any())).thenReturn(ServiceResult.success(List.of()));
    }

    @Test
    void countAssets_withCompactQuerySpecType_isAccepted() {
        baseRequest()
                .body(querySpec("QuerySpec"))
                .post()
                .then()
                .statusCode(200);

        verify(validatorRegistry).validate(eq("v4:QuerySpec"), any());
        var captor = ArgumentCaptor.forClass(JsonObject.class);
        verify(transformerRegistry).transform(captor.capture(), eq(QuerySpec.class));
        assertThat(captor.getValue().getJsonArray(TYPE).getString(0)).isEqualTo(EDC_QUERY_SPEC_TYPE);
    }

    @Test
    void countAssets_withExpandedQuerySpecType_isBadRequest() {
        baseRequest()
                .body(querySpec(EDC_QUERY_SPEC_TYPE))
                .post()
                .then()
                .statusCode(400);

        verifyNoInteractions(transformerRegistry, queryService);
    }

    @Test
    void countAssets_withWrongType_isBadRequest() {
        baseRequest()
                .body(querySpec("Asset"))
                .post()
                .then()
                .statusCode(400);

        verifyNoInteractions(transformerRegistry, queryService);
    }

    @Test
    void countAssets_withoutType_isBadRequest() {
        baseRequest()
                .body(Map.of(CONTEXT, Map.of(VOCAB, EDC_NAMESPACE), "limit", 10))
                .post()
                .then()
                .statusCode(400);

        verifyNoInteractions(transformerRegistry, queryService);
    }

    @Test
    void countAssets_withoutBody_usesDefaultQuery() {
        baseRequest()
                .post()
                .then()
                .statusCode(200);

        verifyNoInteractions(transformerRegistry, validatorRegistry);
        verify(queryService).getCatalog(any());
    }

    @Test
    void countAssets_returnsTheDatasetCountWithTheDsePrefix() {
        // as in the federated catalog runtime: the upstream management v2 context and the DSE prefix on the v4 scope
        jsonLd.registerContext(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2, MANAGEMENT_SCOPE_V4);
        CatalogAssetCountApiExtension.registerDseNamespace(jsonLd, new DseNamespaceConfig(DSE_NAMESPACE, "dse-policy", "https://w3id.org/dse/policy/"));
        var catalog = Catalog.Builder.newInstance().id("catalog-1")
                .dataset(Dataset.Builder.newInstance().id("dataset-a").build())
                .dataset(Dataset.Builder.newInstance().id("dataset-b").build())
                .build();
        when(queryService.getCatalog(any())).thenReturn(ServiceResult.success(List.of(catalog)));

        baseRequest()
                .post()
                .then()
                .log().body()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].'@type'", is("dcat:Catalog"))
                .body("[0].'dse:datasetCount'", is(2))
                .body("[0]", not(hasKey(DSE_NAMESPACE + CatalogAssetCountApiController.DATASET_COUNT_TERM)));
    }

    @Override
    protected Object controller() {
        return new CatalogAssetCountApiController(queryService, transformerRegistry, mock(ParticipantIdMapper.class),
                Json.createBuilderFactory(Map.of()), DSE_NAMESPACE);
    }

    @Override
    protected Object additionalResource() {
        return new JerseyJsonLdInterceptor(jsonLd, typeManager, "test", MANAGEMENT_SCOPE_V4, validatorRegistry, ManagementApiJsonSchema.V4.version());
    }

    private static Map<String, Object> querySpec(String type) {
        return Map.of(CONTEXT, Map.of(VOCAB, EDC_NAMESPACE), TYPE, type, "filterExpression", List.of());
    }

    private RequestSpecification baseRequest() {
        return given()
                .port(port)
                .contentType(JSON)
                .basePath("/v4/catalogs/query/count")
                .when();
    }
}
