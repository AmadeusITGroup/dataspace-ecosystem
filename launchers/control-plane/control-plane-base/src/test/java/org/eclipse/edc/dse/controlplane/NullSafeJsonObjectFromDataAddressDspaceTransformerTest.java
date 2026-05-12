package org.eclipse.edc.dse.controlplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_SCHEMA;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;
import static org.eclipse.edc.transform.transformer.dspace.DataAddressDspaceSerialization.ENDPOINT_PROPERTIES_PROPERTY_TERM;
import static org.eclipse.edc.transform.transformer.dspace.DataAddressDspaceSerialization.ENDPOINT_PROPERTY_NAME_PROPERTY_TERM;
import static org.eclipse.edc.transform.transformer.dspace.DataAddressDspaceSerialization.ENDPOINT_PROPERTY_VALUE_PROPERTY_TERM;
import static org.eclipse.edc.transform.transformer.dspace.DataAddressDspaceSerialization.ENDPOINT_TYPE_PROPERTY_TERM;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NullSafeJsonObjectFromDataAddressDspaceTransformerTest {

    private NullSafeJsonObjectFromDataAddressDspaceTransformer transformer;
    private TransformerContext context;

    @BeforeEach
    void setUp() {
        var factory = Json.createBuilderFactory(Map.of());
        var typeManager = new JacksonTypeManager();
        transformer = new NullSafeJsonObjectFromDataAddressDspaceTransformer(factory, typeManager, JSON_LD);
        context = mock(TransformerContext.class);
    }

    @Test
    void transform_basicAddress_shouldSucceed() {
        var address = DataAddress.Builder.newInstance()
                .type("HttpData")
                .property("baseUrl", "https://example.com")
                .build();

        var result = transformer.transform(address, context);

        assertThat(result).isNotNull();
        assertThat(result.getString(DSPACE_SCHEMA + ENDPOINT_TYPE_PROPERTY_TERM)).isEqualTo("HttpData");
        assertThat(result.getJsonArray(DSPACE_SCHEMA + ENDPOINT_PROPERTIES_PROPERTY_TERM)).isNotEmpty();
    }

    @Test
    void transform_addressWithNullProperty_shouldFilterOutNull() {
        var address = DataAddress.Builder.newInstance()
                .type("HttpData")
                .property("baseUrl", "https://example.com")
                .property("secret", null)
                .build();

        var result = transformer.transform(address, context);

        assertThat(result).isNotNull();
        var properties = result.getJsonArray(DSPACE_SCHEMA + ENDPOINT_PROPERTIES_PROPERTY_TERM);
        // only baseUrl should be present, secret with null value is filtered
        assertThat(properties).hasSize(1);
        var propNames = properties.stream()
                .map(v -> v.asJsonObject().getString(DSPACE_SCHEMA + "name"))
                .toList();
        assertThat(propNames).contains("baseUrl");
        assertThat(propNames).doesNotContain("secret");
    }

    @Test
    void transform_addressWithAllNullProperties_shouldReturnEmptyProperties() {
        var address = DataAddress.Builder.newInstance()
                .type("HttpData")
                .property("key1", null)
                .property("key2", null)
                .build();

        var result = transformer.transform(address, context);

        assertThat(result).isNotNull();
        assertThat(result.getJsonArray(DSPACE_SCHEMA + ENDPOINT_PROPERTIES_PROPERTY_TERM)).isEmpty();
    }

    @Test
    void transform_addressWithOnlyTypeProperty_shouldHaveEmptyEndpointProperties() {
        var address = DataAddress.Builder.newInstance()
                .type("AzureStorage")
                .build();

        var result = transformer.transform(address, context);

        assertThat(result).isNotNull();
        assertThat(result.getString(DSPACE_SCHEMA + ENDPOINT_TYPE_PROPERTY_TERM)).isEqualTo("AzureStorage");
        assertThat(result.getJsonArray(DSPACE_SCHEMA + ENDPOINT_PROPERTIES_PROPERTY_TERM)).isEmpty();
    }

    @Test
    void transform_addressWithMultipleProperties_shouldIncludeAll() {
        var address = DataAddress.Builder.newInstance()
                .type("HttpData")
                .property("baseUrl", "https://example.com")
                .property("method", "POST")
                .property("contentType", "application/json")
                .build();

        var result = transformer.transform(address, context);

        assertThat(result).isNotNull();
        var properties = result.getJsonArray(DSPACE_SCHEMA + ENDPOINT_PROPERTIES_PROPERTY_TERM);
        assertThat(properties).hasSize(3);
    }

    @Test
    void transform_typeProperty_shouldNotAppearInEndpointProperties() {
        var address = DataAddress.Builder.newInstance()
                .type("HttpData")
                .property("baseUrl", "https://example.com")
                .build();

        var result = transformer.transform(address, context);

        assertThat(result).isNotNull();
        var properties = result.getJsonArray(DSPACE_SCHEMA + ENDPOINT_PROPERTIES_PROPERTY_TERM);
        var propNames = properties.stream()
                .map(v -> v.asJsonObject().getString(DSPACE_SCHEMA + "name"))
                .toList();
        // EDC_DATA_ADDRESS_TYPE_PROPERTY is excluded from endpoint properties
        assertThat(propNames).doesNotContain("https://w3id.org/edc/v0.0.1/ns/type");
    }

    @Test
    void transform_returnsExpectedJsonStructure() {
        var address = DataAddress.Builder.newInstance()
                .type("HttpData")
                .property("endpoint", "https://api.example.com")
                .build();

        JsonObject result = transformer.transform(address, context);

        assertThat(result).isNotNull();
        assertThat(result.containsKey(DSPACE_SCHEMA + ENDPOINT_TYPE_PROPERTY_TERM)).isTrue();
        assertThat(result.containsKey(DSPACE_SCHEMA + ENDPOINT_PROPERTIES_PROPERTY_TERM)).isTrue();
    }

    @Test
    void transform_addressWithNestedDataAddress_shouldTransformRecursively() {
        var nestedAddress = DataAddress.Builder.newInstance().type("Nested").build();
        var nestedJson = Json.createObjectBuilder().add("type", "Nested").build();
        when(context.transform(nestedAddress, JsonObject.class)).thenReturn(nestedJson);

        var address = DataAddress.Builder.newInstance()
                .type("HttpData")
                .property("nested", nestedAddress)
                .build();

        var result = transformer.transform(address, context);

        assertThat(result).isNotNull();
        verify(context).transform(nestedAddress, JsonObject.class);
    }

    @Test
    void transform_addressWithStructuredValue_shouldConvertValueToJsonObject() {
        var typeManager = mock(TypeManager.class);
        var objectMapper = mock(ObjectMapper.class);
        var convertedValue = Json.createObjectBuilder().add("region", "eu").build();
        var localTransformer = new NullSafeJsonObjectFromDataAddressDspaceTransformer(Json.createBuilderFactory(Map.of()), typeManager, JSON_LD);
        when(typeManager.getMapper(JSON_LD)).thenReturn(objectMapper);
        when(objectMapper.convertValue(Map.of("region", "eu"), JsonObject.class)).thenReturn(convertedValue);

        var address = DataAddress.Builder.newInstance()
                .type("HttpData")
                .property("metadata", Map.of("region", "eu"))
                .build();

        var result = localTransformer.transform(address, context);

        assertThat(result).isNotNull();
        var metadataProperty = result.getJsonArray(DSPACE_SCHEMA + ENDPOINT_PROPERTIES_PROPERTY_TERM).stream()
                .map(value -> value.asJsonObject())
                .filter(value -> value.getString(DSPACE_SCHEMA + ENDPOINT_PROPERTY_NAME_PROPERTY_TERM).equals("metadata"))
                .findFirst()
                .orElseThrow();

        assertThat(metadataProperty.getJsonObject(DSPACE_SCHEMA + ENDPOINT_PROPERTY_VALUE_PROPERTY_TERM)
                .getString("region")).isEqualTo("eu");
    }

    @Test
    void getInputType_shouldReturnDataAddress() {
        assertThat(transformer.getInputType()).isEqualTo(DataAddress.class);
    }

    @Test
    void getOutputType_shouldReturnJsonObject() {
        assertThat(transformer.getOutputType()).isEqualTo(JsonObject.class);
    }
}
