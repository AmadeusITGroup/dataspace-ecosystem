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

import static org.eclipse.edc.jsonld.spi.Namespaces.DCAT_SCHEMA;
import static org.eclipse.edc.jsonld.spi.Namespaces.DCT_SCHEMA;

/**
 * IRI constants for the DCAT {@code dcat:themeTaxonomy} / SKOS concept-scheme structure added to
 * catalogs by the DSE theme-taxonomy transformer.
 * <p>
 * Shapes mirror the expanded JSON-LD form produced by upstream transformers (property IRIs as
 * object keys, {@code @id}/{@code @type} keywords).
 */
public final class ThemeTaxonomyConstants {

    // SKOS namespace - not provided by EDC's own Namespaces. The "skos" prefix itself is registered
    // by DseJsonLdNamespacesExtension (extensions:common:jsonld-namespaces).
    public static final String SKOS_SCHEMA = "http://www.w3.org/2004/02/skos/core#";

    // DCAT
    public static final String DCAT_THEME_ATTRIBUTE = DCAT_SCHEMA + "theme";
    public static final String DCAT_THEME_TAXONOMY_ATTRIBUTE = DCAT_SCHEMA + "themeTaxonomy";

    // DCT
    public static final String DCT_TITLE_ATTRIBUTE = DCT_SCHEMA + "title";

    // SKOS
    public static final String SKOS_CONCEPT_SCHEME_TYPE = SKOS_SCHEMA + "ConceptScheme";
    public static final String SKOS_CONCEPT_TYPE = SKOS_SCHEMA + "Concept";
    public static final String SKOS_HAS_TOP_CONCEPT_ATTRIBUTE = SKOS_SCHEMA + "hasTopConcept";
    public static final String SKOS_NARROWER_ATTRIBUTE = SKOS_SCHEMA + "narrower";
    public static final String SKOS_PREF_LABEL_ATTRIBUTE = SKOS_SCHEMA + "prefLabel";
    public static final String SKOS_DEFINITION_ATTRIBUTE = SKOS_SCHEMA + "definition";
    public static final String SKOS_NOTATION_ATTRIBUTE = SKOS_SCHEMA + "notation";

    public static final String TAXONOMY_SCHEME_ID = "urn:dse:taxonomy";
    public static final String TAXONOMY_TITLE = "Enterprise Domain Taxonomy";

    private ThemeTaxonomyConstants() {
    }
}
