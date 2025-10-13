package org.eclipse.edc.test.system;

import io.restassured.specification.RequestSpecification;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.connector.controlplane.test.system.utils.LazySupplier;
import org.eclipse.edc.connector.controlplane.test.system.utils.Participant;
import org.eclipse.edc.connector.controlplane.test.system.utils.PolicyFixtures;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static jakarta.json.Json.createObjectBuilder;
import static org.eclipse.edc.connector.controlplane.test.system.utils.PolicyFixtures.atomicConstraint;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.core.CoreConstants.EONAX_POLICY_NS;
import static org.eclipse.edc.spi.core.CoreConstants.EONAX_POLICY_PREFIX;
import static org.eclipse.edc.test.system.ParticipantConstants.CONTROL_PLANE_DSP_PORT;
import static org.eclipse.edc.test.system.ParticipantConstants.IDENTITY_HUB_DID_PORT;
import static org.eclipse.eonax.iam.policy.PolicyConstants.MEMBERSHIP_CONSTRAINT;

abstract class AbstractParticipant extends AbstractEntity {

    @Override
    protected String did() {
        return "did:web:" + name() + "-identityhub%3A" + IDENTITY_HUB_DID_PORT + ":api:did";
    }

    @Override
    protected String identityHubIdentityUrl() {
        return "http://localhost:80/%s/ih/identity".formatted(name());
    }

    @Override
    protected String vaultUrl() {
        return "http://localhost:80/%s/vault".formatted(name());
    }

    protected String controlPlaneProtocolUrl() {
        return "http://%s-controlplane:%s/api/dsp".formatted(name(), CONTROL_PLANE_DSP_PORT);
    }

    protected String controlPlaneManagementUrl() {
        return "http://localhost:80/%s/cp/management".formatted(name());
    }

    protected String dataPlaneDataUrl() {
        return "http://localhost:80/%s/dp/data".formatted(name());
    }

    protected void createEntry(String assetId, String name, String description, Map<String, Object> dataAddressProps, JsonObject... additionalConstraints) {
        var client = participantClient();
        Map<String, Object> assetProps = Map.of(
                "name", name,
                "contenttype", "application/json",
                "version", "1.0",
                "description", description
        );
        var asset = client.createAsset(assetId, assetProps, dataAddressProps);

        var constraints = new ArrayList<JsonObject>();
        constraints.add(atomicConstraint("%s:%s".formatted(EONAX_POLICY_PREFIX, MEMBERSHIP_CONSTRAINT), "odrl:eq", "active"));
        constraints.addAll(Arrays.asList(additionalConstraints));

        var permissions = constraints.stream().map(this::createPermission).toList();
        var policy = createPolicyDefinition(PolicyFixtures.policy(permissions));
        client.createContractDefinition(asset, UUID.randomUUID().toString(), policy, policy);
    }

    public String createPolicyDefinition(JsonObject policy) {
        var requestBody = createObjectBuilder()
                .add(CONTEXT, createObjectBuilder()
                        .add(VOCAB, EDC_NAMESPACE)
                        .add(EONAX_POLICY_PREFIX, EONAX_POLICY_NS))
                .add(TYPE, "PolicyDefinition")
                .add("policy", policy)
                .build();

        return participantClient().baseManagementRequest()
                .contentType(JSON)
                .body(requestBody)
                .when()
                .post("/v3/policydefinitions")
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
                .post("/v3/secrets")
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
        return ParticipantClient.Builder.newInstance()
                .name(name())
                .id(did())
                .dataPlaneData(dataPlaneDataUrl())
                .controlPlaneManagement(controlPlaneManagementUrl())
                .controlPlaneProtocol(controlPlaneProtocolUrl())
                .build();
    }

    private JsonObject createPermission(JsonValue constraint) {
        return createObjectBuilder()
                .add("action", "use")
                .add("constraint", constraint)
                .build();
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
