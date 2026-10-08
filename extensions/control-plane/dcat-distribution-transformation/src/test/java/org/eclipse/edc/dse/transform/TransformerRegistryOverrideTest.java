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
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.junit.extensions.RuntimePerClassExtension;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_DISTRIBUTION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCT_FORMAT_ATTRIBUTE;
import static org.eclipse.edc.util.io.Ports.getFreePort;

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
            setConfiguration(config);
        }
    };

    @Test
    void shouldRegisterDatasetTransformer(TypeTransformerRegistry registry) {
        var dataset = Dataset.Builder.newInstance().id("dataset-id").build();

        assertThat(registry.forContext("dsp-api:2025-1").transformerFor(dataset, JsonObject.class))
                .isInstanceOf(JsonObjectFromDatasetEnrichedDistributionTransformer.class);
        assertThat(registry.transformerFor(dataset, JsonObject.class))
                .isInstanceOf(JsonObjectFromDatasetEnrichedDistributionTransformer.class);
    }

    @Test
    void shouldSerializeCustomDistributionsFromDatasetProperties(TypeTransformerRegistry registry) {
        var dataset = Dataset.Builder.newInstance()
                .id("dataset-id")
                .property(DCAT_DISTRIBUTION_ATTRIBUTE, List.of(Map.of(DCT_FORMAT_ATTRIBUTE, "application/custom-json")))
                .build();

        var result = registry.forContext("dsp-api:2025-1").transform(dataset, JsonObject.class);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent().getJsonArray(DCAT_DISTRIBUTION_ATTRIBUTE))
                .singleElement()
                .extracting(value -> value.asJsonObject().getString(DCT_FORMAT_ATTRIBUTE))
                .isEqualTo("application/custom-json");
    }
}
