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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the skos:ConceptScheme tree (dcat:themeTaxonomy) from a list of whitelist categories.
 * The caller passes the categories to show, including their parents so the tree stays
 * connected. Categories without a parent become top concepts, and every other category is
 * nested under its parent.
 * <p>
 * Example: given the categories
 * <pre>
 *   Travel                  (no parent)
 *   Flights                 (parent: Travel)
 *   Flight Schedules        (parent: Flights)
 *   Hotels                  (no parent)
 * </pre>
 * the tree is
 * <pre>
 *   Travel
 *   └─ Flights
 *      └─ Flight Schedules
 *   Hotels
 * </pre>
 * Plain logic, no JSON-LD here - see {@link ThemeTaxonomyJsonLdWriter} for that part.
 * Each category has at most one parent, so every category has exactly one place in the tree.
 */
public final class ThemeTaxonomyBuilder {

    public ConceptScheme build(List<WhitelistedCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return new ConceptScheme(ThemeTaxonomyConstants.TAXONOMY_SCHEME_ID, ThemeTaxonomyConstants.TAXONOMY_TITLE, List.of());
        }

        // One entry per category id; the first one wins if an id repeats.
        var uniqueCategories = categories.stream()
                .collect(Collectors.toMap(WhitelistedCategory::id, category -> category, (first, second) -> first, LinkedHashMap::new))
                .values();

        // Children of each category, grouped once so building the tree doesn't rescan the list.
        var childrenByParentId = uniqueCategories.stream()
                .filter(category -> category.parentId() != null)
                .collect(Collectors.groupingBy(WhitelistedCategory::parentId, LinkedHashMap::new, Collectors.toList()));

        // Categories without a parent are the roots of the tree (skos:hasTopConcept).
        var treeRoots = uniqueCategories.stream()
                .filter(category -> category.parentId() == null)
                .toList();
        var topConcepts = treeRoots.stream()
                .map(root -> toConcept(root, childrenByParentId))
                .toList();

        return new ConceptScheme(ThemeTaxonomyConstants.TAXONOMY_SCHEME_ID, ThemeTaxonomyConstants.TAXONOMY_TITLE, topConcepts);
    }

    private static Concept toConcept(WhitelistedCategory category, Map<String, List<WhitelistedCategory>> childrenByParentId) {
        var childrenCategories = childrenByParentId.getOrDefault(category.id(), List.of());
        var childrenConcepts = childrenCategories.stream()
                .map(child -> toConcept(child, childrenByParentId))
                .toList();

        return new Concept(category.internalId(), category.name(), category.description(), category.id(), childrenConcepts);
    }

    /**
     * One category in the tree. {@code narrower} holds its child categories. The name comes
     * from skos:narrower, the SKOS term used for children in the output.
     */
    public record Concept(String id, String prefLabel, String definition, String notation, List<Concept> narrower) {
    }

    public record ConceptScheme(String id, String title, List<Concept> topConcepts) {
    }
}
