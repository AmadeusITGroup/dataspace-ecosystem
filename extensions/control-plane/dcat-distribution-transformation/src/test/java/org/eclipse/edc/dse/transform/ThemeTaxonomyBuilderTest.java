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
 */

package org.eclipse.edc.dse.transform;

import org.eclipse.edc.dse.controlplane.category.whitelist.WhitelistedCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ThemeTaxonomyBuilderTest {

    private final ThemeTaxonomyBuilder builder = new ThemeTaxonomyBuilder();

    @Test
    void build_nullInput_returnsEmptyScheme() {
        assertEmptyScheme(builder.build(null));
    }

    @Test
    void build_emptyInput_returnsEmptyScheme() {
        assertEmptyScheme(builder.build(List.of()));
    }

    private static void assertEmptyScheme(ThemeTaxonomyBuilder.ConceptScheme scheme) {
        assertThat(scheme.id()).isEqualTo(ThemeTaxonomyConstants.TAXONOMY_SCHEME_ID);
        assertThat(scheme.title()).isEqualTo(ThemeTaxonomyConstants.TAXONOMY_TITLE);
        assertThat(scheme.topConcepts()).isEmpty();
    }

    @Test
    void build_usesFixedSchemeIdAndTitle() {
        var categories = List.of(category("50", "Airline Order", null, "urn:category:50", null));

        var scheme = builder.build(categories);

        assertThat(scheme).isNotNull();
        assertThat(scheme.id()).isEqualTo(ThemeTaxonomyConstants.TAXONOMY_SCHEME_ID);
        assertThat(scheme.title()).isEqualTo(ThemeTaxonomyConstants.TAXONOMY_TITLE);
    }

    @Test
    void build_singleTopLevelCategory_mapsFieldsCorrectly() {
        var categories = List.of(
                category("50", "Airline Order", "Nevio Order Management System", "urn:category:50", null)
        );

        var scheme = builder.build(categories);

        assertThat(scheme.topConcepts()).hasSize(1);
        var concept = scheme.topConcepts().get(0);
        // internalId -> concept id (SKOS @id)
        assertThat(concept.id()).isEqualTo("urn:category:50");
        // name -> prefLabel
        assertThat(concept.prefLabel()).isEqualTo("Airline Order");
        // description -> definition
        assertThat(concept.definition()).isEqualTo("Nevio Order Management System");
        // id -> notation
        assertThat(concept.notation()).isEqualTo("50");
        assertThat(concept.narrower()).isEmpty();
    }

    @Test
    void build_narrowerCategory_nestedUnderParent() {
        var categories = List.of(
                category("50", "Airline Order", "Nevio OMS", "urn:category:50", null),
                category("990", "Order Management System", "List of order items", "urn:category:990", "50")
        );

        var scheme = builder.build(categories);

        assertThat(scheme.topConcepts()).hasSize(1);
        var top = scheme.topConcepts().get(0);
        assertThat(top.id()).isEqualTo("urn:category:50");
        assertThat(top.narrower()).hasSize(1);

        var narrower = top.narrower().get(0);
        assertThat(narrower.id()).isEqualTo("urn:category:990");
        assertThat(narrower.prefLabel()).isEqualTo("Order Management System");
        assertThat(narrower.definition()).isEqualTo("List of order items");
        assertThat(narrower.notation()).isEqualTo("990");
        assertThat(narrower.narrower()).isEmpty();
    }

    @Test
    void build_multiLevelHierarchy_nestsAllLevels() {
        var categories = List.of(
                category("1", "Level 1", null, "urn:cat:1", null),
                category("2", "Level 2", null, "urn:cat:2", "1"),
                category("3", "Level 3", null, "urn:cat:3", "2")
        );

        var scheme = builder.build(categories);

        var level1 = scheme.topConcepts().get(0);
        assertThat(level1.notation()).isEqualTo("1");
        var level2 = level1.narrower().get(0);
        assertThat(level2.notation()).isEqualTo("2");
        var level3 = level2.narrower().get(0);
        assertThat(level3.notation()).isEqualTo("3");
        assertThat(level3.narrower()).isEmpty();
    }

    @Test
    void build_multipleTopLevelCategories_allIncluded() {
        var categories = List.of(
                category("50", "Airline Order", null, "urn:category:50", null),
                category("60", "Booking Management", null, "urn:category:60", null)
        );

        var scheme = builder.build(categories);

        assertThat(scheme.topConcepts())
                .extracting(ThemeTaxonomyBuilder.Concept::notation)
                .containsExactlyInAnyOrder("50", "60");
    }

    @Test
    void build_multipleChildrenUnderSameParent_allNested() {
        var categories = List.of(
                category("50", "Parent", null, "urn:category:50", null),
                category("51", "Child A", null, "urn:category:51", "50"),
                category("52", "Child B", null, "urn:category:52", "50")
        );

        var scheme = builder.build(categories);

        var parent = scheme.topConcepts().get(0);
        assertThat(parent.narrower())
                .extracting(ThemeTaxonomyBuilder.Concept::notation)
                .containsExactlyInAnyOrder("51", "52");
    }

    @Test
    void build_duplicateIds_deduplicatesKeepingFirst() {
        var categories = List.of(
                category("50", "First", "first desc", "urn:category:50", null),
                category("50", "Second", "second desc", "urn:category:50-dup", null)
        );

        var scheme = builder.build(categories);

        assertThat(scheme.topConcepts()).hasSize(1);
        assertThat(scheme.topConcepts().get(0).prefLabel()).isEqualTo("First");
    }

    @Test
    void build_orphanedParentId_notLostAsOrphan() {
        // parentId references a category that does not exist in the whitelist - the orphan
        // should simply not have a parent to nest under, and is dropped from topConcepts since
        // its parentId is non-null (it is neither a top concept nor reachable as narrower).
        var categories = List.of(
                category("990", "Orphan", null, "urn:category:990", "nonexistent-parent")
        );

        var scheme = builder.build(categories);

        assertThat(scheme.topConcepts()).isEmpty();
    }

    private WhitelistedCategory category(String id, String name, String description, String internalId, String parentId) {
        return new WhitelistedCategory(id, name, description, internalId, parentId);
    }
}
