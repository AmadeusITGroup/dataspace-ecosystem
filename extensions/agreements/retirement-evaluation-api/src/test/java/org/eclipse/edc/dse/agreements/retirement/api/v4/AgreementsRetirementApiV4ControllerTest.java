/********************************************************************************
 * Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.edc.dse.agreements.retirement.api.v4;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.api.management.schema.ManagementApiJsonSchema;
import org.eclipse.edc.dse.agreements.retirement.spi.service.AgreementsRetirementService;
import org.eclipse.edc.dse.agreements.retirement.spi.types.AgreementConstants;
import org.eclipse.edc.dse.agreements.retirement.spi.types.AgreementsRetirementEntry;
import org.eclipse.edc.dse.common.DseNamespaceConfig;
import org.eclipse.edc.jsonld.JsonLdExtension;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.junit.extensions.TestServiceExtensionContext;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.web.jersey.providers.jsonld.JerseyJsonLdInterceptor;
import org.eclipse.edc.web.jersey.testfixtures.RestControllerTestBase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.api.management.ManagementApi.MANAGEMENT_SCOPE_V4;
import static org.eclipse.edc.dse.agreements.retirement.spi.store.AgreementsRetirementStore.ALREADY_EXISTS_TEMPLATE;
import static org.eclipse.edc.dse.agreements.retirement.spi.store.AgreementsRetirementStore.NOT_FOUND_IN_RETIREMENT_TEMPLATE;
import static org.eclipse.edc.dse.agreements.retirement.spi.types.AgreementsRetirementEntry.AR_ENTRY_AGREEMENT_ID;
import static org.eclipse.edc.dse.agreements.retirement.spi.types.AgreementsRetirementEntry.AR_ENTRY_TYPE;
import static org.eclipse.edc.dse.agreements.retirement.spi.types.AgreementsRetirementEntry.AR_ENTRY_TYPE_TERM;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.query.QuerySpec.EDC_QUERY_SPEC_TYPE;
import static org.eclipse.edc.spi.query.QuerySpec.EDC_QUERY_SPEC_TYPE_TERM;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgreementsRetirementApiV4ControllerTest extends RestControllerTestBase {

    private final AgreementsRetirementService service = mock();
    private final TypeTransformerRegistry transformer = mock();
    private final JsonObjectValidatorRegistry validator = mock();
    private final Monitor monitor = mock();
    private final DseNamespaceConfig testConfig = new DseNamespaceConfig(
            "https://w3id.org/dse/v0.0.1/ns/",
            "dse-policy",
            "https://w3id.org/dse/policy/"
    );
    private final AgreementConstants agreementConstants = new AgreementConstants(testConfig);
    private final JsonLd jsonLd = new JsonLdExtension().createJsonLdService(TestServiceExtensionContext.testServiceExtensionContext());

    @Test
    void should_getAllWithMaxQuerySpec_whenBodyIsEmpty() {
        var agreement1 = "test-agreement-id";
        var agreement2 = "test-agreement-id2";
        var entry1 = createRetirementEntry(agreement1);
        var entry2 = createRetirementEntry(agreement2);

        when(transformer.transform(isA(AgreementsRetirementEntry.class), eq(JsonObject.class)))
                .thenReturn(Result.success(createExpandedRetirementEntry(agreement1)))
                .thenReturn(Result.success(createExpandedRetirementEntry(agreement2)));
        when(service.findAll(any())).thenReturn(ServiceResult.success(List.of(entry1, entry2)));

        baseRequest()
                .contentType(ContentType.JSON)
                .post("/request")
                .then()
                .log().ifError()
                .statusCode(200)
                .body(notNullValue())
                .body("size()", is(2));

        verify(service).findAll(QuerySpec.max());
        verifyNoInteractions(validator);
    }

    @Test
    void should_getAllWithMaxQuerySpec_whenBodyIsNull() {
        when(service.findAll(any())).thenReturn(ServiceResult.success(List.of()));
        var controller = new AgreementsRetirementApiV4Controller(service, transformer, validator, monitor);

        var result = controller.getAllRetiredV4(null);

        assertThat(result).isEmpty();
        verify(service).findAll(QuerySpec.max());
        verifyNoInteractions(validator);
    }

    @Test
    void should_getAgreementUsingFilteredQuerySpec() {
        var agreement1 = "test-agreement-id";
        var entry1 = createRetirementEntry(agreement1);
        var querySpec = QuerySpec.Builder.newInstance().filter(createFilterCriteria(agreement1)).build();

        when(validator.validate(any(), any()))
                .thenReturn(ValidationResult.success());
        when(transformer.transform(isA(JsonObject.class), eq(QuerySpec.class)))
                .thenReturn(Result.success(querySpec));
        when(transformer.transform(isA(AgreementsRetirementEntry.class), eq(JsonObject.class)))
                .thenReturn(Result.success(createExpandedRetirementEntry(agreement1)));
        when(service.findAll(any()))
                .thenReturn(ServiceResult.success(List.of(entry1)));

        baseRequest()
                .contentType(ContentType.JSON)
                .body(createFilterQuerySpecJson(EDC_QUERY_SPEC_TYPE_TERM, agreement1))
                .post("/request")
                .then()
                .statusCode(200)
                .body(notNullValue())
                .body("size()", is(1));

        verify(validator).validate(eq("v4:" + EDC_QUERY_SPEC_TYPE_TERM), any());
        var captor = ArgumentCaptor.forClass(JsonObject.class);
        verify(transformer).transform(captor.capture(), eq(QuerySpec.class));
        assertThat(captor.getValue().getJsonArray(TYPE).getString(0)).isEqualTo(EDC_QUERY_SPEC_TYPE);
        verify(service).findAll(querySpec);
    }

    @Test
    void shouldNot_getAll_whenQuerySpecTypeIsExpanded() {
        baseRequest()
                .contentType(ContentType.JSON)
                .body(createFilterQuerySpecJson(EDC_QUERY_SPEC_TYPE, "test-agreement-id"))
                .post("/request")
                .then()
                .statusCode(400);

        verifyNoInteractions(service, transformer);
    }

    @Test
    void shouldNot_getAll_whenQuerySpecTypeIsMissing() {
        baseRequest()
                .contentType(ContentType.JSON)
                .body("{}")
                .post("/request")
                .then()
                .statusCode(400);

        verifyNoInteractions(service, transformer);
    }

    @Test
    void shouldNot_getAll_whenTypeIsWrong() {
        baseRequest()
                .contentType(ContentType.JSON)
                .body(createFilterQuerySpecJson(AR_ENTRY_TYPE_TERM, "test-agreement-id"))
                .post("/request")
                .then()
                .statusCode(400);

        verifyNoInteractions(service, transformer);
    }

    @Test
    void should_removeAgreement() {
        var agreementId = "test-agreement-id";
        when(service.reactivate(agreementId)).thenReturn(ServiceResult.success());
        baseRequest()
                .delete("/{id}", agreementId)
                .then()
                .statusCode(204);
    }

    @Test
    void shouldNot_removeAgreementWhenNotExists() {
        var agreementId = "test-agreement-id";
        when(service.reactivate(agreementId))
                .thenReturn(ServiceResult.notFound(String.format(NOT_FOUND_IN_RETIREMENT_TEMPLATE, agreementId)));

        baseRequest()
                .delete("/{id}", agreementId)
                .then()
                .log().ifError()
                .statusCode(404);
    }

    @Test
    void should_saveAgreementRetirement() {
        var agreementId = "test-agreement-id";
        when(validator.validate(any(), any()))
                .thenReturn(ValidationResult.success());
        when(transformer.transform(isA(JsonObject.class), eq(AgreementsRetirementEntry.class)))
                .thenReturn(Result.success(createRetirementEntry(agreementId)));
        when(service.retireAgreement(isA(AgreementsRetirementEntry.class)))
                .thenReturn(ServiceResult.success());

        baseRequest()
                .contentType(ContentType.JSON)
                .body(createCompactRetirementEntry(AR_ENTRY_TYPE_TERM, agreementId))
                .post()
                .then()
                .log().ifError()
                .statusCode(204);

        verify(validator).validate(eq("v4:" + AR_ENTRY_TYPE_TERM), any());
        var captor = ArgumentCaptor.forClass(JsonObject.class);
        verify(transformer).transform(captor.capture(), eq(AgreementsRetirementEntry.class));
        assertThat(captor.getValue().getJsonArray(TYPE).getString(0)).isEqualTo(AR_ENTRY_TYPE);
    }

    @Test
    void shouldNot_saveAgreementRetirement_whenTypeIsExpanded() {
        baseRequest()
                .contentType(ContentType.JSON)
                .body(createCompactRetirementEntry(AR_ENTRY_TYPE, "test-agreement-id"))
                .post()
                .then()
                .statusCode(400);

        verifyNoInteractions(service, transformer);
    }

    @Test
    void shouldNot_saveAgreementRetirement_whenTypeIsMissing() {
        baseRequest()
                .contentType(ContentType.JSON)
                .body(Map.of(CONTEXT, Map.of(VOCAB, EDC_NAMESPACE), "agreementId", "test-agreement-id"))
                .post()
                .then()
                .statusCode(400);

        verifyNoInteractions(service, transformer);
    }

    @Test
    void shouldNot_saveAgreementRetirement_whenTypeIsWrong() {
        baseRequest()
                .contentType(ContentType.JSON)
                .body(createCompactRetirementEntry(EDC_QUERY_SPEC_TYPE_TERM, "test-agreement-id"))
                .post()
                .then()
                .statusCode(400);

        verify(service, never()).retireAgreement(any());
        verifyNoInteractions(transformer);
    }

    @Test
    void shouldNot_saveAgreementRetirementWhenExists() {
        var agreementId = "test-agreement-id";
        when(validator.validate(any(), any()))
                .thenReturn(ValidationResult.success());
        when(transformer.transform(isA(JsonObject.class), eq(AgreementsRetirementEntry.class)))
                .thenReturn(Result.success(createRetirementEntry(agreementId)));
        when(service.retireAgreement(isA(AgreementsRetirementEntry.class)))
                .thenReturn(ServiceResult.conflict(String.format(ALREADY_EXISTS_TEMPLATE, agreementId)));

        baseRequest()
                .contentType(ContentType.JSON)
                .body(createCompactRetirementEntry(AR_ENTRY_TYPE_TERM, agreementId))
                .post()
                .then()
                .log().ifError()
                .statusCode(409);
    }

    @Override
    protected Object controller() {
        return new AgreementsRetirementApiV4Controller(service, transformer, validator, monitor);
    }

    @Override
    protected Object additionalResource() {
        // registered as in AgreementsRetirementApiExtension: v4 validation of the compact @type before expansion
        return new JerseyJsonLdInterceptor(jsonLd, typeManager, "test", MANAGEMENT_SCOPE_V4, validator,
                ManagementApiJsonSchema.V4.version());
    }

    private Map<String, Object> createFilterQuerySpecJson(String type, String agreementId) {
        return Map.of(
                CONTEXT, Map.of(VOCAB, EDC_NAMESPACE),
                TYPE, type,
                "filterExpression", List.of(Map.of(
                        TYPE, "Criterion",
                        "operandLeft", AR_ENTRY_AGREEMENT_ID,
                        "operator", "=",
                        "operandRight", agreementId)));
    }

    private Map<String, Object> createCompactRetirementEntry(String type, String agreementId) {
        return Map.of(
                CONTEXT, Map.of(VOCAB, EDC_NAMESPACE, "dse", testConfig.dseNamespace()),
                TYPE, type,
                "agreementId", agreementId,
                "dse:reason", "long-reason");
    }

    private Criterion createFilterCriteria(String agreementId) {
        return Criterion.Builder.newInstance()
                .operandLeft(AR_ENTRY_AGREEMENT_ID)
                .operator("=")
                .operandRight(agreementId)
                .build();
    }

    private AgreementsRetirementEntry createRetirementEntry(String agreementId) {
        return AgreementsRetirementEntry.Builder.newInstance(agreementConstants)
                .withAgreementId(agreementId)
                .withReason("long-reason")
                .build();
    }

    private JsonObject createExpandedRetirementEntry(String agreementId) {
        return Json.createObjectBuilder()
                .add(TYPE, AR_ENTRY_TYPE)
                .add(AR_ENTRY_AGREEMENT_ID, agreementId)
                .add(agreementConstants.getArEntryReason(), "long-reason")
                .add(agreementConstants.getArEntryRetirementDate(), Instant.now().toString())
                .build();
    }

    private RequestSpecification baseRequest() {
        return given()
                .baseUri("http://localhost:" + port)
                .basePath("/v4/contractagreements/retirements")
                .when();
    }


}