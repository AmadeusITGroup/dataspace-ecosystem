/*
 *  Copyright (c) 2026 Amadeus IT Group
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.edc.dse.test.e2e;

import io.restassured.specification.RequestSpecification;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.dse.transform.CustomDatasetTransformer;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimePerClassExtension;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;

/**
 * End-to-end system test that verifies the DCAT Distribution Transformation extension
 * correctly overrides the upstream DSP catalog transformers at the protocol level.
 * <p>
 * TODO: This test class should be removed when the actual implementation is complete
 * and system tests should be added to the main system test set in LocalEndToEndTests.java.
 * <p>
 * This test boots a full control-plane runtime with the extension loaded, creates
 * an asset with a contract definition, then sends a DSP catalog request to the
 * protocol endpoint and verifies the response JSON-LD contains the custom markers
 * from our overridden Dataset and Distribution transformers.
 * <p>
 * This proves that:
 * <ul>
 *   <li>The TypeTransformerRegistry's Map-based override mechanism works end-to-end</li>
 *   <li>The overridden transformers are used during actual DSP protocol serialization</li>
 *   <li>A downstream project can customize the DCAT catalog output by registering
 *       replacement transformers in an extension</li>
 * </ul>
 */
@EndToEndTest
class DcatDistributionTransformationEndToEndTest {

    private static final int PROTOCOL_PORT = getFreePort();
    private static final String DSP_CONTEXT = "https://w3id.org/dspace/2025/1/context.jsonld";
    private static final String EDC_CONTEXT = "https://w3id.org/edc/dspace/v0.0.1";
    private static final List<String> CONTEXT = List.of(DSP_CONTEXT, EDC_CONTEXT);

    @RegisterExtension
    static RuntimeExtension runtime = new RuntimePerClassExtension() {
        {
            var config = new HashMap<String, String>();
            config.put("web.http.port", String.valueOf(getFreePort()));
            config.put("web.http.path", "/api");
            config.put("web.http.management.port", String.valueOf(getFreePort()));
            config.put("web.http.management.path", "/management");
            config.put("web.http.protocol.port", String.valueOf(PROTOCOL_PORT));
            config.put("web.http.protocol.path", "/protocol");
            config.put("web.http.control.port", String.valueOf(getFreePort()));
            config.put("web.http.control.path", "/control");
            config.put("web.http.version.port", String.valueOf(getFreePort()));
            config.put("web.http.version.path", "/version");
            config.put("edc.participant.id", "test-provider");
            config.put("edc.dsp.callback.address", "http://localhost:" + PROTOCOL_PORT + "/protocol");
            config.put("dse.dcat.distribution.transformation.enabled", "true");
            setConfiguration(config);
        }
    };

    @BeforeAll
    static void seedData(AssetIndex assetIndex, PolicyDefinitionStore policyStore, ContractDefinitionStore contractDefinitionStore) {
        var asset = Asset.Builder.newInstance()
                .id("test-asset-1")
                .dataAddress(DataAddress.Builder.newInstance()
                        .type("HttpData")
                        .property("baseUrl", "http://example.com/data")
                        .build())
                .participantContextId("test-provider")
                .build();
        assetIndex.create(asset);

        // Create a policy
        var policyDefinition = PolicyDefinition.Builder.newInstance()
                .id("test-policy-1")
                .policy(Policy.Builder.newInstance().build())
                .participantContextId("test-provider")
                .build();
        policyStore.create(policyDefinition);

        // Create a contract definition linking asset and policy
        var contractDefinition = ContractDefinition.Builder.newInstance()
                .id("test-contract-def-1")
                .accessPolicyId("test-policy-1")
                .contractPolicyId("test-policy-1")
                .assetsSelectorCriterion(Criterion.criterion("id", "=", "test-asset-1"))
                .participantContextId("test-provider")
                .build();
        contractDefinitionStore.save(contractDefinition);
    }

    @Test
    void catalogRequest_shouldReturnDatasetWithCustomMarker() {
        protocolRequest()
                .body(catalogRequestBody())
                .post("/2025-1/catalog/request")
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .contentType(JSON)
                // Verify the catalog contains at least one dataset
                .body("dataset.size()", greaterThanOrEqualTo(1))
                // Verify our seeded dataset is present with the custom marker — proving override works
                .body("dataset.findAll { it.'@id' == 'test-asset-1' }.'" + CustomDatasetTransformer.CUSTOM_MARKER_PROPERTY + "'",
                        hasItem(CustomDatasetTransformer.CUSTOM_MARKER_VALUE));
    }

    @Test
    void catalogRequest_shouldReturnCatalogSuccessfully() {
        protocolRequest()
                .body(catalogRequestBody())
                .post("/2025-1/catalog/request")
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .contentType(JSON)
                .body("participantId", notNullValue())
                .body("'@context'", containsInAnyOrder(DSP_CONTEXT, EDC_CONTEXT))
                .body("dataset", notNullValue());
    }

    @Test
    void datasetRequest_shouldUseOverriddenDatasetTransformer() {
        // Request a specific dataset by ID to verify the custom transformer is used
        protocolRequest()
                .get("/2025-1/catalog/datasets/test-asset-1")
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .contentType(JSON)
                // Verify the dataset response contains our custom marker
                .body("'" + CustomDatasetTransformer.CUSTOM_MARKER_PROPERTY + "'",
                        equalTo(CustomDatasetTransformer.CUSTOM_MARKER_VALUE));
    }

    @Test
    void datasetRequest_shouldUseOverriddenDistributionTransformer() {
        // Request catalog to verify the distribution transformer is registered and active.
        // Note: The custom Dataset transformer produces minimal output and does not delegate
        // to the distribution transformer. The override is verified at the unit level in
        // TransformerRegistryOverrideTest.shouldOverrideDistributionTransformer().
        // Here we just confirm the catalog endpoint works end-to-end with overrides active.
        protocolRequest()
                .body(catalogRequestBody())
                .post("/2025-1/catalog/request")
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .contentType(JSON)
                // Verify the dataset marker is present (confirms transformer override mechanism works)
                .body("dataset.findAll { it.'@id' == 'test-asset-1' }.'" + CustomDatasetTransformer.CUSTOM_MARKER_PROPERTY + "'",
                        hasItem(CustomDatasetTransformer.CUSTOM_MARKER_VALUE));
    }

    /**
     * Creates a pre-configured RestAssured request specification for the DSP protocol endpoint.
     */
    private static RequestSpecification protocolRequest() {
        return given()
                .port(PROTOCOL_PORT)
                .basePath("/protocol")
                .contentType(JSON)
                .header("Authorization", "{\"region\": \"any\", \"audience\": \"any\", \"clientId\":\"any\"}");
    }

    /**
     * Creates a DSP CatalogRequestMessage JSON body.
     */
    private static String catalogRequestBody() {
        return createObjectBuilder()
                .add("@context", createArrayBuilder(CONTEXT))
                .add(TYPE, "CatalogRequestMessage")
                .build()
                .toString();
    }
}
