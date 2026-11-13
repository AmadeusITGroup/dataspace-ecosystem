/*
 *  Copyright (c) 2026 Amadeus IT Group
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus IT Group - initial API and implementation
 *
 */

package org.eclipse.edc.dse.catalog.cache;

import io.restassured.specification.RequestSpecification;
import jakarta.json.Json;
import org.eclipse.edc.api.management.schema.ManagementApiJsonSchema;
import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.catalog.api.query.v4.CatalogsApiV4Controller;
import org.eclipse.edc.catalog.spi.QueryService;
import org.eclipse.edc.dse.catalog.count.CatalogAssetCountApiController;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.jsonld.util.JacksonJsonLd;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.runtime.core.validator.JsonObjectValidatorRegistryImpl;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transform.TypeTransformerRegistryImpl;
import org.eclipse.edc.transform.transformer.edc.to.JsonObjectToCriterionTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonObjectToQuerySpecTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonValueToGenericTypeTransformer;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.jersey.JerseyConfiguration;
import org.eclipse.edc.web.jersey.JerseyRestService;
import org.eclipse.edc.web.jersey.providers.jsonld.JerseyJsonLdInterceptor;
import org.eclipse.edc.web.jersey.providers.jsonld.ObjectMapperProvider;
import org.eclipse.edc.web.jetty.JettyConfiguration;
import org.eclipse.edc.web.jetty.JettyService;
import org.eclipse.edc.web.jetty.PortMappingRegistryImpl;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HTTP-level test of the federated catalog management v4 query endpoints with a sort field: the upstream
 * {@code POST /v4/catalogs/request} ({@link CatalogsApiV4Controller}) and the DSE {@code POST /v4/catalogs/query/count}
 * ({@link CatalogAssetCountApiController}), registered on one management context with the v4 JSON-LD interceptor as
 * their extensions do, the EDC exception mappers of {@link JerseyRestService}, and a validator registry filled by
 * {@link DseFederatedCatalogCacheExtension}.
 */
@ExtendWith(DependencyInjectionExtension.class)
class FederatedCatalogQueryValidationApiTest {

    /**
     * The body of the {@code 400}, as the catalog filter receives it from the federated catalog.
     */
    static final String UNKNOWN_SORT_FIELD_ERROR = "[{\"message\":\"Cannot sort by 'doesNotExist': field does not exist in Dataset\"," +
            "\"type\":\"ValidationFailure\",\"path\":\"sortField\",\"invalidValue\":\"doesNotExist\"}]";
    private static final String MANAGEMENT = "management";

    private final int port = getFreePort();
    private final Monitor monitor = mock();
    private final QueryService queryService = mock();
    private final JsonObjectValidatorRegistry validatorRegistry = new JsonObjectValidatorRegistryImpl();
    private JettyService jetty;

    @BeforeEach
    void setUp(ServiceExtensionContext context, ObjectFactory objectFactory) {
        var jsonLd = new TitaniumJsonLd(monitor);
        context.registerService(JsonObjectValidatorRegistry.class, validatorRegistry);
        context.registerService(JsonLd.class, jsonLd);
        objectFactory.constructInstance(DseFederatedCatalogCacheExtension.class).initialize(context);
        when(queryService.getCatalog(any())).thenReturn(ServiceResult.success(List.of()));

        var typeManager = new JacksonTypeManager();
        typeManager.registerContext(JSON_LD, JacksonJsonLd.createObjectMapper());
        var transformerRegistry = new TypeTransformerRegistryImpl(monitor);
        transformerRegistry.register(new JsonObjectToQuerySpecTransformer());
        transformerRegistry.register(new JsonObjectToCriterionTransformer(CriterionOperatorRegistryImpl.ofDefaults()));
        transformerRegistry.register(new JsonValueToGenericTypeTransformer(typeManager, JSON_LD));

        var portMappings = new PortMappingRegistryImpl();
        portMappings.register(new PortMapping(MANAGEMENT, port, "/api/management"));
        jetty = new JettyService(new JettyConfiguration(null, null), monitor, portMappings);
        var webService = new JerseyRestService(jetty, typeManager, mock(JerseyConfiguration.class), monitor);
        webService.registerResource(MANAGEMENT, new ObjectMapperProvider(typeManager, JSON_LD));
        // as FederatedCatalogApiExtension (upstream) and CatalogAssetCountApiExtension (DSE) register them
        webService.registerResource(MANAGEMENT, new CatalogsApiV4Controller(queryService, transformerRegistry));
        webService.registerDynamicResource(MANAGEMENT, CatalogsApiV4Controller.class, v4Interceptor(jsonLd, typeManager));
        webService.registerResource(MANAGEMENT, new CatalogAssetCountApiController(queryService, transformerRegistry,
                mock(ParticipantIdMapper.class), Json.createBuilderFactory(Map.of()), "https://w3id.org/dse/v0.0.1/ns/"));
        webService.registerDynamicResource(MANAGEMENT, CatalogAssetCountApiController.class, v4Interceptor(jsonLd, typeManager));
        jetty.start();
        webService.start();
    }

