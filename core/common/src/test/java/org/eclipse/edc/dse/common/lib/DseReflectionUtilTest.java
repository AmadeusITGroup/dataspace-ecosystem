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
 */

package org.eclipse.edc.dse.common.lib;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.util.reflection.ReflectionException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link DseReflectionUtil#getFieldValue(String, Object)} against the two {@link Dataset}s
 * loaded from {@code catalog-test-data.json}: {@code dataset1} ("Order Created Event") and
 * {@code dataset2} ("Booking Confirmed Event").
 *
 * <p>Every path here is resolved directly against a single {@link Dataset} instance (not the
 * {@link Catalog}), so paths start with {@code properties.'<propertyUri>'} — no {@code datasets.}
 * prefix and no {@code [n]} array indexer into {@code datasets}, per how a caller would query one
 * dataset at a time.
 *
 * <p>Generic attributes (title, type, keyword, id/createdAt/updatedAt, hasVersion, etc.) share the
 * same JSON-LD shape across both datasets, so they are verified against {@code dataset1} only.
 *
 * <p>{@code dcat:distribution} and {@code dcat:themeTaxonomy} are the two properties that differ in
 * shape between the datasets, so they are verified against both:
 * <ul>
 *     <li>{@code dataset1}: both are a single JSON object (Map).</li>
 *     <li>{@code dataset2}: both are a JSON array (List) of two objects.</li>
 * </ul>
 * This confirms {@link DseReflectionUtil} resolves the same path correctly regardless of which shape
 * is present, without ever hardcoding an array index — the Map/List-aware branches of
 * {@code getFieldValue} handle the difference transparently.
 */
class DseReflectionUtilTest {

    private static Dataset dataset1;
    private static Dataset dataset2;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void setUp() throws IOException {
        var objectMapper = new ObjectMapper();
        try (var inputStream = DseReflectionUtilTest.class.getResourceAsStream("/catalog-test-data.json")) {
            assertThat(inputStream).as("catalog-test-data.json not found on classpath").isNotNull();
            var catalogData = objectMapper.readValue(inputStream, new TypeReference<Map<String, Object>>() {
            });

            var datasetsData = (List<Map<String, Object>>) catalogData.get("datasets");
            assertThat(datasetsData).as("expected at least 2 datasets in catalog-test-data.json").hasSizeGreaterThanOrEqualTo(2);

            dataset1 = buildDataset(datasetsData.get(0));
            dataset2 = buildDataset(datasetsData.get(1));
        }
    }

    @SuppressWarnings("unchecked")
    private static Dataset buildDataset(Map<String, Object> datasetData) {
        var datasetBuilder = Dataset.Builder.newInstance()
                .id((String) datasetData.get("id"));

        var offers = (Map<String, Object>) datasetData.get("offers");
        if (offers != null) {
            offers.forEach((offerId, policyData) ->
                    datasetBuilder.offer(offerId, Policy.Builder.newInstance().build()));
        }

        var properties = (Map<String, Object>) datasetData.get("properties");
        if (properties != null) {
            properties.forEach(datasetBuilder::property);
        }

        return datasetBuilder.build();
    }

    @Nested
    @DisplayName("properties.* — generic attributes (verified once, shape is identical across datasets)")
    class GenericAttributes {

        @Test
        @DisplayName("properties.'dc/terms/title' = Order Created Event")
        void title() {
            Object value = DseReflectionUtil.getFieldValue("properties.'http://purl.org/dc/terms/title'", dataset1);

            assertThat(value).isEqualTo("Order Created Event");
        }

        @Test
        @DisplayName("properties.'dc/terms/type' = EVENT")
        void type() {
            Object value = DseReflectionUtil.getFieldValue("properties.'http://purl.org/dc/terms/type'", dataset1);

            assertThat(value).isEqualTo("EVENT");
        }

