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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.dse.transform.ThemeTaxonomyConstants;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.protocol.dsp.catalog.transform.v2025.to.JsonObjectToCatalogTransformer;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transform.TypeTransformerRegistryImpl;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.transform.transformer.edc.to.JsonValueToGenericTypeTransformer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;
import static org.mockito.Mockito.mock;

/**
 * Empirically checks whether the real upstream {@link JsonObjectToCatalogTransformer} already
 * preserves an unrecognized catalog-level property (such as {@code dcat:themeTaxonomy}) into the
 * resulting {@link Catalog}'s properties map on its own, via its generic property fallback - or
 * whether an override is needed on the federated catalog's ingestion side, mirroring the
 * composition-wrapper pattern used on the participant side.
 * <p>
 * Uses a real {@link TypeTransformerRegistry} (not a mock {@code TransformerContext}), because
 * {@code AbstractJsonLdTransformer#transformGenericProperty} delegates nested JSON objects to
 * {@code TransformerContext.transform(Object, Class)}, which a bare mock would resolve to
 * {@code null} - silently hiding whether the passthrough actually works.
 */
class JsonObjectToCatalogTransformerPassthroughTest {

    @Test
    void transform_unknownNestedObjectProperty_isPreservedInCatalogProperties() throws Exception {
        var monitor = mock(Monitor.class);
        var registry = new TypeTransformerRegistryImpl(monitor);
        registry.register(new JsonObjectToCatalogTransformer());
        registry.register(new JsonValueToGenericTypeTransformer(new JacksonTypeManager(), JSON_LD));

        var jsonLd = new TitaniumJsonLd(monitor);
        jsonLd.registerNamespace("skos", "http://www.w3.org/2004/02/skos/core#");

        // A real taxonomy, Order with Order Management System under it, so the check covers
        // the nested concepts and their labels, not just the top-level key.
        var taxonomy = Json.createObjectBuilder()
                .add("@id", "urn:dse:taxonomy")
                .add("@type", Json.createArrayBuilder().add(ThemeTaxonomyConstants.SKOS_CONCEPT_SCHEME_TYPE))
                .add(ThemeTaxonomyConstants.SKOS_HAS_TOP_CONCEPT_ATTRIBUTE, Json.createArrayBuilder().add(Json.createObjectBuilder()
                        .add("@id", "urn:category:Order")
                        .add("@type", Json.createArrayBuilder().add(ThemeTaxonomyConstants.SKOS_CONCEPT_TYPE))
                        .add(ThemeTaxonomyConstants.SKOS_PREF_LABEL_ATTRIBUTE, Json.createArrayBuilder().add(Json.createObjectBuilder().add("@value", "Order")))
                        .add(ThemeTaxonomyConstants.SKOS_NARROWER_ATTRIBUTE, Json.createArrayBuilder().add(Json.createObjectBuilder()
                                .add("@id", "urn:category:Order:subcategory:Order_Management_System")
                                .add("@type", Json.createArrayBuilder().add(ThemeTaxonomyConstants.SKOS_CONCEPT_TYPE))
                                .add(ThemeTaxonomyConstants.SKOS_PREF_LABEL_ATTRIBUTE, Json.createArrayBuilder()
                                        .add(Json.createObjectBuilder().add("@value", "Order Management System")))))))
                .build();

        var expandedCatalog = Json.createObjectBuilder()
                .add("@id", "urn:catalog:test")
                .add("@type", "http://www.w3.org/ns/dcat#Catalog")
                .add(ThemeTaxonomyConstants.DCAT_THEME_TAXONOMY_ATTRIBUTE, Json.createArrayBuilder().add(taxonomy))
                .build();

        var catalog = registry.transform(expandedCatalog, Catalog.class).orElseThrow(failure ->
                new AssertionError("Transform failed: " + failure.getFailureDetail()));

        // The stored value must be the whole taxonomy, not just a key with an empty or null value.
        var expected = new ObjectMapper().readValue(taxonomy.toString(), Map.class);
        assertThat(catalog.getProperties().get(ThemeTaxonomyConstants.DCAT_THEME_TAXONOMY_ATTRIBUTE)).isEqualTo(expected);
    }
}
