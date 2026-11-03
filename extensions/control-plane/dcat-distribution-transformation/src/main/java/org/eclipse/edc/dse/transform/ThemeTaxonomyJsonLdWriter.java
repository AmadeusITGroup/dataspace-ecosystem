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

import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import java.util.List;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VALUE;

/**
 * Renders a {@link ThemeTaxonomyBuilder.ConceptScheme} as expanded JSON-LD, suitable for the
 * catalog-level {@code dcat:themeTaxonomy} property.
 */
public final class ThemeTaxonomyJsonLdWriter {

    private final JsonBuilderFactory jsonFactory;

    public ThemeTaxonomyJsonLdWriter(JsonBuilderFactory jsonFactory) {
        this.jsonFactory = jsonFactory;
    }

    /**
     * Renders the given concept scheme as an expanded JSON-LD {@code skos:ConceptScheme} object,
     * suitable for the {@code dcat:themeTaxonomy} catalog property.
     */
    public JsonObject toJsonLd(ThemeTaxonomyBuilder.ConceptScheme scheme) {
        var builder = jsonFactory.createObjectBuilder()
                .add(ID, scheme.id())
                .add(TYPE, jsonFactory.createArrayBuilder().add(ThemeTaxonomyConstants.SKOS_CONCEPT_SCHEME_TYPE))
                .add(ThemeTaxonomyConstants.DCT_TITLE_ATTRIBUTE, valueArray(scheme.title()))
                .add(ThemeTaxonomyConstants.SKOS_HAS_TOP_CONCEPT_ATTRIBUTE, toConceptArray(scheme.topConcepts()));

        return builder.build();
    }

    private JsonValue toConceptArray(List<ThemeTaxonomyBuilder.Concept> concepts) {
        JsonArrayBuilder arrayBuilder = jsonFactory.createArrayBuilder();
        concepts.forEach(concept -> arrayBuilder.add(toConceptObject(concept)));
        return arrayBuilder.build();
    }

    private JsonObject toConceptObject(ThemeTaxonomyBuilder.Concept concept) {
        var builder = jsonFactory.createObjectBuilder()
                .add(ID, concept.id())
                .add(TYPE, jsonFactory.createArrayBuilder().add(ThemeTaxonomyConstants.SKOS_CONCEPT_TYPE));

        if (concept.prefLabel() != null) {
            builder.add(ThemeTaxonomyConstants.SKOS_PREF_LABEL_ATTRIBUTE, valueArray(concept.prefLabel()));
        }
        if (concept.definition() != null) {
            builder.add(ThemeTaxonomyConstants.SKOS_DEFINITION_ATTRIBUTE, valueArray(concept.definition()));
        }
        if (concept.notation() != null) {
            builder.add(ThemeTaxonomyConstants.SKOS_NOTATION_ATTRIBUTE, valueArray(concept.notation()));
        }
        if (!concept.narrower().isEmpty()) {
            builder.add(ThemeTaxonomyConstants.SKOS_NARROWER_ATTRIBUTE, toConceptArray(concept.narrower()));
        }

        return builder.build();
    }

    private JsonValue valueArray(String value) {
        return jsonFactory.createArrayBuilder()
                .add(jsonFactory.createObjectBuilder().add(VALUE, value))
                .build();
    }
}
