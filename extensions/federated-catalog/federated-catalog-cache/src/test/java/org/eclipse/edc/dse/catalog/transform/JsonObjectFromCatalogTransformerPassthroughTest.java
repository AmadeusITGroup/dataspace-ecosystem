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

package org.eclipse.edc.dse.catalog.transform;

import jakarta.json.Json;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.dse.transform.ThemeTaxonomyConstants;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.jsonld.util.JacksonJsonLd;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.protocol.dsp.catalog.transform.from.JsonObjectFromCatalogV2025Transformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.protocol.dsp.spi.type.Dsp2025Constants.DSP_NAMESPACE_V_2025_1;
import static org.mockito.Mockito.mock;

/**
 * Checks that the upstream DSP 2025-1 catalog transformer writes a cached dcat:themeTaxonomy back
 * out on its own, which is why the federated catalog doesn't register its own catalog transformer.
 * The ingestion side is covered by {@link JsonObjectToCatalogTransformerPassthroughTest}.
 */
class JsonObjectFromCatalogTransformerPassthroughTest {

    private static final String TYPE_CONTEXT = "test";

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

    @Test
    void transform_cachedThemeTaxonomy_isWrittenWithItsNesting() {
        var typeManager = new JacksonTypeManager();
        typeManager.registerContext(TYPE_CONTEXT, JacksonJsonLd.createObjectMapper());
        var transformer = new JsonObjectFromCatalogV2025Transformer(Json.createBuilderFactory(Map.of()), typeManager,
                TYPE_CONTEXT, NO_OP_PARTICIPANT_ID_MAPPER, DSP_NAMESPACE_V_2025_1);

        // Shape the crawler leaves in Catalog.properties: plain Maps/Lists in expanded JSON-LD form.
        var child = Map.of("@id", "urn:example:child");
        var top = Map.of("@id", "urn:example:top", ThemeTaxonomyConstants.SKOS_NARROWER_ATTRIBUTE, List.of(child));
        var taxonomy = Map.of("@id", "urn:dse:taxonomy", ThemeTaxonomyConstants.SKOS_HAS_TOP_CONCEPT_ATTRIBUTE, List.of(top));
        var catalog = Catalog.Builder.newInstance().id("urn:catalog:1")
                .property(ThemeTaxonomyConstants.DCAT_THEME_TAXONOMY_ATTRIBUTE, taxonomy)
                .build();

        var result = transformer.transform(catalog, mock(TransformerContext.class));

        assertThat(result).isNotNull();
        var taxonomyJson = result.getJsonObject(ThemeTaxonomyConstants.DCAT_THEME_TAXONOMY_ATTRIBUTE);
        assertThat(taxonomyJson.getString(ID)).isEqualTo("urn:dse:taxonomy");
        var topJson = taxonomyJson.getJsonArray(ThemeTaxonomyConstants.SKOS_HAS_TOP_CONCEPT_ATTRIBUTE).getJsonObject(0);
        assertThat(topJson.getString(ID)).isEqualTo("urn:example:top");
        assertThat(topJson.getJsonArray(ThemeTaxonomyConstants.SKOS_NARROWER_ATTRIBUTE).getJsonObject(0).getString(ID))
                .isEqualTo("urn:example:child");
    }
}
