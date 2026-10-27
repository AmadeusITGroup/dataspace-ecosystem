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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.dse.common.jsonld.DseJsonLdNamespacesExtension.ADMS_NAMESPACE;
import static org.eclipse.dse.common.jsonld.DseJsonLdNamespacesExtension.PROV_NAMESPACE;
import static org.eclipse.dse.common.jsonld.DseJsonLdNamespacesExtension.SKOS_NAMESPACE;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(DependencyInjectionExtension.class)
class DseJsonLdNamespacesExtensionTest {

    private final JsonLd jsonLd = spy(new TitaniumJsonLd(mock(Monitor.class)));

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(JsonLd.class, jsonLd);
    }

    @Test
    void initialize_registersEveryNamespaceForEveryScope(DseJsonLdNamespacesExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);

        for (var scope : DseJsonLdNamespacesExtension.SCOPES) {
            verify(jsonLd).registerNamespace("skos", SKOS_NAMESPACE, scope);
            verify(jsonLd).registerNamespace("prov", PROV_NAMESPACE, scope);
            verify(jsonLd).registerNamespace("adms", ADMS_NAMESPACE, scope);
        }
        verify(jsonLd, times(3 * 4)).registerNamespace(anyString(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGEMENT_API", "DSP:2025-1", "CATALOG_FILTER_API", "CATALOG_QUERY_API"})
    void compaction_usesRegisteredPrefixes(String scope, DseJsonLdNamespacesExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);

        var compacted = jsonLd.compact(expandedDataset(), scope).orElseThrow(f -> new AssertionError(f.getFailureDetail()));

        assertThat(compacted.getJsonObject("@context"))
                .containsEntry("skos", Json.createValue(SKOS_NAMESPACE))
                .containsEntry("prov", Json.createValue(PROV_NAMESPACE))
                .containsEntry("adms", Json.createValue(ADMS_NAMESPACE));
        assertThat(compacted).containsKeys("skos:prefLabel", "prov:wasAttributedTo", "adms:versionNotes");
    }

    @Test
    void compaction_withoutExtension_keepsFullIris() {
        var compacted = jsonLd.compact(expandedDataset(), "MANAGEMENT_API").orElseThrow(f -> new AssertionError(f.getFailureDetail()));

        assertThat(compacted).containsKey(SKOS_NAMESPACE + "prefLabel").doesNotContainKey("skos:prefLabel");
    }

    @Test
    void registration_isScoped_otherScopesAreUnaffected(DseJsonLdNamespacesExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);

        var compacted = jsonLd.compact(expandedDataset(), "SOME_OTHER_API").orElseThrow(f -> new AssertionError(f.getFailureDetail()));

        assertThat(compacted).containsKey(SKOS_NAMESPACE + "prefLabel");
    }

    private static JsonObject expandedDataset() {
        return Json.createObjectBuilder()
                .add("@id", "https://data.example.org/dataset/probe")
                .add(SKOS_NAMESPACE + "prefLabel", Json.createArrayBuilder().add(Json.createObjectBuilder().add("@value", "probe-label")))
                .add(PROV_NAMESPACE + "wasAttributedTo", Json.createArrayBuilder().add(Json.createObjectBuilder().add("@id", "https://example.org/org/amadeus")))
                .add(ADMS_NAMESPACE + "versionNotes", Json.createArrayBuilder().add(Json.createObjectBuilder().add("@value", "first version")))
                .build();
    }
}
