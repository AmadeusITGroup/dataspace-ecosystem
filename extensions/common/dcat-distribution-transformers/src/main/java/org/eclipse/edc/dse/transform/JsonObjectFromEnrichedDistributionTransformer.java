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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsonp.JSONPModule;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.connector.controlplane.catalog.spi.Distribution;
import org.eclipse.edc.protocol.dsp.catalog.transform.from.JsonObjectFromDistributionTransformer;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.LinkedHashSet;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_DISTRIBUTION_TYPE;

public class JsonObjectFromEnrichedDistributionTransformer extends JsonObjectFromDistributionTransformer {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JSONPModule());

    private final JsonBuilderFactory jsonFactory;
    private final Monitor monitor;

    public JsonObjectFromEnrichedDistributionTransformer(JsonBuilderFactory jsonFactory, Monitor monitor) {
        super(jsonFactory);
        this.jsonFactory = jsonFactory;
        this.monitor = monitor;
    }

    @Override
    public @Nullable JsonObject transform(@NotNull Distribution distribution, @NotNull TransformerContext context) {
        var builder = jsonFactory.createObjectBuilder();
        if (distribution instanceof EnrichedDistribution enriched) {
            var properties = enriched.getMutableProperties();
            var types = properties.remove(TYPE);
            transformProperties(properties, builder,
                    value -> value == null ? JsonValue.NULL : toJsonValue(value), context);
            builder.add(TYPE, distributionTypes(types));
            return builder.build();
        }
        builder.add(TYPE, DCAT_DISTRIBUTION_TYPE);
        // EDC's transformer fails on a null format
        if (distribution.getFormat() == null) {
            monitor.warning("Distribution format is null, serializing distribution without format and access service");
            return builder.build();
        }
        return super.transform(distribution, context);
    }

    private JsonValue distributionTypes(Object value) {
        var types = new LinkedHashSet<String>();
        addTypes(types, value);
        types.add(DCAT_DISTRIBUTION_TYPE);
        if (types.size() == 1) {
            return Json.createValue(types.iterator().next());
        }
        var builder = Json.createArrayBuilder();
        types.forEach(builder::add);
        return builder.build();
    }

    private void addTypes(Collection<String> types, Object value) {
        if (value instanceof String type) {
            types.add(type);
        } else if (value instanceof Collection<?> values) {
            values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .forEach(types::add);
        }
    }

    private JsonValue toJsonValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return Json.createValue(decimal);
        }
        if (value instanceof BigInteger integer) {
            return Json.createValue(integer);
        }
        return MAPPER.convertValue(value, JsonValue.class);
    }


}
