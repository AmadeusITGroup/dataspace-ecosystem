package org.eclipse.edc.test.system;

import io.restassured.specification.RequestSpecification;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.junit.utils.LazySupplier;
import org.eclipse.edc.connector.controlplane.test.system.utils.Participant;
import org.eclipse.edc.connector.controlplane.test.system.utils.PolicyFixtures;
import org.eclipse.edc.jsonld.JsonLdExtension;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.junit.extensions.TestServiceExtensionContext;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static jakarta.json.Json.createObjectBuilder;
import static org.eclipse.dse.iam.policy.PolicyConstants.MEMBERSHIP_CONSTRAINT;
import static org.eclipse.edc.connector.controlplane.test.system.utils.PolicyFixtures.atomicConstraint;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.core.CoreConstants.DSE_POLICY_NS;
import static org.eclipse.edc.spi.core.CoreConstants.DSE_POLICY_PREFIX;
import static org.eclipse.edc.test.system.ParticipantConstants.CLUSTER_HOSTNAME;
import static org.eclipse.edc.test.system.ParticipantConstants.CONTROL_PLANE_DSP_PORT;
import static org.eclipse.edc.test.system.ParticipantConstants.SCHEME;
import static org.eclipse.edc.protocol.dsp.spi.type.Dsp2025Constants.DATASPACE_PROTOCOL_HTTP_V_2025_1;
import static org.eclipse.edc.protocol.dsp.spi.type.Dsp2025Constants.V_2025_1_PATH;

abstract class AbstractParticipant extends AbstractEntity {

    private JsonLd jsonLd;
    private ParticipantClient cachedParticipantClient;

    @Override
    protected String did() {
        return "did:web:" + name() + "-identityhub:api:did";
    }

    @Override
    protected String identityHubIdentityUrl() {
        return "%s://%s/%s/ih/identity".formatted(SCHEME, CLUSTER_HOSTNAME, name());
    }

    @Override
    protected String vaultUrl() {
        return "%s://%s/%s/vault".formatted(SCHEME, CLUSTER_HOSTNAME, name());
    }

    private String controlPlaneProtocolBaseUrl() {
        return "%s://%s-controlplane:%s/api/dsp".formatted(SCHEME, name(), CONTROL_PLANE_DSP_PORT);
    }

    protected String controlPlaneProtocolUrl() {
        return controlPlaneProtocolBaseUrl() + V_2025_1_PATH;
    }

    protected String controlPlaneManagementUrl() {
        return "%s://%s/%s/cp/management".formatted(SCHEME, CLUSTER_HOSTNAME, name());
    }

    protected String dataPlaneDataUrl() {
        return "%s://%s/%s/dp/data".formatted(SCHEME, CLUSTER_HOSTNAME, name());
    }

    protected String controlPlaneCatalogFilterUrl() {
        return "%s://%s/%s/cp/management/v4/catalogs/request".formatted(SCHEME, CLUSTER_HOSTNAME, name());
    }

    protected void createEntry(String assetId, String name, String description, Map<String, Object> dataAddressProps, JsonObject... additionalConstraints) {
        // Build the asset body directly (rather than via the EDC client helper) so it can carry a
        // whitelisted dcat:theme category - the control-plane now rejects an asset created without
        // one (PSMP-9102). A single default category keeps every basic test asset valid without
        // touching each call site.
        var body = createObjectBuilder()
                .add(CONTEXT, createObjectBuilder()
                        .add(VOCAB, EDC_NAMESPACE)
                        .add(DSE_POLICY_PREFIX, DSE_POLICY_NS)
                        .add("dcat", DCAT_NS))
                .add(ID, assetId)
                .add(TYPE, "Asset")
                .add("properties", createObjectBuilder()
                        .add("name", name)
                        .add("contenttype", "application/json")
                        .add("version", "1.0")
                        .add("description", description)
                        .add("dcat:theme", defaultThemeArray()))
                .add("dataAddress", buildDataAddress(dataAddressProps))
                .build();

        var assetId_ = participantClient().baseManagementRequest()
                .contentType(JSON)
                .body(body)
                .when()
                .post("/assets")
                .then()
                .log().ifError()
                .statusCode(200)
                .contentType(JSON)
                .extract().jsonPath().getString(ID);

        var constraints = new ArrayList<JsonObject>();
        constraints.add(atomicConstraint("%s:%s".formatted(DSE_POLICY_PREFIX, MEMBERSHIP_CONSTRAINT), "odrl:eq", "active"));
        constraints.addAll(Arrays.asList(additionalConstraints));

        var permissions = constraints.stream().map(this::createPermission).toList();
        var policy = createPolicyDefinition(PolicyFixtures.policy(permissions));
        participantClient().createContractDefinition(assetId_, UUID.randomUUID().toString(), policy, policy);
    }

