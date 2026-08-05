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
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.query.QueryResolver;
import org.eclipse.edc.spi.query.QuerySpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link QueryResolver} for {@link Catalog} objects that applies filter, sort, and
 * pagination at dataset level across all catalogs.
 *
 * <p>The full {@link QuerySpec} lifecycle is applied in order:
 * <ol>
 *   <li>Filter — each {@link Criterion} is evaluated against individual {@link Dataset}
 *       objects. Only matching datasets are kept; catalogs with no matching datasets
 *       are dropped.</li>
 *   <li>Sort — datasets are sorted across all catalogs using {@link DatasetComparator}.
 *       Sentinel entries (empty-catalog placeholders with {@code null} dataset) follow
 *       the comparator contract explicitly: return {@code 0} when both sides are sentinels,
 *       {@code 1} when only the left side is a sentinel, and {@code -1} when only the
 *       right side is a sentinel.</li>
 *   <li>Paginate — {@code offset} and {@code limit} are applied to the flat dataset list.</li>
 *   <li>Regroup — paginated datasets are grouped back into their source catalogs.</li>
 * </ol>
 */
public class DatasetAwareQueryResolver implements QueryResolver<Catalog> {

    private static final String DATASETS_PREFIX = "datasets.";

    private final CriterionOperatorRegistry criterionOperatorRegistry;

    public DatasetAwareQueryResolver(CriterionOperatorRegistry criterionOperatorRegistry) {
        this.criterionOperatorRegistry = criterionOperatorRegistry;
    }

    @Override
    public Stream<Catalog> query(Stream<Catalog> stream, QuerySpec spec) {
        var criteria = spec.getFilterExpression();
        var allFilteredEntries = flattenFilteredEntries(stream, criteria);
        sortEntries(allFilteredEntries, spec);

        var paginatedEntries = paginateEntries(allFilteredEntries, spec);
        var sentinelCatalogs = findSentinelCatalogs(allFilteredEntries, paginatedEntries);

        return regroupEntries(paginatedEntries, sentinelCatalogs);
    }