        @Test
        @DisplayName("properties.'dcat#keyword' = BUSINESS")
        void keyword() {
            Object value = DseReflectionUtil.getFieldValue("properties.'http://www.w3.org/ns/dcat#keyword'", dataset1);

            assertThat(value).isEqualTo("BUSINESS");
        }

        @Test
        @DisplayName("properties.'edc/.../id' matches the dataset's own id")
        void edcId() {
            Object value = DseReflectionUtil.getFieldValue("properties.'https://w3id.org/edc/v0.0.1/ns/id'", dataset1);

            assertThat(value).isEqualTo("https://test.platform.api.amadeus.com/content-management/content-definitions/order-created-event");
        }

        @Test
        @DisplayName("properties.'edc/.../createdAt' and 'edc/.../updatedAt' are plain timestamp strings")
        void timestamps() {
            Object createdAt = DseReflectionUtil.getFieldValue("properties.'https://w3id.org/edc/v0.0.1/ns/createdAt'", dataset1);
            Object updatedAt = DseReflectionUtil.getFieldValue("properties.'https://w3id.org/edc/v0.0.1/ns/updatedAt'", dataset1);

            assertThat(createdAt).isEqualTo("2026-07-23T15:38:16.224Z");
            assertThat(updatedAt).isEqualTo("2026-07-08T13:31:02.821203588Z");
        }

        @Test
        @DisplayName("properties.'dc/terms/description' and 'dc/terms/issued' are present")
        void descriptionAndIssued() {
            Object description = DseReflectionUtil.getFieldValue("properties.'http://purl.org/dc/terms/description'", dataset1);
            Object issued = DseReflectionUtil.getFieldValue("properties.'http://purl.org/dc/terms/issued'", dataset1);

            assertThat(description).isEqualTo("Event emitted when a new order is created in the order management system. Contains complete order details including items, customer information, and payment status.");
            assertThat(issued).isEqualTo("2023-11-19");
        }

        @Test
        @DisplayName("properties.'dc/terms/description' is absent on dataset2 (returns null)")
        void descriptionIsAbsentOnDataset2() {
            Object value = DseReflectionUtil.getFieldValue("properties.'http://purl.org/dc/terms/description'", dataset2);

            assertThat(value).isNull();
        }

        @Test
        @DisplayName("direct map key returns list values unwrapped via unwrapMapValue list branch")
        void directMapKey_listValue_unwrappedViaFlatten() {
            // A property stored as a List of {@value} maps is unwrapped to a List of plain strings
            var dataset = Dataset.Builder.newInstance()
                    .id("ds-direct")
                    .property("dcat:theme", List.of(
                            Map.of("@value", "theme1"),
                            Map.of("@value", "theme2")
                    ))
                    .build();

            Object value = DseReflectionUtil.getFieldValue("properties.'dcat:theme'", dataset);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("theme1", "theme2");
        }

        @Test
        @DisplayName("missing property key returns null, not exception")
        void missingKey_returnsNull() {
            Object value = DseReflectionUtil.getFieldValue("properties.'no-such-key'", dataset1);
            assertThat(value).isNull();
        }

        @Test
        @DisplayName("properties.'dc/terms/test'.'dc/terms/data' unwraps a nested @value list to a plain String")
        void nestedValueUnwrap() {
            Object value = DseReflectionUtil.getFieldValue(
                    "properties.'http://purl.org/dc/terms/test'.'http://purl.org/dc/terms/data'", dataset1);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("fake");
        }

        @Test
        @DisplayName("properties.'dcat#hasVersion.@id' collects two version @id references")
        void hasVersionIds() {
            Object value = DseReflectionUtil.getFieldValue("properties.'http://www.w3.org/ns/dcat#hasVersion'.'@id'", dataset1);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly(
                    "urn:amadeus:platform:content-management:nevio:content-definitions:order-created-event:versions:v1.0.0",
                    "urn:amadeus:platform:content-management:nevio:content-definitions:order-created-event:versions:v1.1.0"
            );
        }
    }

