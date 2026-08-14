/*
 *  Copyright (c) 2026 Amadeus IT Group
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.edc.dse.transform;

import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_DATASET_TYPE;

/**
 * Custom Dataset transformer that adds a marker property to verify it overrides
 * the upstream {@code JsonObjectFromDatasetTransformer}.
 * <p>
 * <strong>WARNING:</strong> This is a test/validation-only implementation that produces
 * minimal JSON-LD output (only @id, @type, and the marker). It intentionally drops fields
 * that upstream transformers include (distributions, policies, properties, etc.).
 * Do NOT enable this extension in production runtimes — it will break protocol compatibility.
 * Once the actual DCAT distribution transformation is implemented, this class should delegate
 * to or augment the upstream transformer output rather than replacing it entirely.
 */
public class CustomDatasetTransformer extends AbstractJsonLdTransformer<Dataset, JsonObject> {

    public static final String CUSTOM_MARKER_PROPERTY = "https://dse.eclipse.org/custom-dataset-marker";
    public static final String CUSTOM_MARKER_VALUE = "custom-dataset-transformer-active";

    private final JsonBuilderFactory jsonFactory;

    public CustomDatasetTransformer(JsonBuilderFactory jsonFactory) {
        super(Dataset.class, JsonObject.class);
        this.jsonFactory = jsonFactory;
    }

    @Override
    public @NotNull JsonObject transform(@NotNull Dataset dataset, @NotNull TransformerContext context) {
        // TODO: actual DCAT distribution transformation to be implemented here
        return jsonFactory.createObjectBuilder()
                .add(ID, dataset.getId())
                .add(TYPE, DCAT_DATASET_TYPE)
                .add(CUSTOM_MARKER_PROPERTY, CUSTOM_MARKER_VALUE)
                .build();
    }
}