    private List<DatasetEntry> flattenFilteredEntries(Stream<Catalog> stream, List<Criterion> criteria) {
        // Empty catalogs get a sentinel to survive flattening; non-matching catalogs are dropped.
        Stream<Catalog> filtered = stream
                .map(c -> c.getDatasets() == null || c.getDatasets().isEmpty()
                        ? withSentinel(c)
                        : withFilteredDatasets(c, criteria));

        // Sentinels have one null dataset, so only truly empty/non-matching catalogs are removed.
        var nonEmptyFilteredCatalogs = filtered.filter(c -> !c.getDatasets().isEmpty());

        return nonEmptyFilteredCatalogs
                .flatMap(catalog -> catalog.getDatasets().stream().map(ds -> new DatasetEntry(ds, catalog)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void sortEntries(List<DatasetEntry> allFilteredEntries, QuerySpec spec) {
        // Sort at dataset level; sentinel entries stay after real datasets.
        var sortField = spec.getSortField();
        if (sortField != null) {
            var datasetPath = sortField.startsWith(DATASETS_PREFIX)
                    ? sortField.substring(DATASETS_PREFIX.length())
                    : sortField;
            var comparator = new DatasetComparator(datasetPath, spec.getSortOrder());
            allFilteredEntries.sort((DatasetEntry e1, DatasetEntry e2) -> compareEntries(e1, e2, comparator));
        }
    }

    private List<DatasetEntry> paginateEntries(List<DatasetEntry> allFilteredEntries, QuerySpec spec) {
        // Paginate at dataset level; sentinel (null dataset) entries do not count.
        return allFilteredEntries.stream()
                .filter(e -> e.dataset() != null)
                .skip(spec.getOffset())
                .limit(spec.getLimit())
                .toList();
    }

    private List<Catalog> findSentinelCatalogs(List<DatasetEntry> allFilteredEntries, List<DatasetEntry> paginatedEntries) {
        // Sentinel-only catalogs are not paginated and are always included as empty catalogs.
        var paginatedCatalogIds = paginatedEntries.stream()
                .map(e -> e.catalog().getId())
                .collect(Collectors.toSet());

        return allFilteredEntries.stream()
                .filter(e -> e.dataset() == null)
                .map(DatasetEntry::catalog)
                .filter(c -> !paginatedCatalogIds.contains(c.getId()))
                .distinct()
                .toList();
    }

    private Stream<Catalog> regroupEntries(List<DatasetEntry> paginatedEntries, List<Catalog> sentinelCatalogs) {
        // Regroup paginated datasets, then append sentinel-only catalogs as empty catalogs.
        var datasetsByCatalogId = new LinkedHashMap<String, List<Dataset>>();
        var sourceCatalogById = new LinkedHashMap<String, Catalog>();
        for (var entry : paginatedEntries) {
            var key = entry.catalog().getId();
            datasetsByCatalogId.computeIfAbsent(key, k -> new ArrayList<>()).add(entry.dataset());
            sourceCatalogById.putIfAbsent(key, entry.catalog());
        }

        var regrouped = sourceCatalogById.entrySet().stream()
                .map(e -> Catalog.Builder.newInstance()
                        .id(e.getValue().getId())
                        .participantId(e.getValue().getParticipantId())
                        .datasets(datasetsByCatalogId.get(e.getKey()))
                        .dataServices(e.getValue().getDataServices())
                        .properties(e.getValue().getProperties())
                        .build());

        // Empty catalogs (sentinel-only) are returned with no datasets.
        var emptyCatalogStream = sentinelCatalogs.stream()
                .map(c -> Catalog.Builder.newInstance()
                        .id(c.getId())
                        .participantId(c.getParticipantId())
                        .dataServices(c.getDataServices())
                        .properties(c.getProperties())
                        .build());

        return Stream.concat(regrouped, emptyCatalogStream);
    }

    private static int compareEntries(DatasetEntry left, DatasetEntry right, DatasetComparator comparator) {
        int result;
        if (left.dataset() == null && right.dataset() == null) {
            result = 0;
        } else if (left.dataset() == null) {
            result = 1;
        } else if (right.dataset() == null) {
            result = -1;
        } else {
            result = comparator.compare(left.dataset(), right.dataset());
        }
        return result;
    }

    /**
     * Returns a copy of {@code catalog} containing only the datasets that satisfy all
     * {@code criteria}. Each criterion's {@code operandLeft} is stripped of the
     * {@code "datasets."} prefix before being evaluated against the individual {@link Dataset}.
     */
    private Catalog withFilteredDatasets(Catalog catalog, List<Criterion> criteria) {
        Predicate<Dataset> datasetPredicate = criteria.stream()
                .map(this::toDatasetLevelPredicate)
                .reduce(x -> true, Predicate::and);

        var matchingDatasets = catalog.getDatasets().stream()
                .filter(datasetPredicate)
                .toList();

        return Catalog.Builder.newInstance()
                .id(catalog.getId())
                .participantId(catalog.getParticipantId())
                .datasets(matchingDatasets)
                .dataServices(catalog.getDataServices())
                .properties(catalog.getProperties())
                .build();
    }

    /**
     * Converts a catalog-level {@link Criterion} to a {@link Dataset}-level predicate.
     * Strips the {@code "datasets."} prefix from the left operand if present, then
     * delegates to the {@link CriterionOperatorRegistry}.
     */
    private Predicate<Dataset> toDatasetLevelPredicate(Criterion criterion) {
        var operandLeft = criterion.getOperandLeft().toString();
        var datasetPath = operandLeft.startsWith(DATASETS_PREFIX)
                ? operandLeft.substring(DATASETS_PREFIX.length())
                : operandLeft;
        var datasetCriterion = new Criterion(datasetPath, criterion.getOperator(), criterion.getOperandRight());
        return criterionOperatorRegistry.<Dataset>toPredicate(datasetCriterion);
    }

    /** Returns a copy of {@code catalog} with a single null sentinel dataset so it survives the pipeline. */
    private static Catalog withSentinel(Catalog catalog) {
        var datasets = new ArrayList<Dataset>();
        datasets.add(null);
        return Catalog.Builder.newInstance()
                .id(catalog.getId())
                .participantId(catalog.getParticipantId())
                .datasets(datasets)
                .dataServices(catalog.getDataServices())
                .properties(catalog.getProperties())
                .build();
    }

    /** Pairs a {@link Dataset} with its source {@link Catalog} for regrouping after pagination. */
    private record DatasetEntry(Dataset dataset, Catalog catalog) {}
}

