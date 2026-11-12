package org.eclipse.edc.dse.controlplane.asset.validation;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.validator.spi.Validator;
import org.eclipse.edc.validator.spi.Violation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset.EDC_ASSET_PROPERTIES;

class AssetOntologyValidator implements Validator<JsonObject> {

    static final String DCAT_DISTRIBUTION = "http://www.w3.org/ns/dcat#distribution";
    static final String DCAT_ACCESS_SERVICE = "http://www.w3.org/ns/dcat#accessService";
    static final String DCAT_ENDPOINT_DESCRIPTION = "http://www.w3.org/ns/dcat#endpointDescription";
    static final String DCT_CONFORMS_TO = "http://purl.org/dc/terms/conformsTo";

    private final String profileIri;
    private final OpenApiDocumentLoader documentLoader;
    private final OpenApiOntologyValidator openApiValidator;

    AssetOntologyValidator(String profileIri, OpenApiDocumentLoader documentLoader, OpenApiOntologyValidator openApiValidator) {
        this.profileIri = profileIri;
        this.documentLoader = documentLoader;
        this.openApiValidator = openApiValidator;
    }

    @Override
    public ValidationResult validate(JsonObject asset) {
        var endpointDescriptions = findEndpointDescriptions(asset);
        if (endpointDescriptions == null) {
            return ValidationResult.success();
        }
        if (endpointDescriptions.isEmpty()) {
            return failure("An asset conforming to '%s' must define dcat:endpointDescription".formatted(profileIri));
        }
        if (endpointDescriptions.size() > 1) {
            return failure("An asset conforming to '%s' must define exactly one OpenAPI endpoint description".formatted(profileIri));
        }

        try {
            var document = documentLoader.load(endpointDescriptions.iterator().next());
            var violations = openApiValidator.validate(document);
            if (!violations.isEmpty()) {
                return ValidationResult.failure(violations.stream()
                        .map(message -> Violation.violation(message, DCAT_ENDPOINT_DESCRIPTION))
                        .toList());
            }
            return ValidationResult.success();
        } catch (RuntimeException exception) {
            return failure("DXP ontology validation failed: " + exception.getMessage());
        }
    }

    private LinkedHashSet<String> findEndpointDescriptions(JsonObject asset) {
        var properties = firstObject(asset, EDC_ASSET_PROPERTIES, "properties");
        if (properties == null) {
            return null;
        }

        var optedIn = false;
        var descriptions = new LinkedHashSet<String>();
        for (var distribution : objects(properties, DCAT_DISTRIBUTION, "dcat:distribution")) {
            for (var service : objects(distribution, DCAT_ACCESS_SERVICE, "dcat:accessService")) {
                if (values(service, DCT_CONFORMS_TO, "dcterms:conformsTo", "dct:conformsTo").contains(profileIri)) {
                    optedIn = true;
                    descriptions.addAll(values(service, DCAT_ENDPOINT_DESCRIPTION, "dcat:endpointDescription"));
                }
            }
        }
        return optedIn ? descriptions : null;
    }

    private ValidationResult failure(String message) {
        return ValidationResult.failure(Violation.violation(message, EDC_ASSET_PROPERTIES));
    }

    private static JsonObject firstObject(JsonObject object, String... keys) {
        for (var candidate : objects(object, keys)) {
            return candidate;
        }
        return null;
    }

    private static List<JsonObject> objects(JsonObject object, String... keys) {
        var result = new ArrayList<JsonObject>();
        for (var key : keys) {
            var value = object.get(key);
            if (value == null) {
                continue;
            }
            if (value.getValueType() == JsonValue.ValueType.OBJECT) {
                result.add(value.asJsonObject());
            } else if (value.getValueType() == JsonValue.ValueType.ARRAY) {
                value.asJsonArray().stream()
                        .filter(item -> item.getValueType() == JsonValue.ValueType.OBJECT)
                        .map(JsonValue::asJsonObject)
                        .forEach(result::add);
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        return result;
    }

    private static List<String> values(JsonObject object, String... keys) {
        for (var key : keys) {
            var value = object.get(key);
            if (value == null) {
                continue;
            }
            var array = value.getValueType() == JsonValue.ValueType.ARRAY
                    ? value.asJsonArray()
                    : Json.createArrayBuilder().add(value).build();
            return array.stream().map(AssetOntologyValidator::stringValue).filter(valueString -> valueString != null).toList();
        }
        return List.of();
    }

    private static String stringValue(JsonValue value) {
        if (value.getValueType() == JsonValue.ValueType.STRING) {
            return ((JsonString) value).getString();
        }
        if (value.getValueType() == JsonValue.ValueType.OBJECT) {
            var object = value.asJsonObject();
            for (var key : List.of("@id", "@value")) {
                if (object.containsKey(key)) {
                    return stringValue(object.get(key));
                }
            }
        }
        return null;
    }
}