    @AfterEach
    void tearDown() {
        jetty.shutdown();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/v4/catalogs/request", "/v4/catalogs/query/count"})
    void unknownSortField_isBadRequestWithTheValidationFailure(String path) {
        var body = request(path)
                .body(querySpec("doesNotExist"))
                .post()
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("size()", is(1))
                .body("[0].type", is("ValidationFailure"))
                .body("[0].message", is("Cannot sort by 'doesNotExist': field does not exist in Dataset"))
                .body("[0].path", is("sortField"))
                .body("[0].invalidValue", is("doesNotExist"))
                .extract().body().asString();

        assertThat(body).isEqualTo(UNKNOWN_SORT_FIELD_ERROR);
        verifyNoInteractions(queryService);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "/v4/catalogs/request, distributions.doesNotExist",
            "/v4/catalogs/query/count, distributions.doesNotExist",
            "/v4/catalogs/request, properties[0]",
            "/v4/catalogs/query/count, properties[0]"
    })
    void unknownNestedSortField_isBadRequest(String path, String sortField) {
        request(path)
                .body(querySpec(sortField))
                .post()
                .then()
                .statusCode(400)
                .body("[0].type", is("ValidationFailure"))
                .body("[0].message", is("Cannot sort by '%s': field does not exist in Dataset".formatted(sortField)));

        verifyNoInteractions(queryService);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/v4/catalogs/request", "/v4/catalogs/query/count"})
    void unknownSortField_asPrefixedTermOrFullIri_isBadRequest(String path) {
        request(path)
                .body(Map.of(CONTEXT, Map.of("edc", EDC_NAMESPACE), TYPE, "QuerySpec", "edc:sortField", "doesNotExist"))
                .post()
                .then()
                .statusCode(400)
                .body("[0].message", is("Cannot sort by 'doesNotExist': field does not exist in Dataset"));
        request(path)
                .body(Map.of(TYPE, "QuerySpec", EDC_NAMESPACE + "sortField", "doesNotExist"))
                .post()
                .then()
                .statusCode(400)
                .body("[0].message", is("Cannot sort by 'doesNotExist': field does not exist in Dataset"));

        verifyNoInteractions(queryService);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/v4/catalogs/request", "/v4/catalogs/query/count"})
    void unknownSortFieldWithDatasetsPrefix_isBadRequest(String path) {
        request(path)
                .body(querySpec("datasets.doesNotExist"))
                .post()
                .then()
                .statusCode(400)
                .body("[0].message", is("Cannot sort by 'doesNotExist': field does not exist in Dataset"))
                .body("[0].invalidValue", is("datasets.doesNotExist"));

        verifyNoInteractions(queryService);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/v4/catalogs/request", "/v4/catalogs/query/count"})
    void sortFieldOfTheDatasets_isAccepted(String path) {
        request(path)
                .body(querySpec("datasets.properties.'http://purl.org/dc/terms/title'"))
                .post()
                .then()
                .statusCode(200)
                .body("size()", is(0));

        verify(queryService).getCatalog(any());
    }

    @Test
    void sortFieldOfTheDatasets_reachesTheQueryService() {
        request("/v4/catalogs/request").body(querySpec("properties.'http://purl.org/dc/terms/title'")).post().then().statusCode(200);

        var captor = ArgumentCaptor.forClass(QuerySpec.class);
        verify(queryService).getCatalog(captor.capture());
        assertThat(captor.getValue().getSortField()).isEqualTo("properties.'http://purl.org/dc/terms/title'");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/v4/catalogs/request", "/v4/catalogs/query/count"})
    void withoutSortField_isAccepted(String path) {
        request(path)
                .body(Map.of(CONTEXT, Map.of(VOCAB, EDC_NAMESPACE), TYPE, "QuerySpec", "limit", 5))
                .post()
                .then()
                .statusCode(200);

        verify(queryService).getCatalog(any());
    }

    private JerseyJsonLdInterceptor v4Interceptor(TitaniumJsonLd jsonLd, JacksonTypeManager typeManager) {
        return new JerseyJsonLdInterceptor(jsonLd, typeManager, JSON_LD, MANAGEMENT_SCOPE_V4, validatorRegistry, ManagementApiJsonSchema.V4.version());
    }

    private static Map<String, Object> querySpec(String sortField) {
        return Map.of(CONTEXT, Map.of(VOCAB, EDC_NAMESPACE), TYPE, "QuerySpec", "sortField", sortField, "sortOrder", "ASC");
    }

    private RequestSpecification request(String path) {
        return given()
                .port(port)
                .contentType(JSON)
                .basePath("/api/management" + path)
                .when();
    }
}
