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

package org.eclipse.edc.dse.controlplane.query.asset;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.PropertyLookup;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads a full asset row from {@code /asset/asset-db-row.json} on the test classpath, shaped
 * exactly like the {@code asset} table as persisted in the database — {@code asset_id},
 * {@code created_at}, {@code properties}, {@code private_properties}, {@code data_address} and
 * {@code participant_context_id} columns — rather than just the bare {@code properties} map.
 *
 * <p>{@code properties} is a JSON-LD-expanded shape with a nested {@code dcat:theme} object
 * containing both a direct {@code dcterms:name} array and a deeper nested
 * {@code dcterms:sub.dcterms:name} array, each wrapping its value in a {@code {"@value": ...}}
 * JSON-LD value object.
 *
 * <p>Verifies that {@link DseAssetPropertyLookup#getProperty(String, Object)}, addressed with a
 * dotted, pre-quoted path and <b>no array indexer</b>, transparently unwraps every
 * {@code {"@value": ...}} wrapper — the resolved value is a plain {@link String} (or
 * {@code List<String>}), never a {@link Map} containing an {@code @value} key.
 */
class DseAssetPropertyLookupTest {

    private static Asset asset;

    private final PropertyLookup propertyLookup = new DseAssetPropertyLookup();

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void setUp() throws IOException {
        var objectMapper = new ObjectMapper();
        try (var inputStream = DseAssetPropertyLookupTest.class.getResourceAsStream("/asset/asset-db-row.json")) {
            assertThat(inputStream).as("/asset/asset-db-row.json not found on classpath").isNotNull();
            Map<String, Object> row = objectMapper.readValue(inputStream, new TypeReference<>() {
            });

            var properties = (Map<String, Object>) row.get("properties");
            var privateProperties = (Map<String, Object>) row.get("private_properties");
            var dataAddressProperties = (Map<String, Object>) row.get("data_address");

            asset = Asset.Builder.newInstance()
                    .id((String) row.get("asset_id"))
                    .createdAt(((Number) row.get("created_at")).longValue())
                    .properties(properties)
                    .privateProperties(privateProperties)
                    .dataAddress(DataAddress.Builder.newInstance().properties(dataAddressProperties).build())
                    .participantContextId((String) row.get("participant_context_id"))
                    .build();
        }
    }

    @Test
    @DisplayName("Top-level nested path: 'dcat#theme'.'dcterms#name' resolves to a plain value, not a map with @value")
    void topLevelNestedPath_unwrapsValueObject() {
        var path = "'http://www.w3.org/ns/dcat#theme'.'http://purl.org/dc/terms/name'";

        Object value = propertyLookup.getProperty(path, asset);

        assertThat(value).isNotNull();
        assertThat(asList(value)).containsExactly("order");
        assertDoesNotContainValueMap(value);
    }

    @Test
    @DisplayName("Deeper nested path: 'dcat#theme'.'dcterms#sub'.'dcterms#name' resolves without an array indexer")
    void deeperNestedPath_noArrayIndexer_unwrapsValueObject() {
        var path = "'http://www.w3.org/ns/dcat#theme'.'http://purl.org/dc/terms/sub'.'http://purl.org/dc/terms/name'";

        Object value = propertyLookup.getProperty(path, asset);

        assertThat(value).isNotNull();
        assertThat(asList(value)).containsExactly("airline");
        assertDoesNotContainValueMap(value);
    }

    @Test
    @DisplayName("A property that does not exist (e.g. 'dcat#views'.'dcterms#name') resolves to null, no exception")
    void nonExistentNestedPath_resolvesToNull() {
        var path = "'http://www.w3.org/ns/dcat#views'.'http://purl.org/dc/terms/name'";

        Object value = propertyLookup.getProperty(path, asset);

        assertThat(value).isNull();
    }

    @Test
    @DisplayName("Simple top-level string property resolves directly")
    void simpleTopLevelProperty_resolvesDirectly() {
        var value = propertyLookup.getProperty("'http://www.w3.org/ns/dcat#name'", asset);

        assertThat(value).isEqualTo("Bharath1");
    }

    @Test
    @DisplayName("Full Criterion predicate evaluation via CriterionOperatorRegistry matches the unwrapped value")
    void fullCriterionPredicateEvaluation_matchesUnwrappedValue() {
        var registry = CriterionOperatorRegistryImpl.ofDefaults();
        registry.registerPropertyLookup(propertyLookup);

        var criterion = Criterion.criterion(
                "'http://www.w3.org/ns/dcat#theme'.'http://purl.org/dc/terms/name'", "=", "order");

        var predicate = registry.<Asset>toPredicate(criterion);

        assertThat(predicate.test(asset)).isTrue();
    }

    @Test
    @DisplayName("data_address and participant_context_id columns are correctly reflected onto the built Asset")
    void otherRowColumns_areReflectedOntoAsset() {
        assertThat(asset.getId()).isEqualTo("hello-eonax-v4");
        assertThat(asset.getCreatedAt()).isEqualTo(1785420688317L);
        assertThat(asset.getPrivateProperties()).isEmpty();
        assertThat(asset.getDataAddress().getStringProperty("https://w3id.org/edc/v0.0.1/ns/type")).isEqualTo("HttpData");
        assertThat(asset.getDataAddress().getStringProperty("https://w3id.org/edc/v0.0.1/ns/baseUrl"))
                .isEqualTo("https://jsonplaceholder.typicode.com/");
        assertThat(asset.getParticipantContextId()).isEqualTo("did:web:provider-identityhub%3A8383:api:did");
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        return List.of(value);
    }

    private void assertDoesNotContainValueMap(Object value) {
        var values = asList(value);
        assertThat(values).allSatisfy(v -> {
            if (v instanceof Map<?, ?> map) {
                assertThat(map.containsKey("@value")).isFalse();
            }
        });
    }
}
