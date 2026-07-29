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
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.query.SortOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasetAwareQueryResolverTest {

    private DatasetAwareQueryResolver resolver;

    /**
     * catalog-a  (provider-a):  ds-a1 "Aviation Data" / subject "flights"
     *                           ds-a2 "Booking Events"
     * catalog-b  (provider-b):  ds-b1 "Flight Prices" / subject "flights"
     */
    private Catalog catalogA;
    private Catalog catalogB;

    @BeforeEach
    void setUp() {
        var registry = CriterionOperatorRegistryImpl.ofDefaults();
        registry.registerPropertyLookup(new DsePropertyLookup());
        resolver = new DatasetAwareQueryResolver(registry);

        catalogA = catalog("cat-a", "provider-a",
                dataset("ds-a1", Map.of("dcterms:title", "Aviation Data", "dcterms:subject", "flights")),
                dataset("ds-a2", Map.of("dcterms:title", "Booking Events")));

        catalogB = catalog("cat-b", "provider-b",
                dataset("ds-b1", Map.of("dcterms:title", "Flight Prices", "dcterms:subject", "flights")));
    }

    // ── No filters ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("No filters")
    class NoFilters {

        @Test
        @DisplayName("returns all catalogs (by id) when QuerySpec has no filter")
        void query_noFilter_returnsAll() {
            var result = resolver.query(Stream.of(catalogA, catalogB), QuerySpec.none()).toList();
            assertThat(result)
                    .extracting(Catalog::getId)
                    .containsExactlyInAnyOrder("cat-a", "cat-b");
        }

        @Test
        @DisplayName("returns empty list for empty stream")
        void query_emptyStream_returnsEmpty() {
            var result = resolver.query(Stream.empty(), QuerySpec.none()).toList();
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("catalog with no datasets is included when there is no filter")
        void query_catalogWithNoDatasets_isIncluded() {
            var emptyC = catalog("cat-empty", "provider-empty");   // no datasets
            var result = resolver.query(Stream.of(emptyC, catalogA), QuerySpec.none()).toList();
            assertThat(result)
                    .extracting(Catalog::getId)
                    .containsExactlyInAnyOrder("cat-empty", "cat-a");
        }
    }

    // ── Dataset-level filtering ──────────────────────────────────────────────

    @Nested
    @DisplayName("Filtering by dataset property")
    class FilterByDatasetProperty {

        @Test
        @DisplayName("exact match returns only the catalog that contains the matching dataset")
        void query_filterByTitle_exact_returnsMatchingCatalog() {
            var spec = specWithFilter("datasets.properties.dcterms:title", "=", "Aviation Data");

            var result = resolver.query(Stream.of(catalogA, catalogB), spec).toList();

            assertThat(result)
                    .extracting(Catalog::getId)
                    .containsExactly("cat-a");
        }

        @Test
        @DisplayName("only the matching dataset is kept inside the returned catalog")
        void query_filterByTitle_stripsNonMatchingDatasetsFromCatalog() {
            // catalogA has ds-a1 ("Aviation Data") and ds-a2 ("Booking Events")
            var spec = specWithFilter("datasets.properties.dcterms:title", "=", "Aviation Data");

            var result = resolver.query(Stream.of(catalogA, catalogB), spec).toList();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDatasets())
                    .extracting(Dataset::getId)
                    .containsExactly("ds-a1");           // ds-a2 must NOT be present
        }

        @Test
        @DisplayName("filter with no match returns empty")
        void query_filterByTitle_noMatch_returnsEmpty() {
            var spec = specWithFilter("datasets.properties.dcterms:title", "=", "Unknown");

            assertThat(resolver.query(Stream.of(catalogA, catalogB), spec)).isEmpty();
        }

        @Test
        @DisplayName("filter on shared property returns both catalogs, each with only the matching dataset")
        void query_filterBySharedSubject_returnsBothCatalogs() {
            var spec = specWithFilter("datasets.properties.dcterms:subject", "=", "flights");

            var result = resolver.query(Stream.of(catalogA, catalogB), spec).toList();

            assertThat(result)
                    .extracting(Catalog::getId)
                    .containsExactlyInAnyOrder("cat-a", "cat-b");
            // cat-a must contain only ds-a1 (has subject "flights"); ds-a2 must be stripped
            var catA = result.stream().filter(c -> c.getId().equals("cat-a")).findFirst().orElseThrow();
            assertThat(catA.getDatasets())
                    .extracting(Dataset::getId)
                    .containsExactly("ds-a1");
        }

        @Test
        @DisplayName("LIKE filter matches on partial title")
        void query_likeFilter_onDatasetTitle() {
            var spec = specWithFilter("datasets.properties.dcterms:title", "like", "%Data%");

            var result = resolver.query(Stream.of(catalogA, catalogB), spec).toList();

            assertThat(result)
                    .extracting(Catalog::getId)
                    .containsExactly("cat-a");
        }

        @Test
        @DisplayName("short form path (without 'datasets.' prefix) works identically")
        void query_shortFormPath_worksLikeLongForm() {
            var specLong  = specWithFilter("datasets.properties.dcterms:title", "=", "Flight Prices");
            var specShort = specWithFilter("properties.dcterms:title",          "=", "Flight Prices");

            var longResult  = resolver.query(Stream.of(catalogA, catalogB), specLong).toList();
            var shortResult = resolver.query(Stream.of(catalogA, catalogB), specShort).toList();

            assertThat(shortResult)
                    .extracting(Catalog::getId)
                    .isEqualTo(longResult.stream().map(Catalog::getId).toList());
        }

        @Test
        @DisplayName("AND of two criteria: both must match on the same dataset")
        void query_andCriteria_bothMustMatch() {
            // ds-a1 has both title "Aviation Data" and subject "flights" → matches
            // ds-b1 has subject "flights" but title "Flight Prices" → filtered out by title criterion
            var spec = QuerySpec.Builder.newInstance()
                    .filter(criterion("properties.dcterms:title",   "=", "Aviation Data"))
                    .filter(criterion("properties.dcterms:subject",  "=", "flights"))
                    .build();

            var result = resolver.query(Stream.of(catalogA, catalogB), spec).toList();

            assertThat(result)
                    .extracting(Catalog::getId)
                    .containsExactly("cat-a");
            assertThat(result.get(0).getDatasets())
                    .extracting(Dataset::getId)
                    .containsExactly("ds-a1");
        }
    }

    // ── Sorting (dataset-level) ──────────────────────────────────────────────

    @Nested
    @DisplayName("Sorting at dataset level")
    class Sorting {

        @Test
        @DisplayName("sort ASC by dataset title: datasets interleaved across catalogs in alphabetical order")
        void query_sortAsc_byDatasetTitle() {
            // ds-a1 "Aviation Data", ds-a2 "Booking Events", ds-b1 "Flight Prices"
            // ASC order: Aviation, Booking, Flight
            var spec = QuerySpec.Builder.newInstance()
                    .sortField("properties.dcterms:title")
                    .sortOrder(SortOrder.ASC)
                    .build();

            var result = resolver.query(Stream.of(catalogA, catalogB), spec).toList();
            var datasetIds = result.stream()
                    .flatMap(c -> c.getDatasets().stream())
                    .map(Dataset::getId)
                    .toList();

            assertThat(datasetIds).containsExactly("ds-a1", "ds-a2", "ds-b1");
        }

        @Test
        @DisplayName("sort DESC by dataset title reverses the order")
        void query_sortDesc_byDatasetTitle() {
            var spec = QuerySpec.Builder.newInstance()
                    .sortField("properties.dcterms:title")
                    .sortOrder(SortOrder.DESC)
                    .build();

            var result = resolver.query(Stream.of(catalogA, catalogB), spec).toList();
            var datasetIds = result.stream()
                    .flatMap(c -> c.getDatasets().stream())
                    .map(Dataset::getId)
                    .toList();

            assertThat(datasetIds).containsExactly("ds-b1", "ds-a2", "ds-a1");
        }

        @Test
        @DisplayName("sort by non-existent field throws IllegalArgumentException")
        void query_sortByNonExistentField_throwsException() {
            var spec = QuerySpec.Builder.newInstance()
                    .sortField("nonExistentField")
                    .sortOrder(SortOrder.ASC)
                    .build();

            // Two datasets are required so the comparator is actually invoked
            assertThatThrownBy(() -> resolver.query(Stream.of(catalogA, catalogB), spec).toList())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nonExistentField");
        }

        @Test
        @DisplayName("sort changes which catalog appears first in the response")
        void query_sort_affectsCatalogOrder() {
            // Without sort, catalogA comes first (it has 2 datasets; catB only 1)
            // With ASC sort by title: Aviation < Booking < Flight
            // → CatA{ds-a1, ds-a2} then CatB{ds-b1}  (same order by accident, but driven by dataset sort)
            // With DESC: Flight > Booking > Aviation
            // → CatB{ds-b1} then CatA{ds-a2, ds-a1}
            var spec = QuerySpec.Builder.newInstance()
                    .sortField("properties.dcterms:title")
                    .sortOrder(SortOrder.DESC)
                    .build();

            var result = resolver.query(Stream.of(catalogA, catalogB), spec).toList();

            assertThat(result.get(0).getId()).isEqualTo("cat-b");   // "Flight Prices" first
            assertThat(result.get(1).getId()).isEqualTo("cat-a");
        }
    }

    // ── Pagination (dataset-level) ───────────────────────────────────────────

    @Nested
    @DisplayName("Pagination at dataset level")
    class Pagination {

        // catalogA: 2 datasets, catalogB: 1 dataset → total 3 datasets
        // Pagination tests always use a filter so the dataset-level flatten path is taken.
        private static final Criterion MATCH_ALL = new Criterion("id", "!=", "__no_match__");

        @Test
        @DisplayName("limit caps the number of datasets returned across all catalogs")
        void query_limit_capsDatasets() {
            var spec = QuerySpec.Builder.newInstance().filter(MATCH_ALL).limit(2).build();

            var totalDatasets = resolver.query(Stream.of(catalogA, catalogB), spec)
                    .flatMap(c -> c.getDatasets().stream())
                    .count();

            assertThat(totalDatasets).isEqualTo(2);
        }

        @Test
        @DisplayName("offset skips leading datasets")
        void query_offset_skipsDatasets() {
            var spec = QuerySpec.Builder.newInstance().filter(MATCH_ALL).offset(2).build();

            var totalDatasets = resolver.query(Stream.of(catalogA, catalogB), spec)
                    .flatMap(c -> c.getDatasets().stream())
                    .count();

            assertThat(totalDatasets).isEqualTo(1);   // 3 total - 2 skipped = 1
        }

        @Test
        @DisplayName("offset beyond total dataset count returns empty")
        void query_offsetBeyondTotal_returnsEmpty() {
            var spec = QuerySpec.Builder.newInstance().filter(MATCH_ALL).offset(10).build();
            assertThat(resolver.query(Stream.of(catalogA, catalogB), spec)).isEmpty();
        }

        @Test
        @DisplayName("a single catalog can be split across two pages")
        void query_pagination_catalogSplitAcrossPages() {
            var firstPage  = QuerySpec.Builder.newInstance().filter(MATCH_ALL).limit(1).offset(0).build();
            var secondPage = QuerySpec.Builder.newInstance().filter(MATCH_ALL).limit(1).offset(1).build();

            var firstDatasets  = resolver.query(Stream.of(catalogA), firstPage)
                    .flatMap(c -> c.getDatasets().stream()).map(Dataset::getId).toList();
            var secondDatasets = resolver.query(Stream.of(catalogA), secondPage)
                    .flatMap(c -> c.getDatasets().stream()).map(Dataset::getId).toList();

            assertThat(firstDatasets).hasSize(1);
            assertThat(secondDatasets).hasSize(1);
            assertThat(firstDatasets).doesNotContainAnyElementsOf(secondDatasets);
        }

        @Test
        @DisplayName("a single page can contain datasets from multiple catalogs")
        void query_pagination_pageMixesCatalogs() {
            var spec = QuerySpec.Builder.newInstance().filter(MATCH_ALL).offset(1).limit(2).build();

            var catalogIds = resolver.query(Stream.of(catalogA, catalogB), spec)
                    .map(Catalog::getId)
                    .toList();

            assertThat(catalogIds).containsExactlyInAnyOrder("cat-a", "cat-b");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Catalog catalog(String id, String participantId, Dataset... datasets) {
        var builder = Catalog.Builder.newInstance().id(id).participantId(participantId);
        for (var ds : datasets) {
            builder.dataset(ds);
        }
        return builder.build();
    }

    private static Dataset dataset(String id, Map<String, Object> properties) {
        var builder = Dataset.Builder.newInstance().id(id);
        properties.forEach(builder::property);
        return builder.build();
    }

    private static QuerySpec specWithFilter(String left, String op, Object right) {
        return QuerySpec.Builder.newInstance().filter(criterion(left, op, right)).build();
    }

    private static Criterion criterion(String left, String operator, Object right) {
        return new Criterion(left, operator, right);
    }
}
