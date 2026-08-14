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
import org.eclipse.edc.connector.controlplane.catalog.spi.Distribution;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_DISTRIBUTION_TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCT_FORMAT_ATTRIBUTE;

/**
 * Custom Distribution transformer that adds a marker property to verify it overrides
 * the upstream {@code JsonObjectFromDistributionTransformer}.
 * <p>
 * <strong>WARNING:</strong> This is a test/validation-only implementation that produces
 * minimal JSON-LD output (only @type, dct:format, and the marker). It intentionally drops
 * fields that upstream transformers include (data service references, access URLs, etc.).
 * Do NOT enable this extension in production runtimes — it will produce incomplete DCAT
 * distributions. Once the actual implementation is complete, this class should delegate to
 * or augment the upstream transformer output rather than replacing it entirely.
 */
public class CustomDistributionTransformer extends AbstractJsonLdTransformer<Distribution, JsonObject> {

    public static final String CUSTOM_MARKER_PROPERTY = "https://dse.eclipse.org/custom-distribution-marker";
    public static final String CUSTOM_MARKER_VALUE = "custom-distribution-transformer-active";

    private final JsonBuilderFactory jsonFactory;

    public CustomDistributionTransformer(JsonBuilderFactory jsonFactory) {
        super(Distribution.class, JsonObject.class);
        this.jsonFactory = jsonFactory;
    }

    @Override
    public @NotNull JsonObject transform(@NotNull Distribution distribution, @NotNull TransformerContext context) {
        var builder = jsonFactory.createObjectBuilder()
                .add(TYPE, DCAT_DISTRIBUTION_TYPE)
                .add(CUSTOM_MARKER_PROPERTY, CUSTOM_MARKER_VALUE);

        if (distribution.getFormat() != null) {
            builder.add(DCT_FORMAT_ATTRIBUTE, distribution.getFormat());
        }

        return builder.build();
    }
}
