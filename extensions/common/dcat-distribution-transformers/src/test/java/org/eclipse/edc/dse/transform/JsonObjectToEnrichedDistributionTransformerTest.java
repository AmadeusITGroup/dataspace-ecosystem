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

import jakarta.json.Json;
import jakarta.json.JsonValue;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VALUE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_ACCESS_SERVICE_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_DISTRIBUTION_TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCT_FORMAT_ATTRIBUTE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonObjectToEnrichedDistributionTransformerTest {

    private static final String DCAT_MEDIA_TYPE = "http://www.w3.org/ns/dcat#mediaType";

    private final TransformerContext context = mock();
    private final JsonObjectToEnrichedDistributionTransformer transformer = new JsonObjectToEnrichedDistributionTransformer();

    @Test
    void transform_setsTypedFieldsAndKeepsRawFormatAndAccessService() {
        var json = Json.createObjectBuilder()
                .add(DCT_FORMAT_ATTRIBUTE, Json.createArrayBuilder().add(Json.createObjectBuilder().add(ID, "HttpData-PULL")))
                .add(DCAT_ACCESS_SERVICE_ATTRIBUTE, Json.createArrayBuilder().add(Json.createObjectBuilder().add(ID, "service-id")))
                .build();

        var result = transformer.transform(json, context);

        assertThat(result).isInstanceOfSatisfying(EnrichedDistribution.class, distribution -> {
            assertThat(distribution.getFormat()).isEqualTo("HttpData-PULL");
            assertThat(distribution.getDataService().getId()).isEqualTo("service-id");
            assertThat(distribution.getProperties())
                    .containsKeys(DCT_FORMAT_ATTRIBUTE, DCAT_ACCESS_SERVICE_ATTRIBUTE);
        });
    }

    @Test
    void transform_keepsDistributionIdAndSkipsOtherKeywords() {
        var json = Json.createObjectBuilder()
                .add(ID, "urn:distribution:1")
                .add(TYPE, Json.createArrayBuilder().add(DCAT_DISTRIBUTION_TYPE))
                .add(DCAT_ACCESS_SERVICE_ATTRIBUTE, Json.createObjectBuilder().add(ID, "service-id"))
                .build();

        var result = transformer.transform(json, context);

        assertThat(result).isInstanceOfSatisfying(EnrichedDistribution.class, distribution ->
                assertThat(distribution.getProperties())
                        .containsEntry(ID, "urn:distribution:1")
                        .doesNotContainKey(TYPE));
    }

    @Test
    void transform_storesOtherPropertiesUsingGenericConversion() {
        var mediaType = Json.createArrayBuilder()
                .add(Json.createObjectBuilder().add(VALUE, "application/json"))
                .build();
        var json = Json.createObjectBuilder()
                .add(DCAT_ACCESS_SERVICE_ATTRIBUTE, Json.createObjectBuilder().add(ID, "service-id"))
                .add(DCAT_MEDIA_TYPE, mediaType)
                .build();
        when(context.transform(any(JsonValue.class), eq(Object.class))).thenReturn("application/json");

        var result = transformer.transform(json, context);

        assertThat(result).isInstanceOfSatisfying(EnrichedDistribution.class, distribution ->
                assertThat(distribution.getProperties()).containsEntry(DCAT_MEDIA_TYPE, "application/json"));
        verify(context).transform(mediaType.get(0), Object.class);
    }

    @Test
    void transform_withoutAccessService_reportsProblem() {
        var json = Json.createObjectBuilder()
                .add(ID, "urn:distribution:1")
                .build();

        var result = transformer.transform(json, context);

        assertThat(result).isNull();
        verify(context).reportProblem(contains("dataService is required"));
    }
}