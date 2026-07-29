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
import org.eclipse.edc.util.concurrency.LockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.assertj.core.api.Assertions.assertThat;

class CustomFederatedCatalogCacheTest {

    private CustomFederatedCatalogCache cache;

    @BeforeEach
    void setUp() {
        var registry = CriterionOperatorRegistryImpl.ofDefaults();
        registry.registerPropertyLookup(new DsePropertyLookup());
        var resolver = new DatasetAwareQueryResolver(registry);
        cache = new CustomFederatedCatalogCache(new LockManager(new ReentrantReadWriteLock()), resolver);
    }

    @Nested
    @DisplayName("save and query")
    class SaveAndQuery {

        @Test
        @DisplayName("saved catalog is returned by query with no filter")
        void save_thenQuery_returnsAll() {
            cache.save(catalog("cat-1", "provider-1"));
            cache.save(catalog("cat-2", "provider-2"));

            var result = cache.query(QuerySpec.none());

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("overwrite: saving a catalog with the same id replaces the previous entry")
        void save_sameId_overwrites() {
            var original = catalog("cat-1", "provider-1");
            var updated = catalog("cat-1", "provider-updated");
            cache.save(original);
            cache.save(updated);

            var result = cache.query(QuerySpec.none());

            assertThat(result).hasSize(1)
                    .extracting(Catalog::getParticipantId)
                    .containsExactly("provider-updated");
        }

        @Test
        @DisplayName("query filters datasets within catalogs and drops empty catalogs")
        void query_withFilter_returnMatchingDatasetsOnly() {
            cache.save(catalogWithDataset("cat-a", "provider-a", "dcterms:title", "Aviation"));
            cache.save(catalogWithDataset("cat-b", "provider-b", "dcterms:title", "Railway"));

            var spec = QuerySpec.Builder.newInstance()
                    .filter(new Criterion("properties.dcterms:title", "=", "Aviation"))
                    .build();

            var result = cache.query(spec);

            assertThat(result).hasSize(1);
            assertThat(result.iterator().next().getId()).isEqualTo("cat-a");
            assertThat(result.iterator().next().getDatasets())
                    .extracting(Dataset::getId)
                    .containsExactly("ds-cat-a");
        }

        @Test
        @DisplayName("sort by dataset property orders datasets across catalogs")
        void query_sortByDatasetProperty_ordersAcrossCatalogs() {
            cache.save(catalogWithDataset("cat-b", "provider-b", "dcterms:title", "Zebra"));
            cache.save(catalogWithDataset("cat-a", "provider-a", "dcterms:title", "Apple"));

            var spec = QuerySpec.Builder.newInstance()
                    .sortField("properties.dcterms:title")
                    .sortOrder(SortOrder.ASC)
                    .build();

            var result = cache.query(spec);

            assertThat(result)
                    .extracting(Catalog::getId)
                    .containsExactly("cat-a", "cat-b");  // Apple < Zebra
        }

        @Test
        @DisplayName("pagination at dataset level: limit=1 returns exactly one dataset")
        void query_limitOne_returnsOneDataset() {
            // Two datasets in total (one per catalog)
            cache.save(catalogWithDataset("cat-a", "provider-a", "dcterms:title", "Alpha"));
            cache.save(catalogWithDataset("cat-b", "provider-b", "dcterms:title", "Beta"));

            var spec = QuerySpec.Builder.newInstance().limit(1).build();
            var result = cache.query(spec);

            var totalDatasets = result.stream().flatMap(c -> c.getDatasets().stream()).count();
            assertThat(totalDatasets).isEqualTo(1);
        }

        @Test
        @DisplayName("empty cache returns empty collection")
        void query_emptyCache_returnsEmpty() {
            assertThat(cache.query(QuerySpec.none())).isEmpty();
        }
    }

    @Nested
    @DisplayName("expiry lifecycle")
    class Expiry {

        @Test
        @DisplayName("expireAll marks all entries; deleteExpired removes them")
        void expireAll_thenDeleteExpired_removesAll() {
            cache.save(catalog("cat-1", "p1"));
            cache.save(catalog("cat-2", "p2"));

            cache.expireAll();
            cache.deleteExpired();

            assertThat(cache.query(QuerySpec.none())).isEmpty();
        }

        @Test
        @DisplayName("save after expireAll keeps new entry after deleteExpired")
        void expireAll_saveNew_thenDeleteExpired_keepsNew() {
            cache.save(catalog("old", "p-old"));
            cache.expireAll();
            cache.save(catalog("new", "p-new"));   // saved after expireAll — not marked
            cache.deleteExpired();

            assertThat(cache.query(QuerySpec.none()))
                    .hasSize(1)
                    .extracting(Catalog::getId)
                    .containsExactly("new");
        }

        @Test
        @DisplayName("deleteExpired without expireAll does not remove any entry")
        void deleteExpired_withoutExpireAll_removesNothing() {
            cache.save(catalog("cat-1", "p1"));
            cache.deleteExpired();

            assertThat(cache.query(QuerySpec.none())).hasSize(1);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Catalog catalog(String id, String participantId) {
        // Include a placeholder dataset so the new dataset-level flatten logic produces entries.
        var ds = Dataset.Builder.newInstance().id("ds-" + id).property("key", id).build();
        return Catalog.Builder.newInstance().id(id).participantId(participantId).dataset(ds).build();
    }

    private static Catalog catalogWithDataset(String catalogId, String participantId,
                                              String propertyKey, String propertyValue) {
        var dataset = Dataset.Builder.newInstance()
                .id("ds-" + catalogId)
                .property(propertyKey, propertyValue)
                .build();
        return Catalog.Builder.newInstance()
                .id(catalogId)
                .participantId(participantId)
                .dataset(dataset)
                .build();
    }
}