    /**
     * Creates an asset with a custom {@code @context} (e.g. to add {@code dct:} or other prefixes)
     * and arbitrary properties including nested Maps.
     *
     * @param assetId            asset identifier
     * @param extraContext       additional entries for the JSON-LD {@code @context} (e.g. {@code Map.of("dct", "http://purl.org/dc/terms/")})
     * @param properties         flat or nested asset properties; nested {@link Map} values are serialized as JSON objects
     * @param dataAddressProps   data address configuration
     * @param additionalConstraints optional additional ODRL constraints
     */
    protected void createEntryWithCustomContext(String assetId,
                                                Map<String, String> extraContext,
                                                Map<String, Object> properties,
                                                Map<String, Object> dataAddressProps,
                                                JsonObject... additionalConstraints) {
        var contextBuilder = createObjectBuilder()
                .add(VOCAB, EDC_NAMESPACE)
                .add(DSE_POLICY_PREFIX, DSE_POLICY_NS);
        extraContext.forEach(contextBuilder::add);
        if (!extraContext.containsKey("dcat")) {
            contextBuilder.add("dcat", DCAT_NS);
        }

        var propsBuilder = createObjectBuilder();
        var hasTheme = properties.containsKey("dcat:theme");
        properties.forEach((k, v) -> {
            if ("dcat:theme".equals(k) && v instanceof Map<?, ?> nested) {
                // Keep the caller's theme metadata but add a whitelisted @id so the asset passes
                // the category validator (PSMP-9102) while the existing dcat:theme.dct:identifier
                // filter paths still resolve.
                @SuppressWarnings("unchecked")
                var themeMap = (Map<String, Object>) nested;
                propsBuilder.add(k, withThemeId(themeMap));
            } else if (v instanceof Map<?, ?> nested) {
                // Safe: callers of this test helper always provide Map<String, Object> for nested values
                @SuppressWarnings("unchecked")
                var nestedMap = (Map<String, Object>) nested;
                propsBuilder.add(k, buildJsonObject(nestedMap));
            } else {
                propsBuilder.add(k, v.toString());
            }
        });
        if (!hasTheme) {
            // No category supplied by the caller: add the default whitelisted one.
            propsBuilder.add("dcat:theme", defaultThemeArray());
        }

        var body = createObjectBuilder()
                .add(CONTEXT, contextBuilder)
                .add(ID, assetId)
                .add(TYPE, "Asset")
                .add("properties", propsBuilder)
                .add("dataAddress", buildDataAddress(dataAddressProps))
                .build();

        var assetId_ = participantClient().baseManagementRequest()
                .contentType(JSON)
                .body(body)
                .when()
                .post("/assets")
                .then()
                .log().ifError()
                .statusCode(200)
                .contentType(JSON)
                .extract().jsonPath().getString(ID);

        var constraints = new ArrayList<JsonObject>();
        constraints.add(atomicConstraint("%s:%s".formatted(DSE_POLICY_PREFIX, MEMBERSHIP_CONSTRAINT), "odrl:eq", "active"));
        constraints.addAll(Arrays.asList(additionalConstraints));

        var permissions = constraints.stream().map(this::createPermission).toList();
        var policy = createPolicyDefinition(PolicyFixtures.policy(permissions));
        participantClient().createContractDefinition(assetId_, UUID.randomUUID().toString(), policy, policy);
    }

@SuppressWarnings("unchecked")
    private static jakarta.json.JsonObject buildJsonObject(Map<String, Object> map) {
        var builder = createObjectBuilder();
        map.forEach((k, v) -> {
            if (v instanceof Map<?, ?> nested) {
                // Safe: callers of this test helper always provide Map<String, Object> for nested values
                builder.add(k, buildJsonObject((Map<String, Object>) nested));
            } else {
                builder.add(k, v.toString());
            }
        });
        return builder.build();
    }

