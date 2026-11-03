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
 */

package org.eclipse.edc.dse.transform;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.dse.controlplane.category.whitelist.CategoryWhitelist;
import org.eclipse.edc.dse.controlplane.category.whitelist.WhitelistedCategory;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.dse.transform.ThemeTaxonomyConstants.DCAT_THEME_ATTRIBUTE;
import static org.eclipse.edc.dse.transform.ThemeTaxonomyConstants.DCAT_THEME_TAXONOMY_ATTRIBUTE;
import static org.eclipse.edc.dse.transform.ThemeTaxonomyConstants.SKOS_HAS_TOP_CONCEPT_ATTRIBUTE;
import static org.eclipse.edc.dse.transform.ThemeTaxonomyConstants.SKOS_NARROWER_ATTRIBUTE;
import static org.eclipse.edc.dse.transform.ThemeTaxonomyConstants.TAXONOMY_SCHEME_ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_CATALOG_TYPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DSECatalogTransformerTest {

    private static final String TYPE_CONTEXT = "test";

    // Order -> Order Management System -> Data Change Event, and an unrelated Hotel
    private static final List<WhitelistedCategory> WHITELIST = List.of(
            new WhitelistedCategory("Order", "Order", null, "urn:category:order", null),
            new WhitelistedCategory("OMS", "Order Management System", null, "urn:category:order:oms", "Order"),
            new WhitelistedCategory("DCE", "Data Change Event", null, "urn:category:order:dce", "OMS"),
            new WhitelistedCategory("Hotel", "Hotel", null, "urn:category:hotel", null));

    private static final ParticipantIdMapper NO_OP_PARTICIPANT_ID_MAPPER = new ParticipantIdMapper() {
        @Override
        public String toIri(String participantId) {
            return participantId;
        }

        @Override
        public String fromIri(String iriParticipantId) {
            return iriParticipantId;
        }
    };

    private final JacksonTypeManager typeManager = new JacksonTypeManager();
    private final TransformerContext context = mock(TransformerContext.class);
    private final DSECatalogTransformer transformer = new DSECatalogTransformer(
            Json.createBuilderFactory(Map.of()), typeManager, TYPE_CONTEXT, NO_OP_PARTICIPANT_ID_MAPPER,
            new CategoryWhitelist(WHITELIST));

    {
        // The upstream transformer turns each Dataset into JSON through the context.
        when(context.transform(any(Dataset.class), eq(JsonObject.class)))
                .thenAnswer(call -> Json.createObjectBuilder().add(ID, ((Dataset) call.getArgument(0)).getId()).build());
    }

    @Test
    void transform_childTheme_includesChildAndParentOnly() {
        var catalog = catalogWith(dataset("ds-1", List.of(Map.of(ID, "urn:category:order:oms"))));

        var topConcepts = topConcepts(transformer.transform(catalog, context));

        assertThat(topConcepts).hasSize(1);
        var order = topConcepts.getJsonObject(0);
        assertThat(order.getString(ID)).isEqualTo("urn:category:order");
        assertThat(order.getJsonArray(SKOS_NARROWER_ATTRIBUTE)).hasSize(1);
        assertThat(order.getJsonArray(SKOS_NARROWER_ATTRIBUTE).getJsonObject(0).getString(ID)).isEqualTo("urn:category:order:oms");
        assertThat(topConcepts.toString()).doesNotContain("urn:category:hotel");
    }

    @Test
    void transform_grandchildTheme_includesParentAndGrandparent() {
        var catalog = catalogWith(dataset("ds-1", List.of(Map.of(ID, "urn:category:order:dce"))));

        var topConcepts = topConcepts(transformer.transform(catalog, context));

        // Order -> Order Management System -> Data Change Event, and nothing else
        assertThat(topConcepts).hasSize(1);
        var order = topConcepts.getJsonObject(0);
        assertThat(order.getString(ID)).isEqualTo("urn:category:order");
        var oms = order.getJsonArray(SKOS_NARROWER_ATTRIBUTE).getJsonObject(0);
        assertThat(oms.getString(ID)).isEqualTo("urn:category:order:oms");
        var dce = oms.getJsonArray(SKOS_NARROWER_ATTRIBUTE);
        assertThat(dce).hasSize(1);
        assertThat(dce.getJsonObject(0).getString(ID)).isEqualTo("urn:category:order:dce");
        assertThat(topConcepts.toString()).doesNotContain("urn:category:hotel");
    }

    @Test
    void transform_themesAcrossDatasets_areMergedOnce() {
        var catalog = catalogWith(
                dataset("ds-1", List.of(Map.of(ID, "urn:category:order"))),
                dataset("ds-2", List.of(Map.of(ID, "urn:category:hotel"), Map.of(ID, "urn:category:order"))));

        var topConcepts = topConcepts(transformer.transform(catalog, context));

        assertThat(topConcepts).extracting(concept -> concept.asJsonObject().getString(ID))
                .containsExactly("urn:category:order", "urn:category:hotel");
    }

    @Test
    void transform_themeAsPlainCategoryId_isMatched() {
        var catalog = catalogWith(dataset("ds-1", "Hotel"));

        var topConcepts = topConcepts(transformer.transform(catalog, context));

        assertThat(topConcepts).extracting(concept -> concept.asJsonObject().getString(ID))
                .containsExactly("urn:category:hotel");
    }

    @Test
    void transform_noMatchingTheme_addsSchemeWithoutConcepts() {
        var catalog = catalogWith(
                dataset("ds-1", List.of(Map.of(ID, "urn:not:in:whitelist"))),
                dataset("ds-2", null));

        var result = transformer.transform(catalog, context);

        var taxonomy = result.getJsonObject(DCAT_THEME_TAXONOMY_ATTRIBUTE);
        assertThat(taxonomy.getString(ID)).isEqualTo(TAXONOMY_SCHEME_ID);
        assertThat(taxonomy.getJsonArray(SKOS_HAS_TOP_CONCEPT_ATTRIBUTE)).isEmpty();
    }

    @Test
    void transform_emptyCatalog_addsSchemeWithoutConcepts() {
        var result = transformer.transform(Catalog.Builder.newInstance().id("urn:catalog:empty").build(), context);

        assertThat(result.getJsonObject(DCAT_THEME_TAXONOMY_ATTRIBUTE).getJsonArray(SKOS_HAS_TOP_CONCEPT_ATTRIBUTE)).isEmpty();
    }

    @Test
    void transform_delegatesStandardCatalogFieldsToUpstreamTransformer() {
        var result = transformer.transform(Catalog.Builder.newInstance().id("urn:catalog:standard-fields").build(), context);

        // id and type come from the upstream JsonObjectFromCatalogV2025Transformer.
        assertThat(result.getString(ID)).isEqualTo("urn:catalog:standard-fields");
        assertThat(result.getString(TYPE)).isEqualTo(DCAT_CATALOG_TYPE);
    }

    @Test
    void transform_emptyCatalog_keepsUpstreamOutputWithoutEmptyDcatArrays() {
        var result = transformer.transform(Catalog.Builder.newInstance().id("urn:catalog:empty").build(), context);

        // The DSP 2025-1 transformer omits empty dataset/catalog/distribution/service arrays; the
        // legacy one emitted them. This guards against wrapping the wrong upstream class again.
        assertThat(result.keySet()).doesNotContain(
                "http://www.w3.org/ns/dcat#dataset", "http://www.w3.org/ns/dcat#catalog",
                "http://www.w3.org/ns/dcat#distribution", "http://www.w3.org/ns/dcat#service");
    }

    @Test
    void transform_keepsDatasetsUnchanged() {
        var result = transformer.transform(catalogWith(dataset("ds-1", List.of(Map.of(ID, "urn:category:hotel")))), context);

        assertThat(result.toString()).contains("ds-1");
    }

    private static JsonArray topConcepts(JsonObject catalogJson) {
        return catalogJson.getJsonObject(DCAT_THEME_TAXONOMY_ATTRIBUTE).getJsonArray(SKOS_HAS_TOP_CONCEPT_ATTRIBUTE);
    }

    private static Catalog catalogWith(Dataset... datasets) {
        return Catalog.Builder.newInstance().id("urn:catalog:1").datasets(List.of(datasets)).build();
    }

    private static Dataset dataset(String id, Object theme) {
        var builder = Dataset.Builder.newInstance().id(id);
        if (theme != null) {
            builder.property(DCAT_THEME_ATTRIBUTE, theme);
        }
        return builder.build();
    }
}
