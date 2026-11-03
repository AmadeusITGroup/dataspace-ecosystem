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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.dse.transform.ThemeTaxonomyConstants.DCT_TITLE_ATTRIBUTE;
import static org.eclipse.edc.dse.transform.ThemeTaxonomyConstants.SKOS_CONCEPT_SCHEME_TYPE;
import static org.eclipse.edc.dse.transform.ThemeTaxonomyConstants.SKOS_CONCEPT_TYPE;
import static org.eclipse.edc.dse.transform.ThemeTaxonomyConstants.SKOS_DEFINITION_ATTRIBUTE;
import static org.eclipse.edc.dse.transform.ThemeTaxonomyConstants.SKOS_HAS_TOP_CONCEPT_ATTRIBUTE;
import static org.eclipse.edc.dse.transform.ThemeTaxonomyConstants.SKOS_NARROWER_ATTRIBUTE;
import static org.eclipse.edc.dse.transform.ThemeTaxonomyConstants.SKOS_NOTATION_ATTRIBUTE;
import static org.eclipse.edc.dse.transform.ThemeTaxonomyConstants.SKOS_PREF_LABEL_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;

class ThemeTaxonomyJsonLdWriterTest {

    private final ThemeTaxonomyJsonLdWriter writer = new ThemeTaxonomyJsonLdWriter(Json.createBuilderFactory(Map.of()));

    @Test
    void toJsonLd_rendersSchemeIdAndType() {
        var scheme = new ThemeTaxonomyBuilder.ConceptScheme("urn:dse:taxonomy", "Enterprise Domain Taxonomy", List.of());

        var json = writer.toJsonLd(scheme);

        assertThat(json.getString(ID)).isEqualTo("urn:dse:taxonomy");
        assertThat(json.getJsonArray(TYPE)).containsExactly(Json.createValue(SKOS_CONCEPT_SCHEME_TYPE));
    }

    @Test
    void toJsonLd_rendersTitleAsValueArray() {
        var scheme = new ThemeTaxonomyBuilder.ConceptScheme("urn:dse:taxonomy", "Enterprise Domain Taxonomy", List.of());

        var json = writer.toJsonLd(scheme);

        var title = json.getJsonArray(DCT_TITLE_ATTRIBUTE);
        assertThat(title).hasSize(1);
        assertThat(title.getJsonObject(0).getString("@value")).isEqualTo("Enterprise Domain Taxonomy");
    }

    @Test
    void toJsonLd_emptyTopConcepts_rendersEmptyArray() {
        var scheme = new ThemeTaxonomyBuilder.ConceptScheme("urn:dse:taxonomy", "Title", List.of());

        var json = writer.toJsonLd(scheme);

        assertThat(json.getJsonArray(SKOS_HAS_TOP_CONCEPT_ATTRIBUTE)).isEmpty();
    }

    @Test
    void toJsonLd_rendersTopConceptWithAllFields() {
        var concept = new ThemeTaxonomyBuilder.Concept("urn:category:50", "Airline Order", "Nevio OMS", "50", List.of());
        var scheme = new ThemeTaxonomyBuilder.ConceptScheme("urn:dse:taxonomy", "Title", List.of(concept));

        var json = writer.toJsonLd(scheme);

        var topConcepts = json.getJsonArray(SKOS_HAS_TOP_CONCEPT_ATTRIBUTE);
        assertThat(topConcepts).hasSize(1);
        var conceptJson = topConcepts.getJsonObject(0);

        assertThat(conceptJson.getString(ID)).isEqualTo("urn:category:50");
        assertThat(conceptJson.getJsonArray(TYPE)).containsExactly(Json.createValue(SKOS_CONCEPT_TYPE));
        assertThat(conceptJson.getJsonArray(SKOS_PREF_LABEL_ATTRIBUTE).getJsonObject(0).getString("@value")).isEqualTo("Airline Order");
        assertThat(conceptJson.getJsonArray(SKOS_DEFINITION_ATTRIBUTE).getJsonObject(0).getString("@value")).isEqualTo("Nevio OMS");
        assertThat(conceptJson.getJsonArray(SKOS_NOTATION_ATTRIBUTE).getJsonObject(0).getString("@value")).isEqualTo("50");
        assertThat(conceptJson.containsKey(SKOS_NARROWER_ATTRIBUTE)).isFalse();
    }

    @Test
    void toJsonLd_conceptWithNullOptionalFields_omitsThoseKeys() {
        var concept = new ThemeTaxonomyBuilder.Concept("urn:category:50", null, null, null, List.of());
        var scheme = new ThemeTaxonomyBuilder.ConceptScheme("urn:dse:taxonomy", "Title", List.of(concept));

        var json = writer.toJsonLd(scheme);
        var conceptJson = json.getJsonArray(SKOS_HAS_TOP_CONCEPT_ATTRIBUTE).getJsonObject(0);

        assertThat(conceptJson.containsKey(SKOS_PREF_LABEL_ATTRIBUTE)).isFalse();
        assertThat(conceptJson.containsKey(SKOS_DEFINITION_ATTRIBUTE)).isFalse();
        assertThat(conceptJson.containsKey(SKOS_NOTATION_ATTRIBUTE)).isFalse();
    }

    @Test
    void toJsonLd_narrowerConcept_nestedUnderParent() {
        var narrower = new ThemeTaxonomyBuilder.Concept("urn:category:990", "Order Management System", null, "990", List.of());
        var top = new ThemeTaxonomyBuilder.Concept("urn:category:50", "Airline Order", null, "50", List.of(narrower));
        var scheme = new ThemeTaxonomyBuilder.ConceptScheme("urn:dse:taxonomy", "Title", List.of(top));

        var json = writer.toJsonLd(scheme);
        var topJson = json.getJsonArray(SKOS_HAS_TOP_CONCEPT_ATTRIBUTE).getJsonObject(0);

        var narrowerArray = topJson.getJsonArray(SKOS_NARROWER_ATTRIBUTE);
        assertThat(narrowerArray).hasSize(1);
        var narrowerJson = narrowerArray.getJsonObject(0);
        assertThat(narrowerJson.getString(ID)).isEqualTo("urn:category:990");
        assertThat(narrowerJson.getJsonArray(SKOS_PREF_LABEL_ATTRIBUTE).getJsonObject(0).getString("@value"))
                .isEqualTo("Order Management System");
    }

    @Test
    void toJsonLd_multipleTopConcepts_allRendered() {
        var concept1 = new ThemeTaxonomyBuilder.Concept("urn:category:50", "A", null, "50", List.of());
        var concept2 = new ThemeTaxonomyBuilder.Concept("urn:category:60", "B", null, "60", List.of());
        var scheme = new ThemeTaxonomyBuilder.ConceptScheme("urn:dse:taxonomy", "Title", List.of(concept1, concept2));

        var json = writer.toJsonLd(scheme);

        assertThat(json.getJsonArray(SKOS_HAS_TOP_CONCEPT_ATTRIBUTE)).hasSize(2);
    }
}
