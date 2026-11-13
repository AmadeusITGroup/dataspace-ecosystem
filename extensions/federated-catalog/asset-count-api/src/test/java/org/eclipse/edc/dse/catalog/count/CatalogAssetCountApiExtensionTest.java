package org.eclipse.edc.dse.catalog.count;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.catalog.spi.QueryService;
import org.eclipse.edc.dse.common.DseNamespaceConfig;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.spi.WebService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.api.management.ManagementApi.MANAGEMENT_SCOPE;
import static org.eclipse.edc.api.management.ManagementApi.MANAGEMENT_SCOPE_V4;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_CATALOG_TYPE;
import static org.eclipse.edc.protocol.dsp.spi.type.Dsp2025Constants.DSP_SCOPE_V_2025_1;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(DependencyInjectionExtension.class)
class CatalogAssetCountApiExtensionTest {

    private static final String DSE_NAMESPACE = "https://w3id.org/dse/v0.0.1/ns/";

    private final JsonLd jsonLd = spy(new TitaniumJsonLd(mock(Monitor.class)));

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(JsonLd.class, jsonLd);
        context.registerService(WebService.class, mock());
        context.registerService(QueryService.class, mock());
        context.registerService(TypeTransformerRegistry.class, mock());
        context.registerService(ParticipantIdMapper.class, mock());
        context.registerService(TypeManager.class, mock());
        context.registerService(JsonObjectValidatorRegistry.class, mock());
        context.registerService(DseNamespaceConfig.class, new DseNamespaceConfig(DSE_NAMESPACE, "dse-policy", "https://w3id.org/dse/policy/"));
    }

    @Test
    void initialize_registersTheDsePrefixOnTheManagementV3AndV4Scopes(CatalogAssetCountApiExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);

        verify(jsonLd).registerNamespace("dse", DSE_NAMESPACE, MANAGEMENT_SCOPE);
        verify(jsonLd).registerNamespace("dse", DSE_NAMESPACE, MANAGEMENT_SCOPE_V4);
        verify(jsonLd, times(2)).registerNamespace(anyString(), anyString(), anyString());
        // not on the default scope, which would add it to every scope of the runtime
        verify(jsonLd, never()).registerNamespace(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {MANAGEMENT_SCOPE, MANAGEMENT_SCOPE_V4})
    void compaction_managementScopes_datasetCountIsDsePrefixed(String scope, CatalogAssetCountApiExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);

        var compacted = jsonLd.compact(datasetCount(), scope).orElseThrow(f -> new AssertionError(f.getFailureDetail()));

        assertThat(compacted.getInt("dse:datasetCount")).isEqualTo(21);
        assertThat(compacted.getJsonObject(CONTEXT)).containsEntry("dse", Json.createValue(DSE_NAMESPACE));
    }

    @Test
    void compaction_otherScopes_keepTheFullIri(CatalogAssetCountApiExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);

        var compacted = jsonLd.compact(datasetCount(), DSP_SCOPE_V_2025_1).orElseThrow(f -> new AssertionError(f.getFailureDetail()));

        assertThat(compacted).containsKey(DSE_NAMESPACE + "datasetCount").doesNotContainKey("dse:datasetCount");
    }

    private static JsonObject datasetCount() {
        return Json.createObjectBuilder()
                .add(TYPE, DCAT_CATALOG_TYPE)
                .add(DSE_NAMESPACE + CatalogAssetCountApiController.DATASET_COUNT_TERM, 21)
                .build();
    }
}
