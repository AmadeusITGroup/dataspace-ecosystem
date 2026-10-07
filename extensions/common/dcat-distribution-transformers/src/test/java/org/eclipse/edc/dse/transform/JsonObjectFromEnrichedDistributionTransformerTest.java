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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.catalog.spi.DataService;
import org.eclipse.edc.connector.controlplane.catalog.spi.Distribution;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_ACCESS_SERVICE_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_DISTRIBUTION_TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCT_FORMAT_ATTRIBUTE;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JsonObjectFromEnrichedDistributionTransformerTest {

    private final Monitor monitor = mock();
    private final TransformerContext context = mock();
    private JsonObjectFromEnrichedDistributionTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new JsonObjectFromEnrichedDistributionTransformer(Json.createBuilderFactory(Map.of()), monitor);
    }

    @Test
    void transform_standardDistribution_delegatesToEdc() {
        var dataService = DataService.Builder.newInstance().id("service-id").build();
        var distribution = Distribution.Builder.newInstance().format("HttpData-PULL").dataService(dataService).build();
        var serviceJson = Json.createObjectBuilder().add(ID, "service-id").build();
        when(context.transform(dataService, JsonObject.class)).thenReturn(serviceJson);

        var result = transformer.transform(distribution, context);

        assertThat(result).isNotNull();
        assertThat(result.getString(TYPE)).isEqualTo(DCAT_DISTRIBUTION_TYPE);
        assertThat(result.getJsonObject(DCT_FORMAT_ATTRIBUTE).getString(ID)).isEqualTo("HttpData-PULL");
        assertThat(result.getJsonObject(DCAT_ACCESS_SERVICE_ATTRIBUTE)).isEqualTo(serviceJson);
        verifyNoInteractions(monitor);
    }

    @Test
    void transform_standardDistributionWithoutFormat_warnsAndWritesTypeOnly() {
        var distribution = Distribution.Builder.newInstance()
                .dataService(DataService.Builder.newInstance().id("service-id").build())
                .build();

        var result = transformer.transform(distribution, context);

        assertThat(result).isNotNull().hasSize(1);
        assertThat(result.getString(TYPE)).isEqualTo(DCAT_DISTRIBUTION_TYPE);
        verify(monitor).warning(anyString());
        verifyNoInteractions(context);
    }

    @Test
    void transform_enrichedDistribution_writesEnrichedProperties() {
        var properties = new HashMap<String, Object>();
        properties.put(ID, "urn:distribution:1");
        properties.put("http://www.w3.org/ns/dcat#mediaType", "application/json");
        properties.put("http://purl.org/dc/terms/conformsTo", Map.of(ID, "https://example.com/spec"));
        properties.put("tags", List.of("kafka", "event"));
        properties.put("enabled", true);
        properties.put("optional", null);
        var distribution = new EnrichedDistribution(null, null, properties);

        var result = transformer.transform(distribution, context);

        assertThat(result).isNotNull();
        assertThat(result.getString(TYPE)).isEqualTo(DCAT_DISTRIBUTION_TYPE);
        assertThat(result.getString(ID)).isEqualTo("urn:distribution:1");
        assertThat(result.getString("http://www.w3.org/ns/dcat#mediaType")).isEqualTo("application/json");
        assertThat(result.getJsonObject("http://purl.org/dc/terms/conformsTo").getString(ID)).isEqualTo("https://example.com/spec");
        assertThat(result.getJsonArray("tags").getString(1)).isEqualTo("event");
        assertThat(result.getBoolean("enabled")).isTrue();
        assertThat(result.isNull("optional")).isTrue();
        verifyNoInteractions(monitor);
    }

    @Test
    void transform_enrichedDistribution_preservesNumericPrecision() {
        var distribution = new EnrichedDistribution(null, null, Map.of(
                "bigInteger", new BigInteger("9223372036854775808"),
                "bigDecimal", new BigDecimal("1234567890.12345678901234567890")));

        var result = transformer.transform(distribution, context);

        assertThat(result).isNotNull();
        assertThat(result.getJsonNumber("bigInteger").bigIntegerValue()).isEqualTo(new BigInteger("9223372036854775808"));
        assertThat(result.getJsonNumber("bigDecimal").bigDecimalValue()).isEqualTo(new BigDecimal("1234567890.12345678901234567890"));
    }
}
