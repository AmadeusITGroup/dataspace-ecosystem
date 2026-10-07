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

import org.eclipse.edc.connector.controlplane.catalog.spi.DataService;
import org.eclipse.edc.connector.controlplane.catalog.spi.Distribution;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrichedDistributionTest {

    @Test
    void testConstructorWithFormatAndDataService() {
        String format = "HttpData-PULL";
        DataService dataService = createDataService("http://example.com/dsp");

        EnrichedDistribution dist = new EnrichedDistribution(format, dataService, null);

        assertThat(dist.getFormat()).isEqualTo(format);
        assertThat(dist.getDataService()).isEqualTo(dataService);
        assertThat(dist.hasProperties()).isFalse();
        assertThat(dist.getProperties()).isEmpty();
    }

    @Test
    void testConstructorWithProperties() {
        String format = "HttpData-PULL";
        DataService dataService = createDataService("http://example.com/dsp");
        Map<String, Object> enrichedProps = new HashMap<>();
        enrichedProps.put("dcat:mediaType", "application/json");
        enrichedProps.put("dcat:byteSize", 1024);

        EnrichedDistribution dist = new EnrichedDistribution(format, dataService, enrichedProps);

        assertThat(dist.getFormat()).isEqualTo(format);
        assertThat(dist.getDataService()).isEqualTo(dataService);
        assertThat(dist.hasProperties()).isTrue();
        assertThat(dist.getProperties())
                .containsEntry("dcat:mediaType", "application/json")
                .containsEntry("dcat:byteSize", 1024);
    }

    @Test
    void testConstructorCopiesPropertiesMap() {
        String format = "HttpData-PULL";
        Map<String, Object> enrichedProps = new HashMap<>();
        enrichedProps.put("key", "value");

        EnrichedDistribution dist = new EnrichedDistribution(format, null, enrichedProps);
        enrichedProps.put("key2", "value2");

        assertThat(dist.getProperties())
                .containsEntry("key", "value")
                .doesNotContainKey("key2");
    }

    @Test
    void testConstructorFromDistribution() {
        Distribution baseDist = Distribution.Builder.newInstance()
                .format("HttpData-PUSH")
                .dataService(createDataService("http://example.com/dsp"))
                .build();
        Map<String, Object> enrichedProps = new HashMap<>();
        enrichedProps.put("dcat:mediaType", "application/xml");

        EnrichedDistribution dist = new EnrichedDistribution(baseDist, enrichedProps);

        assertThat(dist.getFormat()).isEqualTo("HttpData-PUSH");
        assertThat(dist.getDataService()).isEqualTo(baseDist.getDataService());
        assertThat(dist.getProperties()).containsEntry("dcat:mediaType", "application/xml");
    }

    @Test
    void testConstructorFromDistributionHandlesNullPropertiesAndToString() {
        var baseDistribution = Distribution.Builder.newInstance()
                .format("HttpData-PULL")
                .dataService(createDataService("http://example.com/dsp"))
                .build();

        var distribution = new EnrichedDistribution(baseDistribution, null);

        assertThat(distribution.getProperties()).isEmpty();
        assertThat(distribution.toString()).contains("HttpData-PULL");
    }

    @Test
    void testGetPropertiesReturnsUnmodifiableMap() {
        Map<String, Object> props = new HashMap<>();
        props.put("key", "value");
        EnrichedDistribution dist = new EnrichedDistribution("format", null, props);

        assertThatThrownBy(() -> dist.getProperties().put("key2", "value2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testGetMutableProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("key", "value");
        EnrichedDistribution dist = new EnrichedDistribution("format", null, props);

        Map<String, Object> mutable = dist.getMutableProperties();
        mutable.put("key2", "value2");

        assertThat(dist.getProperties()).doesNotContainKey("key2");
        assertThat(mutable).containsEntry("key2", "value2");
    }

    @Test
    void testWithProperty() {
        EnrichedDistribution dist = new EnrichedDistribution("format", null, null);

        EnrichedDistribution result = dist.withProperty("dcat:mediaType", "text/plain");

        assertThat(result).isSameAs(dist);
        assertThat(dist.getProperties()).containsEntry("dcat:mediaType", "text/plain");
    }

    @Test
    void testWithProperties() {
        EnrichedDistribution dist = new EnrichedDistribution("format", null, null);
        Map<String, Object> props = new HashMap<>();
        props.put("dcat:mediaType", "application/json");
        props.put("dcat:byteSize", 2048);

        EnrichedDistribution result = dist.withProperties(props);

        assertThat(result).isSameAs(dist);
        assertThat(dist.getProperties())
                .containsEntry("dcat:mediaType", "application/json")
                .containsEntry("dcat:byteSize", 2048);
    }

    @Test
    void testWithPropertiesHandlesNull() {
        var distribution = new EnrichedDistribution("format", null, null);

        assertThat(distribution.withProperties(null)).isSameAs(distribution);
        assertThat(distribution.getProperties()).isEmpty();
    }

    @Test
    void testHasProperties() {
        EnrichedDistribution empty = new EnrichedDistribution("format", null, null);
        Map<String, Object> props = new HashMap<>();
        props.put("key", "value");
        EnrichedDistribution withProps = new EnrichedDistribution("format", null, props);

        assertThat(empty.hasProperties()).isFalse();
        assertThat(withProps.hasProperties()).isTrue();
    }

    @Test
    void testToString() {
        Map<String, Object> props = new HashMap<>();
        props.put("dcat:mediaType", "application/json");
        EnrichedDistribution dist = new EnrichedDistribution("HttpData-PULL", null, props);

        var result = dist.toString();

        assertThat(result)
                .contains("HttpData-PULL")
                .contains("dcat:mediaType");
    }

    @Test
    void testBuilderPattern() {
        DataService dataService = createDataService("http://example.com/dsp");
        Map<String, Object> props = new HashMap<>();
        props.put("dcat:mediaType", "application/json");

        EnrichedDistribution dist = EnrichedDistribution.Builder.newInstance()
                .format("HttpData-PULL")
                .dataService(dataService)
                .properties(props)
                .property("dcat:byteSize", 1024)
                .build();

        assertThat(dist.getFormat()).isEqualTo("HttpData-PULL");
        assertThat(dist.getDataService()).isEqualTo(dataService);
        assertThat(dist.getProperties())
                .containsEntry("dcat:mediaType", "application/json")
                .containsEntry("dcat:byteSize", 1024);
    }

    @Test
    void testBuilderAcceptsNullProperties() {
        var distribution = EnrichedDistribution.Builder.newInstance()
                .format("HttpData-PULL")
                .dataService(createDataService("http://example.com/dsp"))
                .properties(null)
                .build();

        assertThat(distribution.getProperties()).isEmpty();
    }

    @Test
    void testBuilderThrowsIfDataServiceNotSet() {
        assertThatThrownBy(() -> EnrichedDistribution.Builder.newInstance()
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("dataService is required");
    }

    @Test
    void testBuilderDataServiceCannotBeNull() {
        assertThatThrownBy(() -> EnrichedDistribution.Builder.newInstance()
                .dataService(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("dataService cannot be null");
    }

    private DataService createDataService(String url) {
        return DataService.Builder.newInstance().endpointUrl(url).build();
    }
}