    /**
     * DCAT namespace IRI, used for the {@code dcat:theme} category property.
     */
    protected static final String DCAT_NS = "http://www.w3.org/ns/dcat#";

    /**
     * The category every test asset carries by default. System-tests deploy the committed example
     * list ({@code system-tests/category-whitelist.example.json}), on which this id is whitelisted,
     * so the control plane's category validator (PSMP-9102) accepts the asset.
     */
    protected static final String DEFAULT_WHITELISTED_THEME_ID =
            "urn:amadeus:platform:content-management:taxonomy:category:Order";

    /**
     * A {@code dcat:theme} array holding one default whitelisted category.
     */
    private static jakarta.json.JsonArray defaultThemeArray() {
        return jakarta.json.Json.createArrayBuilder()
                .add(createObjectBuilder().add("@id", DEFAULT_WHITELISTED_THEME_ID))
                .build();
    }

    /**
     * Serializes a caller-supplied theme map and adds a whitelisted {@code @id} to it (unless the
     * caller already set one), so the theme metadata is preserved AND the category is valid.
     */
    private static jakarta.json.JsonObject withThemeId(Map<String, Object> themeMap) {
        var builder = createObjectBuilder();
        themeMap.forEach((k, v) -> {
            if (v instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                var nestedMap = (Map<String, Object>) nested;
                builder.add(k, buildJsonObject(nestedMap));
            } else {
                builder.add(k, v.toString());
            }
        });
        if (!themeMap.containsKey("@id")) {
            builder.add("@id", DEFAULT_WHITELISTED_THEME_ID);
        }
        return builder.build();
    }

    private static jakarta.json.JsonObject buildDataAddress(Map<String, Object> dataAddressProps) {
        var builder = createObjectBuilder().add(TYPE, "DataAddress");
        // Carry every data-address property the caller supplied (type, baseUrl, proxyQueryParams,
        // oauth2:*, Azure storage props, ...), defaulting type/baseUrl when absent.
        if (!dataAddressProps.containsKey("type")) {
            builder.add("type", "HttpData");
        }
        if (!dataAddressProps.containsKey("baseUrl")) {
            builder.add("baseUrl", "http://example.com");
        }
        dataAddressProps.forEach((k, v) -> builder.add(k, v.toString()));
        return builder.build();
    }

    public String createPolicyDefinition(JsonObject policy) {
        var requestBody = createObjectBuilder()
                .add(CONTEXT, createObjectBuilder()
                        .add(VOCAB, EDC_NAMESPACE)
                        .add(DSE_POLICY_PREFIX, DSE_POLICY_NS))
                .add(TYPE, "PolicyDefinition")
                .add("policy", policy)
                .build();

        return participantClient().baseManagementRequest()
                .contentType(JSON)
                .body(requestBody)
                .when()
                .post("/policydefinitions")
                .then()
                .log().ifError()
                .statusCode(200)
                .contentType(JSON)
                .extract().jsonPath().getString(ID);
    }

    /**
     * Creates an asset from a fully custom JSON-LD body (e.g. one with deeply nested objects
     * and arrays that {@link #createEntryWithCustomContext} cannot represent).
     *
     * @param assetBody the complete {@code Asset} JSON-LD document, including {@code @context},
     *                  {@code @id}, {@code properties} and {@code dataAddress}
     * @return the created asset's id
     */
    protected String createRawAsset(JsonObject assetBody) {
        return participantClient().baseManagementRequest()
                .contentType(JSON)
                .body(assetBody)
                .when()
                .post("/assets")
                .then()
                .log().ifError()
                .statusCode(200)
                .contentType(JSON)
                .extract().jsonPath().getString(ID);
    }