    @Nested
    @DisplayName("properties.'dcat#qualifiedAttribution' — provenance attribution")
    class QualifiedAttribution {

        private static final String QUALIFIED_ATTRIBUTION_PATH = "properties.'http://www.w3.org/ns/prov#qualifiedAttribution'";

        @Test
        @DisplayName("agent.foaf:name unwraps to AF")
        void agentName() {
            Object value = DseReflectionUtil.getFieldValue(
                    QUALIFIED_ATTRIBUTION_PATH + ".'http://www.w3.org/ns/prov#agent'.'foaf:name'", dataset1);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("AF");
        }

        @Test
        @DisplayName("hadRole.@id is present on dataset1")
        void hadRolePresentOnDataset1() {
            Object value = DseReflectionUtil.getFieldValue(
                    QUALIFIED_ATTRIBUTION_PATH + ".'http://www.w3.org/ns/prov#hadRole'.'@id'", dataset1);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("urn:amadeus:platform:content-management:role:owner");
        }

        @Test
        @DisplayName("hadRole is absent on dataset2 (returns null, since qualifiedAttribution is a plain Map)")
        void hadRoleAbsentOnDataset2() {
            Object value = DseReflectionUtil.getFieldValue(
                    QUALIFIED_ATTRIBUTION_PATH + ".'http://www.w3.org/ns/prov#hadRole'.'@id'", dataset2);

            assertThat(value).isNull();
        }
    }

    @Nested
    @DisplayName("properties.'dcat#distribution' — differs in shape between datasets (Map vs List)")
    class DistributionAccess {

        private static final String DISTRIBUTION_PATH = "properties.'http://www.w3.org/ns/dcat#distribution'";

        @Test
        @DisplayName("dataset1 (single Map): accessService.@type resolves without an array indexer")
        void dataset1_accessServiceType() {
            Object value = DseReflectionUtil.getFieldValue(
                    DISTRIBUTION_PATH + ".'http://www.w3.org/ns/dcat#accessService'.'@type'", dataset1);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("http://www.w3.org/ns/dcat#DataService");
        }

        @Test
        @DisplayName("dataset1 (single Map): accessService.conformsTo.title unwraps a deeply nested @value")
        void dataset1_conformsToTitle() {
            Object value = DseReflectionUtil.getFieldValue(
                    DISTRIBUTION_PATH + ".'http://www.w3.org/ns/dcat#accessService'.'http://purl.org/dc/terms/conformsTo'.'http://purl.org/dc/terms/title'",
                    dataset1);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("Binding Kafka-specific information on AsyncAPI");
        }

        @Test
        @DisplayName("dataset1 (single Map): accessService.mediaType unwraps to the media type string")
        void dataset1_mediaType() {
            Object value = DseReflectionUtil.getFieldValue(
                    DISTRIBUTION_PATH + ".'http://www.w3.org/ns/dcat#accessService'.'http://www.w3.org/ns/dcat#mediaType'", dataset1);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("https://www.iana.org/assignments/media-types/application/json");
        }

        @Test
        @DisplayName("dataset2 (List of 2): @type resolves across both distributions without an array indexer")
        void dataset2_distributionType() {
            Object value = DseReflectionUtil.getFieldValue(DISTRIBUTION_PATH + ".'@type'", dataset2);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly(
                    "http://www.w3.org/ns/dcat#Distribution",
                    "http://www.w3.org/ns/dcat#Distribution"
            );
        }

        @Test
        @DisplayName("dataset2 (List of 2): accessService.@id collects ids from both distributions")
        void dataset2_accessServiceIds() {
            Object value = DseReflectionUtil.getFieldValue(
                    DISTRIBUTION_PATH + ".'http://www.w3.org/ns/dcat#accessService'.'@id'", dataset2);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly(
                    "urn:amadeus:platform:content-management:nevio:content-distributions:booking-confirmed-event:kafka:service",
                    "urn:amadeus:platform:content-management:nevio:content-distributions:booking-confirmed-event:http:service"
            );
        }

