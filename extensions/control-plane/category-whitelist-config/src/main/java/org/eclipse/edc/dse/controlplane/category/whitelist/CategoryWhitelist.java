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

package org.eclipse.edc.dse.controlplane.category.whitelist;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory holder for the shared category whitelist loaded once at boot from the
 * ConfigMap-backed file. Preserves the file's declared order and exposes read-only lookups by
 * {@code id} and by {@code internalId}.
 *
 * <p>The constructor validates its input, so an instance is always a well-formed tree: at least
 * one entry, no {@code null} entries, non-blank {@code id} and {@code internalId}, no duplicate
 * {@code id} or {@code internalId}, every {@code parentId} referencing an {@code id} in the list,
 * and no category that is its own ancestor (self-parent or cycle). Violations throw
 * {@link IllegalArgumentException}.
 */
public class CategoryWhitelist {

    private final List<WhitelistedCategory> categories;
    private final Map<String, WhitelistedCategory> byId;
    private final Map<String, WhitelistedCategory> byInternalId;

    public CategoryWhitelist(List<WhitelistedCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            throw new IllegalArgumentException("contains no categories");
        }
        var idMap = new LinkedHashMap<String, WhitelistedCategory>();
        var internalIdMap = new LinkedHashMap<String, WhitelistedCategory>();
        for (int i = 0; i < categories.size(); i++) {
            var category = categories.get(i);
            if (category == null) {
                throw new IllegalArgumentException("has a null entry at index " + i);
            }
            if (category.id() == null || category.id().isBlank()) {
                throw new IllegalArgumentException("has an entry with no id at index " + i);
            }
            if (category.internalId() == null || category.internalId().isBlank()) {
                throw new IllegalArgumentException("entry '" + category.id() + "' has no internalId");
            }
            if (idMap.putIfAbsent(category.id(), category) != null) {
                throw new IllegalArgumentException("has duplicate id '" + category.id() + "'");
            }
            if (internalIdMap.putIfAbsent(category.internalId(), category) != null) {
                throw new IllegalArgumentException("has duplicate internalId '" + category.internalId() + "'");
            }
        }
        for (var category : categories) {
            validateAncestry(category, idMap);
        }
        this.categories = List.copyOf(categories);
        this.byId = Map.copyOf(idMap);
        this.byInternalId = Map.copyOf(internalIdMap);
    }

    /**
     * Walks up the parent chain of {@code category}: every parent must exist, and the walk must
     * reach a top-level category without visiting any category twice (self-parent or cycle).
     */
    private static void validateAncestry(WhitelistedCategory category, Map<String, WhitelistedCategory> byId) {
        if (category.id().equals(category.parentId())) {
            throw new IllegalArgumentException("entry '" + category.id() + "' is its own parent");
        }
        var visited = new HashSet<String>();
        var current = category;
        while (current.parentId() != null) {
            if (!visited.add(current.id())) {
                throw new IllegalArgumentException("entry '" + category.id() + "' is part of a parentId cycle");
            }
            var parent = byId.get(current.parentId());
            if (parent == null) {
                throw new IllegalArgumentException("entry '" + current.id() +
                        "' references unknown parentId '" + current.parentId() + "'");
            }
            current = parent;
        }
    }

    /**
     * All whitelisted categories, in the order declared in the file.
     *
     * @return an unmodifiable, ordered view of the whitelist
     */
    public Collection<WhitelistedCategory> getAll() {
        return categories;
    }

    /**
     * Number of whitelisted categories.
     *
     * @return the entry count
     */
    public int size() {
        return categories.size();
    }

    public Optional<WhitelistedCategory> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<WhitelistedCategory> findByInternalId(String internalId) {
        return Optional.ofNullable(byInternalId.get(internalId));
    }

    public boolean isWhitelisted(String internalId) {
        return byInternalId.containsKey(internalId);
    }
}
