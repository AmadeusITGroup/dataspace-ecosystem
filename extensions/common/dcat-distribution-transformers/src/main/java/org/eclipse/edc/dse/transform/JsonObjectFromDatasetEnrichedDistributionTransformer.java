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
import org.eclipse.edc.protocol.dsp.catalog.transform.from.JsonObjectFromDatasetTransformer;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_DISTRIBUTION_ATTRIBUTE;

// Based on EDC's JsonObjectFromDatasetTransformer; keeps asset dcat:distribution alongside generated ones.
public class JsonObjectFromDatasetEnrichedDistributionTransformer extends JsonObjectFromDatasetTransformer {

    private final JsonBuilderFactory jsonFactory;
    private final TypeManager typeManager;
    private final String typeContext;

    public JsonObjectFromDatasetEnrichedDistributionTransformer(JsonBuilderFactory jsonFactory, TypeManager typeManager, String typeContext) {
        super(jsonFactory, typeManager, typeContext);
        this.jsonFactory = jsonFactory;
        this.typeManager = typeManager;
        this.typeContext = typeContext;
    }

    @Override
    public @Nullable JsonObject transform(@NotNull Dataset dataset, @NotNull TransformerContext context) {
        var customDistributions = dataset.getProperties().get(DCAT_DISTRIBUTION_ATTRIBUTE);
        return customDistributions == null
                ? super.transform(dataset, context)
                : transformWithCustomDistributions(dataset, customDistributions, context);
    }

    private @Nullable JsonObject transformWithCustomDistributions(Dataset dataset, Object customDistributions, TransformerContext context) {
        var datasetWithoutCustomDistributions = withoutCustomDistributions(dataset);
        var edcResult = super.transform(datasetWithoutCustomDistributions, context);
        if (edcResult == null) {
            return null;
        }

        var mergedDistributions = new ArrayList<>();
        var edcDistributions = edcResult.getJsonArray(DCAT_DISTRIBUTION_ATTRIBUTE);
        if (edcDistributions != null) {
            mergedDistributions.addAll(edcDistributions);
        }
        if (customDistributions instanceof Collection<?> customList) {
            mergedDistributions.addAll(customList);
        } else {
            mergedDistributions.add(customDistributions);
        }

        var objectBuilder = jsonFactory.createObjectBuilder(edcResult);
        transformProperties(Map.of(DCAT_DISTRIBUTION_ATTRIBUTE, mergedDistributions), objectBuilder,
                typeManager.getMapper(typeContext), context);
        return objectBuilder.build();
    }

    private Dataset withoutCustomDistributions(Dataset dataset) {
        var properties = dataset.getProperties();
        var propertiesWithoutCustomDistributions = new HashMap<>(properties);
        propertiesWithoutCustomDistributions.remove(DCAT_DISTRIBUTION_ATTRIBUTE);

        return copyDataset(dataset, propertiesWithoutCustomDistributions);
    }

    private Dataset copyDataset(Dataset dataset, Map<String, Object> properties) {
        var builder = Dataset.Builder.newInstance().id(dataset.getId());
        dataset.getOffers().forEach(builder::offer);
        dataset.getDistributions().forEach(builder::distribution);
        properties.forEach(builder::property);
        return builder.build();
    }
}
