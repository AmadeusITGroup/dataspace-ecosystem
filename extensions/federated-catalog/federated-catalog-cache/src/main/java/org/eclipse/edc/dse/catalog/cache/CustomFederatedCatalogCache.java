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

import org.eclipse.edc.catalog.spi.CatalogConstants;
import org.eclipse.edc.catalog.spi.FederatedCatalogCache;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.spi.query.QueryResolver;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.util.concurrency.LockManager;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory implementation of {@link FederatedCatalogCache} that replaces EDC's default
 * {@code InMemoryFederatedCatalogCache}.
 *
 * <p>The key difference is that query resolution is delegated to {@link DatasetAwareQueryResolver}
 * instead of {@code ReflectionBasedQueryResolver}. This allows filter expressions that target
 * nested dataset properties (e.g. {@code datasets.properties.dcterms:title}) to work correctly,
 * since {@link DatasetAwareQueryResolver} uses {@link org.eclipse.edc.dse.common.lib.DsePropertyLookup} registered in the
 * {@link org.eclipse.edc.spi.query.CriterionOperatorRegistry}, which in turn uses
 * {@link org.eclipse.edc.dse.common.lib.DseReflectionUtil} for path navigation.
 *
 * <p>All write operations ({@link #save}, {@link #expireAll}, {@link #deleteExpired}) are
 * guarded by a write lock. {@link #query} uses a read lock for concurrency safety.
 */
public class CustomFederatedCatalogCache implements FederatedCatalogCache {

    private final Map<String, MarkableEntry<Catalog>> cache = new ConcurrentHashMap<>();
    private final LockManager lockManager;
    private final QueryResolver<Catalog> queryResolver;

    public CustomFederatedCatalogCache(LockManager lockManager, QueryResolver<Catalog> queryResolver) {
        this.lockManager = lockManager;
        this.queryResolver = queryResolver;
    }

    @Override
    public void save(Catalog catalog) {
        lockManager.writeLock(() -> {
            var id = Optional.ofNullable(catalog.getProperties().get(CatalogConstants.PROPERTY_ORIGINATOR))
                    .map(Object::toString)
                    .orElse(catalog.getId());
            cache.put(id, new MarkableEntry<>(false, catalog));
            return null;
        });
    }

    @Override
    public Collection<Catalog> query(QuerySpec query) {
        return lockManager.readLock(() -> {
            var catalogs = cache.values().stream().map(MarkableEntry::getEntry);
            return queryResolver.query(catalogs, query).toList();
        });
    }

    @Override
    public void deleteExpired() {
        lockManager.writeLock(() -> {
            cache.values().removeIf(MarkableEntry::isMarked);
            return null;
        });
    }

    @Override
    public void expireAll() {
        lockManager.writeLock(() -> {
            cache.replaceAll((k, v) -> new MarkableEntry<>(true, v.getEntry()));
            return null;
        });
    }

    private static class MarkableEntry<B> {
        private final B entry;
        private final boolean marked;

        MarkableEntry(boolean marked, B entry) {
            this.marked = marked;
            this.entry = entry;
        }

        boolean isMarked() {
            return marked;
        }

        B getEntry() {
            return entry;
        }
    }
}
