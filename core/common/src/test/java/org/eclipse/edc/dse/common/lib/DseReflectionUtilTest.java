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

package org.eclipse.edc.dse.common.lib;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.policy.model.Policy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DseReflectionUtilTest {

    private static Catalog catalog;

    @BeforeAll
    static void setUp() throws IOException {
        var objectMapper = new ObjectMapper();
        try (var inputStream = DseReflectionUtilTest.class.getResourceAsStream("/catalog-test-data.json")) {
            assertThat(inputStream).as("catalog-test-data.json not found on classpath").isNotNull();
            var catalogData = objectMapper.readValue(inputStream, new TypeReference<Map<String, Object>>() {});

            var catalogBuilder = Catalog.Builder.newInstance()
                    .id((String) catalogData.get("id"))
                    .participantId((String) catalogData.get("participantId"));

            // Load datasets
            @SuppressWarnings("unchecked")
            var datasetsData = (List<Map<String, Object>>) catalogData.get("datasets");
            for (var datasetData : datasetsData) {
                var datasetBuilder = Dataset.Builder.newInstance()
                        .id((String) datasetData.get("id"));

                // Load offers
                @SuppressWarnings("unchecked")
                var offers = (Map<String, Object>) datasetData.get("offers");
                if (offers != null) {
                    offers.forEach((offerId, policyData) ->
                            datasetBuilder.offer(offerId, Policy.Builder.newInstance().build()));
                }

                // Load properties
                @SuppressWarnings("unchecked")
                var properties = (Map<String, Object>) datasetData.get("properties");
                if (properties != null) {
                    properties.forEach(datasetBuilder::property);
                }

                catalogBuilder.dataset(datasetBuilder.build());
            }

            catalog = catalogBuilder.build();
        }
    }

    @Nested
    @DisplayName("datasets.properties.dcterms:* — simple string values")
    class SimpleStringProperties {

        @Test
        @DisplayName("datasets.properties.dcterms:title — collects title from all datasets")
        void getFieldValue_title() {
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcterms:title", catalog);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("Order Created Event", "Flight Offer Data");
        }

        @Test
        @DisplayName("datasets.properties.dcterms:format — collects format value objects from all datasets")
        void getFieldValue_format() {
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcterms:format", catalog);

            assertThat(value).isInstanceOf(List.class);
            // When a single @value map is at the terminal of a list-iteration path, it remains as a map
            assertThat((List<?>) value).hasSize(2);
            var unwrapped = ((List<Map<?, ?>>) value).stream().map(m -> m.get("@value")).toList();
            assertThat(unwrapped).isEqualTo(List.of("application/json", "text/csv"));
        }

        @Test
        @DisplayName("datasets.id — collects all dataset ids")
        void getFieldValue_ids() {
            var value = DseReflectionUtil.getFieldValue("datasets.id", catalog);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("urn:dataset:order-event", "urn:dataset:flight-offer");
        }
    }

    @Nested
    @DisplayName("datasets.properties.dcterms:test.dcterms:data — nested map then list with @value")
    class NestedMapWithListUnwrap {

        @Test
        @DisplayName("datasets.properties.dcterms:test — collects dcterms:test map from all datasets")
        void getFieldValue_testMap() {
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcterms:test", catalog);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<?>) value).hasSize(2);
        }

        @Test
        @DisplayName("datasets.properties.dcterms:test.dcterms:data — unwraps @value from nested list")
        void getFieldValue_testData() {
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcterms:test.dcterms:data", catalog);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("fake", "mock");
        }
    }

    @Nested
    @DisplayName("datasets.properties.dcterms:creator.* — nested object field access")
    class NestedObjectAccess {

        @Test
        @DisplayName("datasets.properties.dcterms:creator — collects creator maps from all datasets")
        void getFieldValue_creator() {
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcterms:creator", catalog);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<?>) value).hasSize(2);
        }

        @Test
        @DisplayName("datasets.properties.dcterms:creator.@id — collects @id from all creators")
        void getFieldValue_creatorId() {
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcterms:creator.@id", catalog);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("urn:participant:amadeus", "urn:participant:airline");
        }

        @Test
        @DisplayName("datasets.properties.dcterms:creator.dcterms:name — collects name lists from all creators")
        void getFieldValue_creatorName() {
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcterms:creator.dcterms:name", catalog);

            assertThat(value).isInstanceOf(List.class);
            // Each dataset's creator has a dcterms:name list; unwrapping @value yields the name strings
            assertThat((List<String>) value).containsExactly("Amadeus", "Partner Airline");
        }
    }

    @Nested
    @DisplayName("datasets.properties.dcterms:subject — list of @value objects")
    class ListOfValueObjects {

        @Test
        @DisplayName("datasets.properties.dcterms:subject — collects and unwraps @value from subject lists")
        void getFieldValue_subject() {
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcterms:subject", catalog);

            assertThat(value).isInstanceOf(List.class);
            // Collects all subjects across all datasets, unwrapped from @value
            assertThat((List<String>) value).contains("booking", "travel", "order", "flights", "availability");
        }
    }

    @Nested
    @DisplayName("datasets.properties.dcterms:description — single-element @value list")
    class DescriptionAccess {

        @Test
        @DisplayName("datasets.properties.dcterms:description — collects descriptions with @value unwrap")
        void getFieldValue_description() {
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcterms:description", catalog);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly(
                    "An event emitted when an order is created",
                    "Real-time flight availability and pricing"
            );
        }
    }

    @Nested
    @DisplayName("datasets.properties.dcterms:spatial.dcterms:coverage.dcterms:regions — deeply nested")
    class DeeplyNestedAccess {

        @Test
        @DisplayName("datasets.properties.dcterms:spatial — collects spatial maps")
        void getFieldValue_spatial() {
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcterms:spatial", catalog);

            assertThat(value).isInstanceOf(List.class);
            // Only first dataset has dcterms:spatial
            assertThat((List<?>) value).hasSize(1);
        }

        @Test
        @DisplayName("datasets.properties.dcterms:spatial.dcterms:coverage — accesses coverage")
        void getFieldValue_spatialCoverage() {
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcterms:spatial.dcterms:coverage", catalog);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<?>) value).hasSize(1);
        }

        @Test
        @DisplayName("datasets.properties.dcterms:spatial.dcterms:coverage.dcterms:regions — accesses regions with @value unwrap")
        void getFieldValue_spatialCoverageRegions() {
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcterms:spatial.dcterms:coverage.dcterms:regions", catalog);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).contains("EU", "US", "APAC");
        }
    }

    @Nested
    @DisplayName("datasets.properties.dcat:distribution.* — list of complex objects")
    class DistributionAccess {

        @Test
        @DisplayName("datasets.properties.dcat:distribution — collects distributions")
        void getFieldValue_distribution() {
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcat:distribution", catalog);

            assertThat(value).isInstanceOf(List.class);
            // First dataset has 2 distributions, second dataset has none
            assertThat((List<?>) value).hasSize(2);
        }

        @Test
        @DisplayName("datasets.properties.dcat:distribution.dcterms:format — collects format from all distributions")
        void getFieldValue_distributionFormat() {
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcat:distribution.dcterms:format", catalog);

            assertThat(value).isInstanceOf(List.class);
            var unwrappedFormats = ((List<Map<?, ?>>) value).stream().map(m -> m.get("@value")).toList();
            assertThat(unwrappedFormats).isEqualTo(List.of("application/json", "application/octet-stream"));
        }

        @Test
        @DisplayName("datasets.properties.dcat:distribution.dcat:byteSize — collects byteSize from distributions")
        void getFieldValue_distributionByteSize() {
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcat:distribution.dcat:byteSize", catalog);

            assertThat(value).isInstanceOf(List.class);
            var unwrappedSizes = ((List<Map<?, ?>>) value).stream().map(m -> m.get("@value")).toList();
            assertThat(unwrappedSizes).isEqualTo(List.of("2048", "4096"));
        }

        @Test
        @DisplayName("datasets.properties.dcat:distribution.dcat:accessURL — collects accessURL @value from distributions")
        void getFieldValue_distributionAccessUrl() {
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcat:distribution.dcat:accessURL", catalog);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).contains(
                    "https://api.example.com/orders",
                    "https://api.example.com/orders/stream"
            );
        }
    }

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("Should throw NullPointerException for null property name")
        void getFieldValue_nullPropertyName() {
            assertThatThrownBy(() -> DseReflectionUtil.getFieldValue(null, catalog))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Should throw NullPointerException for null object")
        void getFieldValue_nullObject() {
            assertThatThrownBy(() -> DseReflectionUtil.getFieldValue("datasets.id", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("datasets.properties.dcterms:nonExistent — returns empty list for missing keys")
        void getFieldValue_nonExistentKey() {
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcterms:nonExistent", catalog);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<?>) value).isEmpty();
        }
    }

    @Nested
    @DisplayName("resolveElement — list branch (element is a List, resolves property recursively for each item)")
    class ResolveElementListBranch {

        @Test
        @DisplayName("Should resolve property from nested list of @value maps: datasets.properties.dcterms:subject")
        void getFieldValue_listOfValueMaps() {
            // dcterms:subject is a List of @value maps — resolveElement iterates and unwraps each
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcterms:subject", catalog);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).contains("booking", "travel", "order");
        }

        @Test
        @DisplayName("Should resolve property through nested lists: datasets.properties.dcat:distribution.dcat:accessURL")
        void getFieldValue_listOfListsProperty() {
            // dcat:distribution is a List of Maps, each containing dcat:accessURL which is also a List
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcat:distribution.dcat:accessURL", catalog);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).contains(
                    "https://api.example.com/orders",
                    "https://api.example.com/orders/stream"
            );
        }

        @Test
        @DisplayName("Should resolve inner list property for each dataset: datasets.properties.dcterms:description")
        void getFieldValue_listWithSingleElementLists() {
            // Each dataset has dcterms:description as a single-element list — resolveElement iterates each
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcterms:description", catalog);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly(
                    "An event emitted when an order is created",
                    "Real-time flight availability and pricing"
            );
        }

        @Test
        @DisplayName("Should flatten nested list results: datasets.properties.dcat:distribution.dcat:byteSize")
        void getFieldValue_flattenNestedListResults() {
            // Each distribution has dcat:byteSize as a @value map — resolveElement iterates distributions
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcat:distribution.dcat:byteSize", catalog);

            assertThat(value).isInstanceOf(List.class);
            var unwrapped = ((List<Map<?, ?>>) value).stream().map(m -> m.get("@value")).toList();
            assertThat(unwrapped).isEqualTo(List.of("2048", "4096"));
        }

        @Test
        @DisplayName("Should return empty list when property does not exist in any dataset: datasets.properties.dcterms:nonExistent")
        void getFieldValue_missingPropertyInList() {
            var value = DseReflectionUtil.getFieldValue("datasets.properties.dcterms:nonExistent", catalog);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<?>) value).isEmpty();
        }
    }

    @Nested
    @DisplayName("Array indexer — field[n] syntax to access specific element in a list")
    class ArrayIndexerAccess {

        @Test
        @DisplayName("Should access first dataset by index: datasets[0]")
        void getFieldValue_firstDatasetByIndex() {
            Object value = DseReflectionUtil.getFieldValue("datasets[0]", catalog);

            assertThat(value).isNotNull();
            assertThat(value).isInstanceOf(Dataset.class);
            assertThat(((Dataset) value).getId()).isEqualTo("urn:dataset:order-event");
        }

        @Test
        @DisplayName("Should access second dataset by index: datasets[1]")
        void getFieldValue_secondDatasetByIndex() {
            Object value = DseReflectionUtil.getFieldValue("datasets[1]", catalog);

            assertThat(value).isNotNull();
            assertThat(value).isInstanceOf(Dataset.class);
            assertThat(((Dataset) value).getId()).isEqualTo("urn:dataset:flight-offer");
        }

        @Test
        @DisplayName("Should access dataset id via index and dot notation: datasets[0].id")
        void getFieldValue_indexedThenField() {
            String value = DseReflectionUtil.getFieldValue("datasets[0].id", catalog);

            assertThat(value).isEqualTo("urn:dataset:order-event");
        }

        @Test
        @DisplayName("Should access nested property via index: datasets[0].properties.dcterms:title")
        void getFieldValue_indexedThenNestedProperty() {
            Object value = DseReflectionUtil.getFieldValue("datasets[0].properties.dcterms:title", catalog);

            assertThat(value).isEqualTo("Order Created Event");
        }

        @Test
        @DisplayName("Should access element from a list property via index: datasets[0].properties.dcterms:subject[0]")
        void getFieldValue_indexOnNestedListProperty() {
            Object value = DseReflectionUtil.getFieldValue("datasets[0].properties.dcterms:subject[0]", catalog);

            assertThat(value).isInstanceOf(Map.class);
            assertThat(((Map<?, ?>) value).get("@value")).isEqualTo("booking");
        }

        @Test
        @DisplayName("Should access deep path via index: datasets[0].properties.dcterms:test.dcterms:data")
        void getFieldValue_indexedThenDeepPath() {
            var value = DseReflectionUtil.getFieldValue("datasets[0].properties.dcterms:test.dcterms:data", catalog);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<?>) value).isNotEmpty();
        }

        @Test
        @DisplayName("Should access second dataset title via index: datasets[1].properties.dcterms:title")
        void getFieldValue_secondDatasetTitle() {
            Object value = DseReflectionUtil.getFieldValue("datasets[1].properties.dcterms:title", catalog);

            assertThat(value).isEqualTo("Flight Offer Data");
        }

        @Test
        @DisplayName("Should throw IndexOutOfBoundsException for out-of-range index: datasets[99]")
        void getFieldValue_indexOutOfBounds() {
            assertThatThrownBy(() -> DseReflectionUtil.getFieldValue("datasets[99]", catalog))
                    .isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    @Nested
    @DisplayName("Coverage — resolveElement list branch and null/error paths")
    class CoverageEdgeCases {

        @Test
        @DisplayName("Should resolve property from element that is a nested list (dcterms:keywords contains lists)")
        void getFieldValue_elementIsList() {
            // dcterms:keywords is a list of lists — resolveElement's List branch iterates inner lists
            var value = DseReflectionUtil.getFieldValue("datasets[0].properties.dcterms:keywords", catalog);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<?>) value).hasSize(2);
        }

        @Test
        @DisplayName("Should traverse list of lists and resolve @value: datasets[0].properties.dcterms:relatedSets.@value")
        void getFieldValue_listOfListsResolveProperty() {
            // dcterms:relatedSets is [[{@value:set-a1},{@value:set-a2}],[{@value:set-b1},{@value:set-b2}]]
            // Accessing .@value triggers resolveElement with each inner List element,
            // which hits the `element instanceof List` branch in resolveElement
            var value = DseReflectionUtil.getFieldValue("datasets[0].properties.dcterms:relatedSets.@value", catalog);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).contains("set-a1", "set-a2", "set-b1", "set-b2");
        }

        @Test
        @DisplayName("Should return null when intermediate path resolves to null: datasets[0].properties.dcterms:nullableRef.dcterms:inner.something")
        void getFieldValue_nullIntermediatePath() {
            // dcterms:nullableRef.dcterms:inner is null, so traversing deeper should return null
            var value = DseReflectionUtil.getFieldValue("datasets[0].properties.dcterms:nullableRef.dcterms:inner.something", catalog);

            assertThat(value).isNull();
        }

        @Test
        @DisplayName("Should throw ReflectionException when field does not exist on object")
        void getFieldValue_fieldNotExistOnObject() {
            // Accessing a non-existent field directly on catalog (not via Map) throws ReflectionException
            assertThatThrownBy(() -> DseReflectionUtil.getFieldValue("nonExistentField", catalog))
                    .isInstanceOf(org.eclipse.edc.util.reflection.ReflectionException.class);
        }
    }
}
