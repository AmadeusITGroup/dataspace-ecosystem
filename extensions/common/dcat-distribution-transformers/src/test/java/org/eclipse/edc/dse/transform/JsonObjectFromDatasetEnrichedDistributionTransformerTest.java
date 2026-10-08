/*
 *  Copyright (c) 2026 Amadeus IT Group
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.edc.dse.transform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsonp.JSONPModule;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.catalog.spi.DataService;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.connector.controlplane.catalog.spi.Distribution;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_DISTRIBUTION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCT_FORMAT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_POLICY_ATTRIBUTE;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonObjectFromDatasetEnrichedDistributionTransformerTest {

    private static final String JSON_LD = "json-ld";

    private final TransformerContext context = mock();
    private JsonObjectFromDatasetEnrichedDistributionTransformer transformer;

    @BeforeEach
    void setUp() {
        var typeManager = new JacksonTypeManager();
        // EDC registers a JSON-P aware json-ld mapper at runtime
        typeManager.registerContext(JSON_LD, new ObjectMapper().registerModule(new JSONPModule()));
        transformer = new JsonObjectFromDatasetEnrichedDistributionTransformer(Json.createBuilderFactory(Map.of()), typeManager, JSON_LD);
    }

    @Test
    void transform_withoutCustomDistributions_writesEdcDistributions() {
        var generated = generatedDistribution();
        var dataset = Dataset.Builder.newInstance()
                .id("dataset-id")
                .distributions(List.of(generated))
                .build();
        when(context.transform(eq(generated), eq(JsonObject.class))).thenReturn(formatJson("HttpData-PUSH"));

        var result = transformer.transform(dataset, context);

        assertThat(result.getJsonArray(DCAT_DISTRIBUTION_ATTRIBUTE))
                .extracting(value -> value.asJsonObject().getString(DCT_FORMAT_ATTRIBUTE))
                .containsExactly("HttpData-PUSH");
    }

    @Test
    void transform_mergesEdcAndCustomDistributions() {
        var generated = generatedDistribution();
        var dataset = Dataset.Builder.newInstance()
                .id("dataset-id")
                .distributions(List.of(generated))
                .property(DCAT_DISTRIBUTION_ATTRIBUTE, List.of(Map.of(DCT_FORMAT_ATTRIBUTE, "application/json")))
                .build();
        when(context.transform(eq(generated), eq(JsonObject.class))).thenReturn(formatJson("HttpData-PUSH"));

        var result = transformer.transform(dataset, context);

        assertThat(result.getJsonArray(DCAT_DISTRIBUTION_ATTRIBUTE))
                .extracting(value -> value.asJsonObject().getString(DCT_FORMAT_ATTRIBUTE))
                .containsExactly("HttpData-PUSH", "application/json");
    }

    @Test
    void transform_mergesSingleCustomDistribution() {
        var dataset = Dataset.Builder.newInstance()
                .id("dataset-id")
                .property(DCAT_DISTRIBUTION_ATTRIBUTE, Map.of(DCT_FORMAT_ATTRIBUTE, "application/json"))
                .build();

        var result = transformer.transform(dataset, context);

        assertThat(result.getJsonArray(DCAT_DISTRIBUTION_ATTRIBUTE))
                .singleElement()
                .extracting(value -> value.asJsonObject().getString(DCT_FORMAT_ATTRIBUTE))
                .isEqualTo("application/json");
    }

    @Test
    void transform_preservesCustomDistributionProperties() {
        var customDistribution = Map.of(
                DCT_FORMAT_ATTRIBUTE, "application/custom-json",
                "dcat:mediaType", "application/json",
                "dcat:downloadURL", "https://example.com/custom.json");
        var dataset = Dataset.Builder.newInstance()
                .id("dataset-id")
                .property(DCAT_DISTRIBUTION_ATTRIBUTE, List.of(customDistribution))
                .build();

        var result = transformer.transform(dataset, context);

        var distribution = result.getJsonArray(DCAT_DISTRIBUTION_ATTRIBUTE).getJsonObject(0);
        assertThat(distribution.getString(DCT_FORMAT_ATTRIBUTE)).isEqualTo("application/custom-json");
        assertThat(distribution.getString("dcat:mediaType")).isEqualTo("application/json");
        assertThat(distribution.getString("dcat:downloadURL")).isEqualTo("https://example.com/custom.json");
    }

    @Test
    void transform_doesNotModifyDatasetProperties() {
        var generated = generatedDistribution();
        var customDistributions = List.of(Map.of(DCT_FORMAT_ATTRIBUTE, "application/json"));
        var dataset = Dataset.Builder.newInstance()
                .id("dataset-id")
                .distributions(List.of(generated))
                .property(DCAT_DISTRIBUTION_ATTRIBUTE, customDistributions)
                .build();
        when(context.transform(eq(generated), eq(JsonObject.class))).thenReturn(formatJson("HttpData-PUSH"));

        transformer.transform(dataset, context);

        assertThat(dataset.getProperties().get(DCAT_DISTRIBUTION_ATTRIBUTE)).isSameAs(customDistributions);
    }

    @Test
    void transform_serializesDatasetOffers() {
        var policy = mock(Policy.class);
        var dataset = Dataset.Builder.newInstance()
                .id("dataset-id")
                .offer("offer-id", policy)
                .build();
        when(context.transform(eq(policy), eq(JsonObject.class)))
                .thenReturn(Json.createObjectBuilder().add("@type", "odrl:Offer").build());

        var result = transformer.transform(dataset, context);

        assertThat(result.getJsonArray(ODRL_POLICY_ATTRIBUTE))
                .singleElement()
                .extracting(value -> value.asJsonObject().getString("@id"))
                .isEqualTo("offer-id");
    }

    private static Distribution generatedDistribution() {
        return Distribution.Builder.newInstance()
                .format("HttpData-PUSH")
                .dataService(DataService.Builder.newInstance().id("data-service-id").build())
                .build();
    }

    private static JsonObject formatJson(String format) {
        return Json.createObjectBuilder().add(DCT_FORMAT_ATTRIBUTE, format).build();
    }
}