    protected void createRawAssetWithDefaultContract(JsonObject assetBody) {
        var assetId = createRawAsset(assetBody);
        var constraint = atomicConstraint("%s:%s".formatted(DSE_POLICY_PREFIX, MEMBERSHIP_CONSTRAINT), "odrl:eq", "active");
        var policy = createPolicyDefinition(PolicyFixtures.policy(List.of(createPermission(constraint))));
        participantClient().createContractDefinition(assetId, UUID.randomUUID().toString(), policy, policy);
    }

    /**
     * Creates a contract definition whose {@code assetsSelector} is a single string-based
     * {@link org.eclipse.edc.spi.query.Criterion} (e.g. a nested asset property path compared against a
     * string value), instead of the default {@code id = assetId} selector used by
     * {@link #createEntry}/{@link #createEntryWithCustomContext}. All three criterion parts
     * ({@code operandLeft}, {@code operator}, {@code operandRight}) are sent as JSON strings, so this
     * helper cannot represent non-string right operands (numbers, booleans, collections) or
     * multi-criterion selectors.
     *
     * @param contractDefinitionId the contract definition id
     * @param accessPolicyId       the access policy id
     * @param contractPolicyId     the contract policy id
     * @param operandLeft          the assetsSelector criterion's left operand (e.g. a nested property path)
     * @param operator             the assetsSelector criterion's operator (e.g. {@code "="})
     * @param operandRight         the assetsSelector criterion's right operand, sent as a JSON string
     * @return the created contract definition's id
     */
    /**
     * Creates a contract definition with one or more criteria in the asset selector.
     * Supports filtering by {@link org.eclipse.edc.spi.query.Criterion} (e.g. a nested asset property path
     * compared against a string value), instead of the default {@code id = assetId} selector used by
     * {@link #createEntry}/{@link #createEntryWithCustomContext}.
     *
     * Usage for single criterion (legacy):
     * {@code createContractDefinitionWithSelector(defId, policyId, policyId, "prop", "=", "value")}
     *
     * Usage for multiple criteria:
     * {@code createContractDefinitionWithSelector(defId, policyId, policyId, 
     *        new String[]{"prop1", "=", "val1"}, new String[]{"prop2", "=", "val2"})}
     *
     * @param contractDefinitionId the contract definition id
     * @param accessPolicyId       the access policy id
     * @param contractPolicyId     the contract policy id
     * @param criteriaOrOperands   either three strings (operandLeft, operator, operandRight) for single criterion,
     *                             or arrays of three strings for multiple criteria
     * @return the created contract definition's id
     */
    protected String createContractDefinitionWithSelector(String contractDefinitionId,
                                                          String accessPolicyId,
                                                          String contractPolicyId,
                                                          Object... criteriaOrOperands) {
        // Build criteria list
        var criteriaList = new ArrayList<String[]>();
        
        if (criteriaOrOperands.length == 3 && 
            criteriaOrOperands[0] instanceof String && 
            criteriaOrOperands[1] instanceof String && 
            criteriaOrOperands[2] instanceof String) {
            // Single criterion: three string arguments
            criteriaList.add(new String[]{
                (String) criteriaOrOperands[0],
                (String) criteriaOrOperands[1],
                (String) criteriaOrOperands[2]
            });
        } else {
            // Multiple criteria: array arguments
            for (var criterion : criteriaOrOperands) {
                if (criterion instanceof String[]) {
                    criteriaList.add((String[]) criterion);
                }
            }
        }

        // Build assetsSelector JSON-LD structure
        var selectorBuilder = jakarta.json.Json.createArrayBuilder();
        for (var criterion : criteriaList) {
            selectorBuilder.add(createObjectBuilder()
                    .add(TYPE, "Criterion")
                    .add(EDC_NAMESPACE + "operandLeft", criterion[0])
                    .add(EDC_NAMESPACE + "operator", criterion[1])
                    .add(EDC_NAMESPACE + "operandRight", criterion[2]));
        }

        var requestBody = createObjectBuilder()
                .add(CONTEXT, createObjectBuilder().add(VOCAB, EDC_NAMESPACE))
                .add(ID, contractDefinitionId)
                .add(TYPE, "ContractDefinition")
                .add(EDC_NAMESPACE + "accessPolicyId", accessPolicyId)
                .add(EDC_NAMESPACE + "contractPolicyId", contractPolicyId)
                .add(EDC_NAMESPACE + "assetsSelector", selectorBuilder)
                .build();

        return participantClient().baseManagementRequest()
                .contentType(JSON)
                .body(requestBody)
                .when()
                .post("/contractdefinitions")
                .then()
                .log().ifError()
                .statusCode(200)
                .contentType(JSON)
                .extract().jsonPath().getString(ID);
    }

