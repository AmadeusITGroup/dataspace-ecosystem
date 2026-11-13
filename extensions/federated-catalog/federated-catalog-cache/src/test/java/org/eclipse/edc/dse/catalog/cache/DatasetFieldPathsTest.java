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

import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.util.reflection.ReflectionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasetFieldPathsTest {

    @ParameterizedTest
    @CsvSource({
            "datasets.id, id",
            "datasets.properties.x, properties.x",
            "id, id",
            "mydatasets.id, mydatasets.id",
            "properties.datasets.x, properties.datasets.x"
    })
    void toDatasetPath_removesOnlyLeadingDatasetsPrefix(String queryPath, String datasetPath) {
        assertThat(DatasetFieldPaths.toDatasetPath(queryPath)).isEqualTo(datasetPath);
    }

    @Test
    void sortValue_returnsTheValue_nullForMissingMapKey() {
        var dataset = Dataset.Builder.newInstance().id("ds-1").properties(Map.of("name", "a")).build();

        assertThat(DatasetFieldPaths.sortValue("id", dataset)).isEqualTo("ds-1");
        assertThat(DatasetFieldPaths.sortValue("properties.name", dataset)).isEqualTo("a");
        assertThat(DatasetFieldPaths.sortValue("properties.missing", dataset)).isNull();
    }

    @Test
    void sortValue_unknownField_isIllegalArgument() {
        var dataset = Dataset.Builder.newInstance().id("ds-1").build();

        assertThatThrownBy(() -> DatasetFieldPaths.sortValue("doesNotExist", dataset))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot sort by 'doesNotExist': field does not exist in Dataset")
                .hasCauseInstanceOf(ReflectionException.class);
    }

    @Test
    void existsInDataset() {
        assertThat(DatasetFieldPaths.existsInDataset("id")).isTrue();
        assertThat(DatasetFieldPaths.existsInDataset("properties.'http://purl.org/dc/terms/title'")).isTrue();
        assertThat(DatasetFieldPaths.existsInDataset("distributions[3]")).isTrue();
        assertThat(DatasetFieldPaths.existsInDataset("distributions.format")).isTrue();
        assertThat(DatasetFieldPaths.existsInDataset("distributions.doesNotExist")).isFalse();
        assertThat(DatasetFieldPaths.existsInDataset("properties[0]")).isFalse();
        assertThat(DatasetFieldPaths.existsInDataset("doesNotExist")).isFalse();
        assertThat(DatasetFieldPaths.existsInDataset("")).isFalse();
    }
}
