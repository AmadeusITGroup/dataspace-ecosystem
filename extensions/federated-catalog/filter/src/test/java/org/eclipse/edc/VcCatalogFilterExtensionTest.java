package org.eclipse.edc;

import org.eclipse.edc.dse.common.DseNamespaceConfig;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.junit.jupiter.api.Test;

import static org.eclipse.edc.VcCatalogFilterExtension.CATALOG_FILTER_SCOPE;
import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_CONTEXT_2025_1;
import static org.eclipse.edc.jsonld.spi.Namespaces.EDC_DSPACE_CONTEXT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class VcCatalogFilterExtensionTest {

    @Test
    void shouldRegisterFederatedCatalogContexts() {
        var jsonLd = mock(JsonLd.class);
        var namespaceConfig = new DseNamespaceConfig("https://w3id.org/dse/v0.0.1/ns/", "dse-policy", "https://w3id.org/dse/policy/");

        VcCatalogFilterExtension.registerContexts(jsonLd, namespaceConfig);

        verify(jsonLd).registerContext(DSPACE_CONTEXT_2025_1, CATALOG_FILTER_SCOPE);
        verify(jsonLd).registerContext(EDC_DSPACE_CONTEXT, CATALOG_FILTER_SCOPE);
        verify(jsonLd).registerNamespace("dse", namespaceConfig.dseNamespace());
        verifyNoMoreInteractions(jsonLd);
    }
}