    protected void createSecret(String key, String value) {
        var requestBodyBuilder = createObjectBuilder()
                .add(CONTEXT, createObjectBuilder().add(VOCAB, EDC_NAMESPACE))
                .add(ID, key)
                .add(TYPE, "Secret")
                .add("value", value);

        var requestBody = requestBodyBuilder.build();

        participantClient().baseManagementRequest()
                .contentType(JSON)
                .body(requestBody)
                .when()
                .post("/secrets")
                .then()
                .log().ifError()
                .statusCode(200);
    }

    public <T> T queryData(String contractId, Map<String, String> queryParams, int statusCode, Class<T> clazz) {
        return participantClient().baseDataRequest()
                .queryParams(queryParams)
                .headers(Map.of("Authorization", contractId, "Contract-Id", contractId))
                .when()
                .get()
                .then()
                .statusCode(statusCode)
                .extract()
                .body()
                .as(clazz);
    }

    public ParticipantClient participantClient() {
        if (cachedParticipantClient == null) {
            cachedParticipantClient = ParticipantClient.Builder.newInstance()
                    .name(name())
                    .id(did())
                    // versionPath is empty because controlPlaneProtocolUrl() already includes V_2025_1_PATH;
                    // Participant.getProtocolUrl() appends versionPath to the configured protocol URL.
                    .protocol(DATASPACE_PROTOCOL_HTTP_V_2025_1, "")
                    .managementVersionBasePath("/v4")
                    .jsonLd(jsonLd())
                    .dataPlaneData(dataPlaneDataUrl())
                    .controlPlaneManagement(controlPlaneManagementUrl())
                    .controlPlaneProtocol(controlPlaneProtocolUrl())
                    .build();
        }
        return cachedParticipantClient;
    }

    private JsonLd jsonLd() {
        if (jsonLd == null) {
            jsonLd = new JsonLdExtension().createJsonLdService(TestServiceExtensionContext.testServiceExtensionContext());
        }
        return jsonLd;
    }

    private JsonObject createPermission(JsonValue constraint) {
        return createObjectBuilder()
                .add("action", "use")
                .add("constraint", constraint)
                .build();
    }

    public void finishDataTransfer(String transferId) {
        participantClient().terminateTransfer(transferId);
    }

    public static class ParticipantClient extends Participant {

        protected LazySupplier<URI> dataPlaneData;

        public RequestSpecification baseDataRequest() {
            return given().baseUri(this.dataPlaneData.get().toString());
        }

        public static class Builder extends Participant.Builder<ParticipantClient, Builder> {

            protected Builder(ParticipantClient client) {
                super(client);
            }

            public Builder controlPlaneManagement(String controlPlaneManagement) {
                participant.controlPlaneManagement = new LazySupplier<>(() -> URI.create(controlPlaneManagement));
                return self();
            }

            public Builder controlPlaneProtocol(String controlPlaneProtocol) {
                participant.controlPlaneProtocol = new LazySupplier<>(() -> URI.create(controlPlaneProtocol));
                return self();
            }

            public Builder dataPlaneData(String dataPlaneData) {
                participant.dataPlaneData = new LazySupplier<>(() -> URI.create(dataPlaneData));
                return self();
            }

            public ParticipantClient build() {
                super.build();
                Objects.requireNonNull(participant.dataPlaneData, "dataPlaneData");
                return participant;
            }

            protected Builder self() {
                return this;
            }

            public static ParticipantClient.Builder newInstance() {
                return new Builder(new ParticipantClient());
            }
        }
    }


}