        @Test
        @DisplayName("dataset2 (List of 2): mediaType is present only on the first (Kafka) distribution")
        void dataset2_mediaTypeOnlyOnFirstDistribution() {
            Object value = DseReflectionUtil.getFieldValue(
                    DISTRIBUTION_PATH + ".'http://www.w3.org/ns/dcat#accessService'.'http://www.w3.org/ns/dcat#mediaType'", dataset2);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("application/json");
        }
    }

    @Nested
    @DisplayName("properties.'dcat#themeTaxonomy' — differs in shape between datasets (Map vs List)")
    class ThemeTaxonomyAccess {

        private static final String THEME_TAXONOMY_PATH = "properties.'http://www.w3.org/ns/dcat#themeTaxonomy'";
        private static final String TOP_CONCEPT_KEY = "'http://www.w3.org/2004/02/skos/core#hasTopConcept'";
        private static final String PREF_LABEL_KEY = "'http://www.w3.org/2004/02/skos/core#prefLabel'";
        private static final String NOTATION_KEY = "'http://www.w3.org/2004/02/skos/core#notation'";
        private static final String NARROWER_KEY = "'http://www.w3.org/2004/02/skos/core#narrower'";

        @Test
        @DisplayName("dataset1 (single Map): title unwraps to Enterprise Domain Taxonomy")
        void dataset1_title() {
            Object value = DseReflectionUtil.getFieldValue(THEME_TAXONOMY_PATH + ".'http://purl.org/dc/terms/title'", dataset1);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("Enterprise Domain Taxonomy");
        }

        @Test
        @DisplayName("dataset1 (single Map): hasTopConcept.prefLabel = Airline Order")
        void dataset1_topConceptPrefLabel() {
            Object value = DseReflectionUtil.getFieldValue(
                    THEME_TAXONOMY_PATH + "." + TOP_CONCEPT_KEY + "." + PREF_LABEL_KEY, dataset1);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("Airline Order");
        }

        @Test
        @DisplayName("dataset1 (single Map): hasTopConcept.notation = 50")
        void dataset1_topConceptNotation() {
            Object value = DseReflectionUtil.getFieldValue(
                    THEME_TAXONOMY_PATH + "." + TOP_CONCEPT_KEY + "." + NOTATION_KEY, dataset1);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("50");
        }

        @Test
        @DisplayName("dataset1 (single Map): hasTopConcept.narrower.prefLabel = Order Management System")
        void dataset1_narrowerConceptPrefLabel() {
            Object value = DseReflectionUtil.getFieldValue(
                    THEME_TAXONOMY_PATH + "." + TOP_CONCEPT_KEY + "." + NARROWER_KEY + "." + PREF_LABEL_KEY, dataset1);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("Order Management System");
        }

        @Test
        @DisplayName("dataset2 (List of 2): title collects titles from both taxonomies without an array indexer")
        void dataset2_title() {
            Object value = DseReflectionUtil.getFieldValue(THEME_TAXONOMY_PATH + ".'http://purl.org/dc/terms/title'", dataset2);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("Booking Domain Taxonomy", "Payments Domain Taxonomy");
        }

        @Test
        @DisplayName("dataset2 (List of 2): hasTopConcept.prefLabel collects labels from both taxonomies")
        void dataset2_topConceptPrefLabel() {
            Object value = DseReflectionUtil.getFieldValue(
                    THEME_TAXONOMY_PATH + "." + TOP_CONCEPT_KEY + "." + PREF_LABEL_KEY, dataset2);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("Booking Management", "Payment Processing");
        }

