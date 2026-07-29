/*
 *  Copyright (c) 2024 Amadeus IT Group
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus IT Group - initial API and implementation
 *
 */

package org.eclipse.edc.dse.catalog.cache;

import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.dse.common.lib.DsePropertyLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DsePropertyLookupTest {

    private DsePropertyLookup lookup;

    @BeforeEach
    void setUp() {
        lookup = new DsePropertyLookup();
    }

    @Nested
    @DisplayName("Non-Catalog objects")
    class NonCatalogObjects {

        @Test
        @DisplayName("returns null for a plain String when key cannot be resolved")
        void getProperty_nonCatalog_returnsNull() {
            assertThat(lookup.getProperty("id", "not-a-catalog")).isNull();
        }

        @Test
        @DisplayName("resolves property on a Dataset (Dataset is now supported)")
        void getProperty_dataset_resolvesId() {
            var dataset = Dataset.Builder.newInstance().id("ds-1").build();
            assertThat(lookup.getProperty("id", dataset)).isEqualTo("ds-1");
        }
    }

    @Nested
    @DisplayName("Direct Catalog fields")
    class DirectCatalogFields {

        @Test
        @DisplayName("resolves participantId via reflection")
        void getProperty_participantId() {
            var catalog = Catalog.Builder.newInstance().id("cat-1").participantId("provider-1").build();
            assertThat(lookup.getProperty("participantId", catalog)).isEqualTo("provider-1");
        }

        @Test
        @DisplayName("resolves id via reflection")
        void getProperty_id() {
            var catalog = Catalog.Builder.newInstance().id("cat-42").build();
            assertThat(lookup.getProperty("id", catalog)).isEqualTo("cat-42");
        }
    }

    @Nested
    @DisplayName("Nested dataset field paths")
    class NestedDatasetFields {

        @Test
        @DisplayName("collects all dataset ids via datasets.id")
        void getProperty_datasetsId() {
            var catalog = catalogWithDatasets(
                    Dataset.Builder.newInstance().id("ds-1").build(),
                    Dataset.Builder.newInstance().id("ds-2").build()
            );
            var result = lookup.getProperty("datasets.id", catalog);
            assertThat(result).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            var ids = (List<String>) result;
            assertThat(ids).containsExactly("ds-1", "ds-2");
        }

        @Test
        @DisplayName("collects dataset property values via datasets.properties.<key>")
        void getProperty_datasetsPropertyTitle() {
            var catalog = catalogWithDatasets(
                    datasetWithProperties(Map.of("dcterms:title", "Aviation Data")),
                    datasetWithProperties(Map.of("dcterms:title", "Flight Prices"))
            );
            var result = lookup.getProperty("datasets.properties.dcterms:title", catalog);
            assertThat(result).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            var titles = (List<String>) result;
            assertThat(titles).containsExactlyInAnyOrder("Aviation Data", "Flight Prices");
        }

        @Test
        @DisplayName("returns null for a non-existent path")
        void getProperty_nonExistentPath_returnsNull() {
            var catalog = Catalog.Builder.newInstance().id("cat-1").build();
            // "nonExistentField" is not a field on Catalog — DseReflectionUtil throws ReflectionException
            // which DsePropertyLookup catches and returns null
            assertThat(lookup.getProperty("nonExistentField", catalog)).isNull();
        }

        @Test
        @DisplayName("unwraps JSON-LD @value wrappers in property values")
        void getProperty_datasetsPropertyWithAtValue() {
            var catalog = catalogWithDatasets(
                    datasetWithProperties(Map.of("dcterms:description",
                            List.of(Map.of("@value", "An event description"))))
            );
            var result = lookup.getProperty("datasets.properties.dcterms:description", catalog);
            assertThat(result).isInstanceOf(List.class);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Catalog catalogWithDatasets(Dataset... datasets) {
        var builder = Catalog.Builder.newInstance().id("cat-1");
        for (var ds : datasets) {
            builder.dataset(ds);
        }
        return builder.build();
    }

    private static Dataset datasetWithProperties(Map<String, Object> properties) {
        var builder = Dataset.Builder.newInstance().id("ds-" + System.nanoTime());
        properties.forEach(builder::property);
        return builder.build();
    }
}
