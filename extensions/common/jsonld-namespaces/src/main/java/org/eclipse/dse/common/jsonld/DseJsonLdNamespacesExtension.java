/*
 *  Copyright (c) 2026 Amadeus SA
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus SA - Initial Implementation
 *
 */

package org.eclipse.dse.common.jsonld;

import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.util.List;
import java.util.Map;

/**
 * Registers the extra JSON-LD vocabularies used by the CLE Event model (DCAT representation) so that
 * DSE responses compact them ({@code skos:prefLabel}) instead of emitting full IRIs.
 *
 * <p>Declaring a prefix only in an asset's own {@code @context} is not enough: every API builds its
 * response {@code @context} from the namespaces registered in code for its JSON-LD scope (see
 * PSMP-4558 / PSMP-8266). This extension is loaded by the control-plane, catalog-filter and
 * federated-catalog runtimes; registering a scope that a runtime does not serve is a no-op.
 */
@Extension(value = DseJsonLdNamespacesExtension.NAME)
public class DseJsonLdNamespacesExtension implements ServiceExtension {

    public static final String NAME = "DSE JSON-LD Namespaces";

    public static final String SKOS_PREFIX = "skos";
    public static final String SKOS_NAMESPACE = "http://www.w3.org/2004/02/skos/core#";
    public static final String PROV_PREFIX = "prov";
    public static final String PROV_NAMESPACE = "http://www.w3.org/ns/prov#";
    public static final String ADMS_PREFIX = "adms";
    public static final String ADMS_NAMESPACE = "http://www.w3.org/ns/adms#";

    /**
     * Prefix -> namespace IRI.
     */
    public static final Map<String, String> NAMESPACES = Map.of(
            SKOS_PREFIX, SKOS_NAMESPACE,
            PROV_PREFIX, PROV_NAMESPACE,
            ADMS_PREFIX, ADMS_NAMESPACE);

    /**
     * Management API (EDC {@code ManagementApiConfigurationExtension}).
     */
    public static final String MANAGEMENT_API_SCOPE = "MANAGEMENT_API";
    /**
     * Dataspace Protocol 2025-1 (EDC {@code Dsp2025Constants.DSP_SCOPE_V_2025_1}).
     */
    public static final String DSP_2025_1_SCOPE = "DSP:2025-1";
    /**
     * DSE catalog filter API ({@code VcCatalogFilterExtension.CATALOG_FILTER_SCOPE}).
     */
    public static final String CATALOG_FILTER_API_SCOPE = "CATALOG_FILTER_API";
    /**
     * Federated catalog query API (EDC FC {@code CATALOG_QUERY_SCOPE}).
     */
    public static final String CATALOG_QUERY_API_SCOPE = "CATALOG_QUERY_API";

    public static final List<String> SCOPES = List.of(
            MANAGEMENT_API_SCOPE, DSP_2025_1_SCOPE, CATALOG_FILTER_API_SCOPE, CATALOG_QUERY_API_SCOPE);

    @Inject
    private JsonLd jsonLd;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        for (var scope : SCOPES) {
            NAMESPACES.forEach((prefix, namespace) -> jsonLd.registerNamespace(prefix, namespace, scope));
        }
        context.getMonitor().debug(() -> "Registered JSON-LD namespaces %s for scopes %s".formatted(NAMESPACES.keySet(), SCOPES));
    }
}