        @Test
        @DisplayName("dataset2 (List of 2): hasTopConcept.notation collects notations from both taxonomies")
        void dataset2_topConceptNotation() {
            Object value = DseReflectionUtil.getFieldValue(
                    THEME_TAXONOMY_PATH + "." + TOP_CONCEPT_KEY + "." + NOTATION_KEY, dataset2);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("60", "70");
        }

        @Test
        @DisplayName("dataset2: hasTopConcept.narrower is absent on both taxonomies (returns empty list)")
        void dataset2_narrowerIsAbsent() {
            Object value = DseReflectionUtil.getFieldValue(
                    THEME_TAXONOMY_PATH + "." + TOP_CONCEPT_KEY + "." + NARROWER_KEY, dataset2);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<?>) value).isEmpty();
        }
    }

    @Nested
    @DisplayName("Error and edge cases — closes remaining DseReflectionUtil branch coverage")
    class ErrorAndEdgeCases {

        @Test
        @DisplayName("Null propertyName argument throws NullPointerException")
        void nullPropertyName_throwsNpe() {
            assertThatThrownBy(() -> DseReflectionUtil.getFieldValue(null, dataset1))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Null object argument throws NullPointerException")
        void nullObject_throwsNpe() {
            assertThatThrownBy(() -> DseReflectionUtil.getFieldValue("properties.'http://purl.org/dc/terms/title'", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Missing property key returns null")
        void missingPropertyKey_returnsNull() {
            Object value = DseReflectionUtil.getFieldValue("properties.'http://purl.org/dc/terms/doesNotExist'", dataset1);

            assertThat(value).isNull();
        }

        @Test
        @DisplayName("Non-existent field on the Dataset object throws ReflectionException")
        void nonExistentFieldOnObject_throwsReflectionException() {
            assertThatThrownBy(() -> DseReflectionUtil.getFieldValue("nonExistentField", dataset1))
                    .isInstanceOf(ReflectionException.class);
        }

        @Test
        @DisplayName("Array indexer out of bounds throws IndexOutOfBoundsException")
        void arrayIndexerOnNonList_throws() {
            assertThatThrownBy(() -> DseReflectionUtil.getFieldValue("properties.'http://www.w3.org/ns/dcat#hasVersion'[99]", dataset1))
                    .isInstanceOf(IndexOutOfBoundsException.class);
        }

        @Test
        @DisplayName("Array indexer: properties.'dcat#hasVersion'[0].'@id' resolves the first version reference by index")
        void arrayIndexer_resolvesElementThenNestedPath() {
            Object value = DseReflectionUtil.getFieldValue(
                    "properties.'http://www.w3.org/ns/dcat#hasVersion'[0].'@id'", dataset1);

            assertThat(value).isEqualTo("urn:amadeus:platform:content-management:nevio:content-definitions:order-created-event:versions:v1.0.0");
        }

        @Test
        @DisplayName("Null intermediate path segment short-circuits to null instead of traversing further")
        void nullIntermediatePathSegment_shortCircuitsToNull() {
            Object value = DseReflectionUtil.getFieldValue(
                    "properties.'http://purl.org/dc/terms/doesNotExist'.'http://purl.org/dc/terms/deeper'", dataset1);

            assertThat(value).isNull();
        }

        @Test
        @DisplayName("List-of-lists: resolveElement's List branch is exercised via a minimal hand-built structure")
        void listOfLists_resolvesElementRecursively() {
            // dcat:themeTaxonomy is never a list-of-lists in the fixture; this minimal structure
            // covers resolveElement's `element instanceof List` branch directly.
            var innerList = List.<Object>of(Map.of("@value", "first"), Map.of("@value", "second"));
            var data = Map.<String, Object>of("outer", List.of(innerList));

            Object value = DseReflectionUtil.getFieldValue("outer.@value", data);

            assertThat(value).isInstanceOf(List.class);
            assertThat((List<String>) value).containsExactly("first", "second");
        }
    }
}
