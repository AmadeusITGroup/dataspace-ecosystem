package org.eclipse.edc.dse.controlplane;

import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.stream.JsonCollectors;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.eclipse.edc.jsonld.spi.JsonLdKeywords;
import org.eclipse.edc.jsonld.spi.Namespaces;
import org.eclipse.edc.transform.transformer.dspace.DataAddressDspaceSerialization;

/**
 * Null-safe variant of {@code JsonObjectFromDataAddressDspaceTransformer}.
 * Filters out properties with null values to prevent NPE in {@code JsonObjectBuilder.add()}.
 */
public class NullSafeJsonObjectFromDataAddressDspaceTransformer extends AbstractJsonLdTransformer<DataAddress, JsonObject> {

    private final JsonBuilderFactory jsonFactory;
    private final TypeManager typeManager;
    private final String typeContext;

    public NullSafeJsonObjectFromDataAddressDspaceTransformer(JsonBuilderFactory jsonFactory, TypeManager typeManager, String typeContext) {
        super(DataAddress.class, JsonObject.class);
        this.jsonFactory = jsonFactory;
        this.typeManager = typeManager;
        this.typeContext = typeContext;
    }

    @Override
    public @Nullable JsonObject transform(@NotNull DataAddress dataAddress, @NotNull TransformerContext context) {
        var endpointProperties = dataAddress.getProperties().entrySet().stream()
                .filter(e -> !DataAddress.EDC_DATA_ADDRESS_TYPE_PROPERTY.equals(e.getKey()))
                .filter(e -> e.getValue() != null)
                .map(it -> endpointProperty(it.getKey(), it.getValue(), context))
                .collect(JsonCollectors.toJsonArray());

        var schema = Namespaces.DSPACE_SCHEMA;
        return jsonFactory.createObjectBuilder()
                .add(JsonLdKeywords.TYPE, schema + DataAddressDspaceSerialization.DSPACE_DATAADDRESS_TYPE_TERM)
                .add(schema + DataAddressDspaceSerialization.ENDPOINT_TYPE_PROPERTY_TERM, dataAddress.getType())
                .add(schema + DataAddressDspaceSerialization.ENDPOINT_PROPERTIES_PROPERTY_TERM, endpointProperties)
                .build();
    }

    private JsonObject endpointProperty(String key, Object value, TransformerContext context) {
        var schema = Namespaces.DSPACE_SCHEMA;
        var builder = jsonFactory.createObjectBuilder()
                .add(JsonLdKeywords.TYPE, schema + DataAddressDspaceSerialization.ENDPOINT_PROPERTY_PROPERTY_TYPE_TERM)
                .add(schema + DataAddressDspaceSerialization.ENDPOINT_PROPERTY_NAME_PROPERTY_TERM, key);

        if (value instanceof String stringVal) {
            builder.add(schema + DataAddressDspaceSerialization.ENDPOINT_PROPERTY_VALUE_PROPERTY_TERM, stringVal);
        } else if (value instanceof DataAddress da) {
            var transformedAddress = context.transform(da, JsonObject.class);
            if (transformedAddress != null) {
                builder.add(schema + DataAddressDspaceSerialization.ENDPOINT_PROPERTY_VALUE_PROPERTY_TERM, transformedAddress);
            }
        } else {
            var convertedValue = typeManager.getMapper(typeContext).convertValue(value, JsonObject.class);
            if (convertedValue != null) {
                builder.add(schema + DataAddressDspaceSerialization.ENDPOINT_PROPERTY_VALUE_PROPERTY_TERM, convertedValue);
            }
        }

        return builder.build();
    }
}
