package org.eclipse.edc.dse.agreements.retirement.api.v4;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.api.management.schema.ManagementApiJsonSchema;
import org.eclipse.edc.dse.agreements.retirement.api.transform.JsonObjectFromAgreementRetirementTransformer;
import org.eclipse.edc.dse.agreements.retirement.spi.service.AgreementsRetirementService;
import org.eclipse.edc.dse.agreements.retirement.spi.types.AgreementConstants;
import org.eclipse.edc.dse.agreements.retirement.spi.types.AgreementsRetirementEntry;
import org.eclipse.edc.dse.common.DseNamespaceConfig;
import org.eclipse.edc.jsonld.JsonLdExtension;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.junit.extensions.TestServiceExtensionContext;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.jersey.providers.jsonld.JerseyJsonLdInterceptor;
import org.eclipse.edc.web.jersey.testfixtures.RestControllerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.api.management.ManagementApi.MANAGEMENT_SCOPE;
import static org.eclipse.edc.api.management.ManagementApi.MANAGEMENT_SCOPE_V4;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_PREFIX;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_PREFIX;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Keys of the control-plane retirement response, with the real {@link JsonObjectFromAgreementRetirementTransformer} and
 * the JSON-LD scopes as the control-plane runtime configures them: the upstream management API registrations, the DSE
 * {@code skos} / {@code prov} / {@code adms} prefixes ({@code DseJsonLdNamespacesExtension}) and {@code dse-policy} on
 * the default scope ({@code PolicyEvaluationExtension}). The control plane registers no {@code dse} prefix (only the
 * federated catalog and the catalog filter do), so the reason and the retirement date, which the transformer writes in
 * the DSE namespace ({@link AgreementConstants}), come back as full IRIs.
 */
class AgreementsRetirementApiV4ResponseTest extends RestControllerTestBase {

    private static final String DSE_NAMESPACE = "https://w3id.org/dse/v0.0.1/ns/";
    private static final String REASON_IRI = DSE_NAMESPACE + "reason";
    private static final String RETIREMENT_DATE_IRI = DSE_NAMESPACE + "agreementRetirementDate";
    private static final Map<String, String> DSE_EVENT_NAMESPACES = Map.of(
            "skos", "http://www.w3.org/2004/02/skos/core#",
            "prov", "http://www.w3.org/ns/prov#",
            "adms", "http://www.w3.org/ns/adms#");

    private final AgreementConstants agreementConstants =
            new AgreementConstants(new DseNamespaceConfig(DSE_NAMESPACE, "dse-policy", "https://w3id.org/dse/policy/"));
    private final JsonObjectFromAgreementRetirementTransformer fromTransformer =
            new JsonObjectFromAgreementRetirementTransformer(Json.createBuilderFactory(Map.of()), agreementConstants);
    private final AgreementsRetirementService service = mock();
    private final TypeTransformerRegistry transformerRegistry = mock();
    private final JsonLd jsonLd = new JsonLdExtension().createJsonLdService(TestServiceExtensionContext.testServiceExtensionContext());
    private final AgreementsRetirementEntry entry = AgreementsRetirementEntry.Builder.newInstance(agreementConstants)
            .withAgreementId("agreement-1")
            .withReason("physical counterpart retired")
            .withAgreementRetirementDate(1_700_000_000L)
            .build();

    @BeforeEach
    void setUp() {
        // upstream ManagementApiConfigurationExtension: v3 without the management context (default), v4 management/v2
        jsonLd.registerNamespace(VOCAB, EDC_NAMESPACE, MANAGEMENT_SCOPE);
        jsonLd.registerNamespace(EDC_PREFIX, EDC_NAMESPACE, MANAGEMENT_SCOPE);
        jsonLd.registerNamespace(ODRL_PREFIX, ODRL_SCHEMA, MANAGEMENT_SCOPE);
        jsonLd.registerContext(EDC_CONNECTOR_MANAGEMENT_CONTEXT_V2, MANAGEMENT_SCOPE_V4);
        // DSE control-plane extensions
        List.of(MANAGEMENT_SCOPE, MANAGEMENT_SCOPE_V4).forEach(scope ->
                DSE_EVENT_NAMESPACES.forEach((prefix, namespace) -> jsonLd.registerNamespace(prefix, namespace, scope)));
        jsonLd.registerNamespace("dse-policy", "https://w3id.org/dse/policy/");

        when(transformerRegistry.transform(isA(AgreementsRetirementEntry.class), eq(JsonObject.class)))
                .thenAnswer(invocation -> Result.success(fromTransformer.transform(invocation.getArgument(0), mock(TransformerContext.class))));
        when(service.findAll(any())).thenReturn(ServiceResult.success(List.of(entry)));
    }

    @Test
    void retirementsV4_reasonAndRetirementDateAreFullDseIris() {
        var body = given()
                .port(port)
                .contentType(JSON)
                .post("/v4/contractagreements/retirements/request")
                .then()
                .log().body()
                .statusCode(200)
                .extract().body().asString();

        var retirement = Json.createReader(new StringReader(body)).readArray().getJsonObject(0);
        assertThat(retirement.getString(REASON_IRI)).isEqualTo("physical counterpart retired");
        assertThat(retirement.getJsonNumber(RETIREMENT_DATE_IRI).longValue()).isEqualTo(1_700_000_000L);
        assertThat(retirement.getString("edc:agreementId")).isEqualTo("agreement-1");
        assertThat(retirement).doesNotContainKeys("dse:reason", "edc:reason", "reason", "dse:agreementRetirementDate");
    }

    @Test
    void retirementsV3_reasonWasAlreadyFullDseIri() {
        // management v3 (the API on master): same transformer, v3 scope
        var expanded = fromTransformer.transform(entry, mock(TransformerContext.class));

        var compacted = jsonLd.compact(expanded, MANAGEMENT_SCOPE).orElseThrow(f -> new AssertionError(f.getFailureDetail()));

        assertThat(compacted.getString(REASON_IRI)).isEqualTo("physical counterpart retired");
        assertThat(compacted.getString("agreementId")).isEqualTo("agreement-1");
        assertThat(compacted).doesNotContainKeys("dse:reason", "edc:reason", "reason");
    }

    @Override
    protected Object controller() {
        return new AgreementsRetirementApiV4Controller(service, transformerRegistry, mock(JsonObjectValidatorRegistry.class), monitor);
    }

    @Override
    protected Object additionalResource() {
        // registered as in AgreementsRetirementApiExtension
        return new JerseyJsonLdInterceptor(jsonLd, typeManager, "test", MANAGEMENT_SCOPE_V4, mock(JsonObjectValidatorRegistry.class),
                ManagementApiJsonSchema.V4.version());
    }
}
