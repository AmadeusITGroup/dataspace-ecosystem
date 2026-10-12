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

import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.eclipse.edc.connector.controlplane.catalog.spi.DataService;
import org.eclipse.edc.connector.controlplane.catalog.spi.Distribution;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_ACCESS_SERVICE_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCT_FORMAT_ATTRIBUTE;

// Based on EDC's JsonObjectToDistributionTransformer; keeps unknown properties as enriched properties.
public class JsonObjectToEnrichedDistributionTransformer extends AbstractJsonLdTransformer<JsonObject, Distribution> {

    public JsonObjectToEnrichedDistributionTransformer() {
        super(JsonObject.class, Distribution.class);
    }

    @Override
    public @Nullable Distribution transform(@NotNull JsonObject object, @NotNull TransformerContext context) {
        var builder = EnrichedDistribution.Builder.newInstance();
        // visitProperties skips JSON-LD keywords, so the distribution @id must be kept explicitly
        if (object.containsKey(ID)) {
            builder.property(ID, nodeId(object));
        }
        if (object.containsKey(TYPE)) {
            builder.property(TYPE, transformTypes(object.get(TYPE)));
        }
        visitProperties(object, (key, value) -> transformProperties(key, value, builder, context));
        return builderResult(builder::build, context);
    }

    private List<String> transformTypes(JsonValue value) {
        var values = value.getValueType() == JsonValue.ValueType.ARRAY ? value.asJsonArray() : List.of(value);
        return values.stream()
                .filter(JsonString.class::isInstance)
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .toList();
    }

    private void transformProperties(String key, JsonValue value, EnrichedDistribution.Builder builder, TransformerContext context) {
        if (DCAT_ACCESS_SERVICE_ATTRIBUTE.equals(key)) {
            var dataServiceBuilder = DataService.Builder.newInstance();
            transformString(value, dataServiceBuilder::id, context);
            builder.dataService(dataServiceBuilder.build());
        } else if (DCT_FORMAT_ATTRIBUTE.equals(key)) {
            transformString(value, builder::format, context);
        }
        // keep the raw value too, so serialization reproduces the original distribution
        builder.property(key, transformGenericProperty(value, context));
    }
}
