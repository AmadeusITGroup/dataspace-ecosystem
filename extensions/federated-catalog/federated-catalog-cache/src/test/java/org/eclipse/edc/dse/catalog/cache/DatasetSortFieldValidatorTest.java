/*
 *  Copyright (c) 2026 Amadeus IT Group
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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.catalog.spi.DataService;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.connector.controlplane.catalog.spi.Distribution;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.SortOrder;
import org.eclipse.edc.validator.spi.Violation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.mockito.Mockito.mock;

class DatasetSortFieldValidatorTest {

    private final DatasetSortFieldValidator validator = new DatasetSortFieldValidator(new TitaniumJsonLd(mock(Monitor.class)));

    @Test
    void validate_unknownField_isSortFieldViolationWithSortMessage() {
        var result = validator.validate(querySpec("doesNotExist"));

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailure().getViolations()).containsExactly(
                new Violation("Cannot sort by 'doesNotExist': field does not exist in Dataset", "sortField", "doesNotExist"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "properties", "offers", "distributions"})
    void validate_fieldOfDataset_isValid(String sortField) {
        assertThat(validator.validate(querySpec(sortField)).succeeded()).isTrue();
    }

    @Test
    void validate_datasetsPrefix_isRemovedAsInTheSort() {
        assertThat(validator.validate(querySpec("datasets.id")).succeeded()).isTrue();
        assertThat(validator.validate(querySpec("datasets.properties.'http://purl.org/dc/terms/title'")).succeeded()).isTrue();

        var result = validator.validate(querySpec("datasets.doesNotExist"));

        // the message names the dataset path, as the sort does; the violation keeps the value that was sent
        assertThat(result.getFailure().getViolations()).containsExactly(
                new Violation("Cannot sort by 'doesNotExist': field does not exist in Dataset", "sortField", "datasets.doesNotExist"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "properties.'http://purl.org/dc/terms/title'",
            "properties.'https://w3id.org/edc/v0.0.1/ns/name'",
            "properties.missingKey",
            "properties.missingKey.nested",
            "offers.missingOfferId"
    })
    void validate_missingMapKey_isValid(String sortField) {
        assertThat(validator.validate(querySpec(sortField)).succeeded()).isTrue();
    }

    @Test
    void validate_unknownFieldBelowDatasetField_isViolation() {
        // id is a String, which has no field 'doesNotExist'
        var result = validator.validate(querySpec("id.doesNotExist"));

        assertThat(result.getFailure().getViolations()).extracting(Violation::message)
                .containsExactly("Cannot sort by 'id.doesNotExist': field does not exist in Dataset");
    }

    @ParameterizedTest
    @ValueSource(strings = {"distributions.format", "distributions[0].format", "distributions.dataService.id", "distributions[3]"})
    void validate_fieldBelowTheDistributions_isValid(String sortField) {
        // checked on the declared element type Distribution; a missing list index depends on the values
        assertThat(validator.validate(querySpec(sortField)).succeeded()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"distributions.doesNotExist", "distributions[0].doesNotExist", "distributions.dataService.doesNotExist"})
    void validate_unknownFieldBelowTheDistributions_isViolation(String sortField) {
        var result = validator.validate(querySpec(sortField));

        assertThat(result.getFailure().getViolations()).extracting(Violation::message)
                .containsExactly("Cannot sort by '%s': field does not exist in Dataset".formatted(sortField));
    }

    @ParameterizedTest
    @ValueSource(strings = {"properties[0]", "offers[0]", "id[0]"})
    void validate_indexerOnNonListField_isViolation(String sortField) {
        assertThat(validator.validate(querySpec(sortField)).failed()).isTrue();
    }

    @Test
    void validate_sortFieldInEveryFormThatExpandsToTheEdcSortField_isChecked() {
        var prefixed = Json.createObjectBuilder()
                .add(CONTEXT, Json.createObjectBuilder().add("edc", EDC_NAMESPACE))
                .add(TYPE, "QuerySpec")
                .add("edc:sortField", "doesNotExist")
                .build();
        var fullIri = Json.createObjectBuilder()
                .add(TYPE, "QuerySpec")
                .add(EDC_NAMESPACE + "sortField", "doesNotExist")
                .build();

        assertThat(validator.validate(prefixed).failed()).isTrue();
        assertThat(validator.validate(fullIri).failed()).isTrue();
    }

    @Test
    void validate_sortFieldThatDoesNotExpandToTheEdcSortField_isNotChecked() {
        // without an EDC context the term is dropped by the expansion, so the query does not sort by it either
        var noContext = Json.createObjectBuilder().add(TYPE, "QuerySpec").add("sortField", "doesNotExist").build();

        assertThat(validator.validate(noContext).succeeded()).isTrue();
    }

    @Test
    void validate_withoutSortField_isValid() {
        var querySpec = Json.createObjectBuilder(querySpec("unused")).remove("sortField").build();

        assertThat(validator.validate(querySpec).succeeded()).isTrue();
    }

    @Test
    void validate_nonStringSortField_isNotChecked() {
        var querySpec = Json.createObjectBuilder(querySpec("unused")).add("sortField", 42).build();

        assertThat(validator.validate(querySpec).succeeded()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"doesNotExist", "id.doesNotExist", "@id", "https://w3id.org/edc/v0.0.1/ns/id",
            "distributions.doesNotExist", "distributions[0].doesNotExist"})
    void validate_rejectsWhatTheSortRejects_withTheSameMessage(String sortField) {
        var message = validator.validate(querySpec(sortField)).getFailure().getViolations().get(0).message();

        var comparator = new DatasetComparator(DatasetFieldPaths.toDatasetPath(sortField), SortOrder.ASC);
        var dataset1 = Dataset.Builder.newInstance().id("ds-1").properties(Map.of("name", "a")).distribution(distribution("a")).build();
        var dataset2 = Dataset.Builder.newInstance().id("ds-2").properties(Map.of("name", "b")).distribution(distribution("b")).build();
        assertThatThrownBy(() -> List.of(dataset1, dataset2).stream().sorted(comparator).toList())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }

    private static Distribution distribution(String format) {
        return Distribution.Builder.newInstance().format(format)
                .dataService(DataService.Builder.newInstance().id("ds-" + format).build()).build();
    }

    private static JsonObject querySpec(String sortField) {
        return Json.createObjectBuilder()
                .add(CONTEXT, Json.createObjectBuilder().add(VOCAB, EDC_NAMESPACE))
                .add(TYPE, "QuerySpec")
                .add("sortField", sortField)
                .add("sortOrder", "ASC")
                .build();
    }
}
