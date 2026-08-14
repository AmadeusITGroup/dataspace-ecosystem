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

import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.catalog.spi.DataService;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.connector.controlplane.catalog.spi.Distribution;
import org.eclipse.edc.junit.extensions.RuntimePerClassExtension;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.dse.transform.CustomDatasetTransformer.CUSTOM_MARKER_PROPERTY;
import static org.eclipse.edc.dse.transform.CustomDatasetTransformer.CUSTOM_MARKER_VALUE;
import static org.eclipse.edc.util.io.Ports.getFreePort;

/**
 * Integration test that verifies the TypeTransformerRegistry allows overriding
 * transformers registered by upstream EDC extensions.
 * <p>
 * The test boots a full control-plane runtime which loads both the upstream
 * DSP catalog transform extension (registering {@code JsonObjectFromDatasetTransformer}
 * and {@code JsonObjectFromDistributionTransformer}) and our custom
 * {@link DcatDistributionTransformationExtension} (registering {@link CustomDatasetTransformer}
 * and {@link CustomDistributionTransformer}).
 * <p>
 * The fix in TypeTransformerRegistry (Map-based instead of ArrayList) ensures
 * that the last registration for a given input/output type pair wins, without
 * causing ambiguity errors.
 */
class TransformerRegistryOverrideTest {

    private static final int PROTOCOL_PORT = getFreePort();

    @RegisterExtension
    static RuntimePerClassExtension runtime = new RuntimePerClassExtension() {
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
            config.put("edc.participant.id", "test-participant");
            config.put("edc.dsp.callback.address", "http://localhost:" + PROTOCOL_PORT + "/protocol");
            config.put("dse.dcat.distribution.transformation.enabled", "true");
            setConfiguration(config);
        }
    };

    @Test
    void shouldOverrideDatasetTransformer(TypeTransformerRegistry registry) {
        // Get the DSP v2025/1 context registry where transformers are registered
        var dspRegistry = registry.forContext("dsp-api:2025-1");

        // Create a minimal Dataset
        var dataset = Dataset.Builder.newInstance()
                .id("test-dataset-id")
                .build();

        // Transform the dataset — our custom transformer should be invoked
        var result = dspRegistry.transform(dataset, JsonObject.class);

        assertThat(result.succeeded()).isTrue();
        var jsonObject = result.getContent();

        // Verify our custom marker is present — proving the override worked
        assertThat(jsonObject.getString(CUSTOM_MARKER_PROPERTY))
                .isEqualTo(CUSTOM_MARKER_VALUE);
    }

    @Test
    void shouldOverrideDistributionTransformer(TypeTransformerRegistry registry) {
        // Get the DSP v2025/1 context registry where transformers are registered
        var dspRegistry = registry.forContext("dsp-api:2025-1");

        // Create a minimal Distribution
        var distribution = Distribution.Builder.newInstance()
                .format("application/json")
                .dataService(DataService.Builder.newInstance().build())
                .build();

        // Transform the distribution — our custom transformer should be invoked
        var result = dspRegistry.transform(distribution, JsonObject.class);

        assertThat(result.succeeded()).isTrue();
        var jsonObject = result.getContent();

        // Verify our custom marker is present — proving the override worked
        assertThat(jsonObject.getString(CustomDistributionTransformer.CUSTOM_MARKER_PROPERTY))
                .isEqualTo(CustomDistributionTransformer.CUSTOM_MARKER_VALUE);
    }

    @Test
    void shouldResolveCorrectTransformerInstance(TypeTransformerRegistry registry) {
        // Get the DSP v2025/1 context registry
        var dspRegistry = registry.forContext("dsp-api:2025-1");

        // Create a minimal Dataset to use as input
        var dataset = Dataset.Builder.newInstance()
                .id("test-dataset")
                .build();

        // Verify that transformerFor returns our custom instance, not the upstream one
        var transformer = dspRegistry.transformerFor(dataset, JsonObject.class);
        assertThat(transformer).isInstanceOf(CustomDatasetTransformer.class);

        // Same for Distribution
        var distribution = Distribution.Builder.newInstance()
                .format("text/plain")
                .dataService(DataService.Builder.newInstance().build())
                .build();
        var distTransformer = dspRegistry.transformerFor(distribution, JsonObject.class);
        assertThat(distTransformer).isInstanceOf(CustomDistributionTransformer.class);
    }
}
