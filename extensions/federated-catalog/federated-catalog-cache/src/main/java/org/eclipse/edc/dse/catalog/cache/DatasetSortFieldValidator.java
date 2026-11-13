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

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.validator.spi.Validator;

import java.util.Optional;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VALUE;
import static org.eclipse.edc.spi.query.QuerySpec.EDC_QUERY_SPEC_SORT_FIELD;
import static org.eclipse.edc.validator.spi.Violation.violation;

/**
 * Validation of the federated catalog management v4 {@code QuerySpec}: a {@code sortField} that no dataset has is a
 * {@code 400 ValidationFailure} (path {@code sortField}) with the message of the sort, instead of the {@code 500}
 * without body that the upstream query service makes of the sort exception (it turns every exception of the cache
 * query into an unexpected failure).
 *
 * <p>{@link DseFederatedCatalogCacheExtension} registers it under {@code v4:QuerySpec}, next to any other validator of
 * that type. The upstream management v4 JSON-LD interceptor runs these validators on the request body as received,
 * i.e. before JSON-LD expansion, on the upstream {@code POST /v4/catalogs/request} and on the DSE
 * {@code POST /v4/catalogs/query/count}. The body is therefore expanded here, and the sort field is read as
 * {@code https://w3id.org/edc/v0.0.1/ns/sortField}: every form that expands to it ({@code sortField} with an EDC
 * {@code @vocab}, {@code edc:sortField}, the full IRI) is checked, exactly the value the query then sorts by. A body
 * that cannot be expanded is left to the interceptor.
 *
 * <p>The path rules are those of the sort ({@link DatasetFieldPaths}): {@code datasets.} is removed, a missing
 * {@code Map} key is allowed. {@link DatasetComparator} keeps its own check.
 */
public class DatasetSortFieldValidator implements Validator<JsonObject> {

    static final String SORT_FIELD = "sortField";

    private final JsonLd jsonLd;

    public DatasetSortFieldValidator(JsonLd jsonLd) {
        this.jsonLd = jsonLd;
    }

    @Override
    public ValidationResult validate(JsonObject querySpec) {
        var expanded = jsonLd.expand(querySpec);
        if (expanded.failed()) {
            return ValidationResult.success();
        }
        var sortField = sortField(expanded.getContent());
        if (sortField.isEmpty()) {
            // no sort field, or not a plain string: nothing for this validator to check
            return ValidationResult.success();
        }
        var datasetPath = DatasetFieldPaths.toDatasetPath(sortField.get());
        if (DatasetFieldPaths.existsInDataset(datasetPath)) {
            return ValidationResult.success();
        }
        return ValidationResult.failure(violation(DatasetFieldPaths.unknownSortFieldMessage(datasetPath), SORT_FIELD, sortField.get()));
    }

    private static Optional<String> sortField(JsonObject expandedQuerySpec) {
        if (!(expandedQuerySpec.get(EDC_QUERY_SPEC_SORT_FIELD) instanceof JsonArray values) || values.size() != 1 ||
                !(values.get(0) instanceof JsonObject value) || !(value.get(VALUE) instanceof JsonString string)) {
            return Optional.empty();
        }
        return Optional.of(string.getString());
    }
}
