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
 *   <li>Sort — datasets are sorted across all catalogs using {@link DatasetComparator}.</li>
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

        // When no filter criteria and no sort field are provided, preserve ALL catalogs
        // as-is (including empty ones). Paginate at catalog level so that participants
        // with zero datasets remain visible in query results.
        if (criteria.isEmpty() && spec.getSortField() == null) {
            return stream.skip(spec.getOffset()).limit(spec.getLimit());
        }

        // 1. Filter: keep only datasets that satisfy all criteria within each catalog.
        //    Drop catalogs whose dataset list becomes empty after filtering.
        Stream<Catalog> filtered = stream.map(c -> withFilteredDatasets(c, criteria))
                .filter(c -> !c.getDatasets().isEmpty());

        // 2. Flatten all matching datasets, keeping a reference to their source catalog.
        var allFilteredEntries = filtered
                .flatMap(catalog -> catalog.getDatasets().stream()
                        .map(ds -> new DatasetEntry(ds, catalog)))
                .collect(Collectors.toCollection(ArrayList::new));

        // 3. Sort at dataset level.
        var sortField = spec.getSortField();
        if (sortField != null) {
            var datasetPath = sortField.startsWith(DATASETS_PREFIX)
                    ? sortField.substring(DATASETS_PREFIX.length())
                    : sortField;
            var comparator = new DatasetComparator(datasetPath, spec.getSortOrder());
            allFilteredEntries.sort((e1, e2) -> comparator.compare(e1.dataset(), e2.dataset()));
        }

        // 4. Paginate at dataset level.
        var paginatedEntries = allFilteredEntries.stream()
                .skip(spec.getOffset())
                .limit(spec.getLimit())
                .toList();

        // 5. Regroup paginated datasets into their source catalogs, preserving catalog metadata.
        //    LinkedHashMap preserves insertion order (= sort order).
        var datasetsByCatalogId = new LinkedHashMap<String, List<Dataset>>();
        var sourceCatalogById   = new LinkedHashMap<String, Catalog>();
        for (var entry : paginatedEntries) {
            var key = entry.catalog().getId();
            datasetsByCatalogId.computeIfAbsent(key, k -> new ArrayList<>()).add(entry.dataset());
            sourceCatalogById.putIfAbsent(key, entry.catalog());
        }

        return sourceCatalogById.entrySet().stream()
                .map(e -> Catalog.Builder.newInstance()
                        .id(e.getValue().getId())
                        .participantId(e.getValue().getParticipantId())
                        .datasets(datasetsByCatalogId.get(e.getKey()))
                        .dataServices(e.getValue().getDataServices())
                        .properties(e.getValue().getProperties())
                        .build());
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

    /** Pairs a {@link Dataset} with its source {@link Catalog} for regrouping after pagination. */
    private record DatasetEntry(Dataset dataset, Catalog catalog) {}
}

