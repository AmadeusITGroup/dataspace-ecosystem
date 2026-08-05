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

import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("resolves direct object fields")
    void getProperty_directField() {
        var dataset = Dataset.Builder.newInstance().id("ds-1").build();

        assertThat(lookup.getProperty("id", dataset)).isEqualTo("ds-1");
    }

    @Test
    @DisplayName("resolves nested dataset properties through catalog")
    void getProperty_nestedPath() {
        var dataset = Dataset.Builder.newInstance()
                .id("ds-1")
                .property("dcterms:title", "Aviation Data")
                .build();
        var catalog = Catalog.Builder.newInstance().id("cat-1").dataset(dataset).build();

        var result = lookup.getProperty("datasets.properties.dcterms:title", catalog);

        assertThat(result).isInstanceOf(List.class);
        assertThat((List<String>) result).containsExactly("Aviation Data");
    }

    @Test
    @DisplayName("returns null when path cannot be resolved")
    void getProperty_unknownPath() {
        var catalog = Catalog.Builder.newInstance().id("cat-1").build();

        assertThat(lookup.getProperty("unknown.path", catalog)).isNull();
    }

    @Test
    @DisplayName("returns null silently for reflectively inaccessible field instead of throwing")
    void getProperty_reflectionFailure_returnsNull() {
        // A URI-style key will fail reflective lookup on a non-map object; must return null
        var dataset = Dataset.Builder.newInstance().id("ds-1").build();
        assertThat(lookup.getProperty("http://purl.org/dc/terms/nonexistent", dataset)).isNull();
    }
    void getProperty_unwrapsAtValue() {
        var dataset = Dataset.Builder.newInstance()
                .id("ds-1")
                .property("dcterms:description", List.of(Map.of("@value", "description")))
                .build();
        var catalog = Catalog.Builder.newInstance().id("cat-1").dataset(dataset).build();

        var result = lookup.getProperty("datasets.properties.dcterms:description", catalog);

        assertThat(result).isInstanceOf(List.class);
        assertThat((List<String>) result).contains("description");
    }
}
