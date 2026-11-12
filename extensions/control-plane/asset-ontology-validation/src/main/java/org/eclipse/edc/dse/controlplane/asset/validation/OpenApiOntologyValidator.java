package org.eclipse.edc.dse.controlplane.asset.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class OpenApiOntologyValidator {

    static final String ONTOLOGY_CLASS_EXTENSION = "x-dxp-ontology-class";
    private static final int MAX_SCHEMA_DEPTH = 128;

    private final OntologyProfile profile;
    private final ObjectMapper mapper;

    OpenApiOntologyValidator(OntologyProfile profile, ObjectMapper mapper) {
        this.profile = profile;
        this.mapper = mapper;
    }

    List<String> validate(String document) {
        var options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        var parseResult = new OpenAPIV3Parser().readContents(document, null, options);
        if (parseResult.getOpenAPI() == null) {
            return parseResult.getMessages().isEmpty()
                    ? List.of("dcat:endpointDescription does not contain a valid OpenAPI document")
                    : parseResult.getMessages();
        }

        var violations = new ArrayList<String>();
        if (!parseResult.getMessages().isEmpty()) {
            violations.addAll(parseResult.getMessages());
        }
        var annotatedResponses = 0;
        var openApi = parseResult.getOpenAPI();
        if (openApi.getPaths() != null) {
            for (var pathEntry : openApi.getPaths().entrySet()) {
                for (var operationEntry : pathEntry.getValue().readOperationsMap().entrySet()) {
                    annotatedResponses += validateOperation(openApi, pathEntry.getKey(), operationEntry.getKey().name(),
                            operationEntry.getValue(), violations);
                }
            }
        }
        if (annotatedResponses == 0) {
            violations.add("The OpenAPI document must contain at least one successful JSON response schema annotated with " +
                    ONTOLOGY_CLASS_EXTENSION);
        }
        return violations;
    }

    private int validateOperation(OpenAPI openApi, String path, String method, Operation operation, List<String> violations) {
        if (operation.getResponses() == null) {
            return 0;
        }
        var annotated = 0;
        for (var responseEntry : operation.getResponses().entrySet()) {
            if (!responseEntry.getKey().startsWith("2")) {
                continue;
            }
            var responsePath = "%s %s response %s".formatted(method, path, responseEntry.getKey());
            var response = resolveResponse(responseEntry.getValue(), openApi, responsePath, violations);
            if (response == null || response.getContent() == null) {
                continue;
            }
            for (var contentEntry : response.getContent().entrySet()) {
                if (!isJsonMediaType(contentEntry.getKey()) || contentEntry.getValue().getSchema() == null) {
                    continue;
                }
                var schemaPath = responsePath + " " + contentEntry.getKey();
                var schema = contentEntry.getValue().getSchema();
                var annotatedSchema = annotatedSchema(schema, openApi, schemaPath, violations);
                if (annotatedSchema == null) {
                    continue;
                }
                annotated++;
                validateSchema(annotatedSchema.schema(), annotatedSchema.classIri(), openApi, schemaPath,
                    new HashSet<>(), 0, violations);
                validateExamples(contentEntry.getValue(), schema, openApi, schemaPath, violations);
            }
        }
        return annotated;
    }

    private ApiResponse resolveResponse(ApiResponse response, OpenAPI openApi, String path, List<String> violations) {
        if (response.get$ref() == null) {
            return response;
        }
        var prefix = "#/components/responses/";
        if (!response.get$ref().startsWith(prefix) || openApi.getComponents() == null || openApi.getComponents().getResponses() == null) {
            violations.add(path + " uses an unsupported external or unresolved response reference: " + response.get$ref());
            return null;
        }
        var resolved = openApi.getComponents().getResponses().get(response.get$ref().substring(prefix.length()));
        if (resolved == null) {
            violations.add(path + " references a missing response: " + response.get$ref());
        }
        return resolved;
    }

    private AnnotatedSchema annotatedSchema(Schema<?> schema, OpenAPI openApi, String path, List<String> violations) {
        var candidate = schema;
        var value = extensionValue(candidate);
        if ("array".equals(schema.getType())) {
            candidate = schema.getItems();
        }
        candidate = resolveSchema(candidate, openApi, path, violations);
        if (candidate == null) {
            return null;
        }
        if (value == null) {
            value = extensionValue(candidate);
        }
        return value instanceof String classIri ? new AnnotatedSchema(candidate, classIri) : null;
    }

    private Object extensionValue(Schema<?> schema) {
        return schema == null || schema.getExtensions() == null
                ? null
                : schema.getExtensions().get(ONTOLOGY_CLASS_EXTENSION);
    }

    private void validateSchema(Schema<?> apiSchema, String classIri, OpenAPI openApi, String path,
                                Set<String> visited, int depth, List<String> violations) {
        if (depth > MAX_SCHEMA_DEPTH) {
            violations.add(path + " exceeds the maximum supported schema depth of " + MAX_SCHEMA_DEPTH);
            return;
        }
        var className = profile.className(classIri).orElse(null);
        var sourceSchema = profile.schema(classIri).orElse(null);
        if (className == null || sourceSchema == null) {
            violations.add(path + " declares an unknown DXP ontology class: " + classIri);
            return;
        }
        var resolved = resolveSchema(apiSchema, openApi, path, violations);
        if (resolved == null || !visited.add(className + "@" + System.identityHashCode(resolved))) {
            return;
        }
        var sourceType = sourceSchema.path("type").asText(null);
        if (sourceType != null && !"object".equals(sourceType)) {
            if (!sourceType.equals(resolved.getType())) {
                violations.add(path + " must have type '" + sourceType + "' instead of '" + resolved.getType() + "'");
            }
            return;
        }
        if (!isObjectSchema(resolved)) {
            violations.add(path + " must be an object schema for ontology class " + classIri);
            return;
        }

        var sourceProperties = sourceSchema.path("properties");
        var apiProperties = properties(resolved);
        var apiRequired = resolved.getRequired() == null ? Set.<String>of() : Set.copyOf(resolved.getRequired());
        var sourceRequired = sourceSchema.path("required");
        if (sourceRequired.isArray()) {
            sourceRequired.forEach(required -> {
                if (!apiRequired.contains(required.asText())) {
                    violations.add(path + " is missing required IATA property '" + required.asText() + "'");
                }
            });
        }

        for (var propertyEntry : apiProperties.entrySet()) {
            var propertyPath = path + "." + propertyEntry.getKey();
            var sourceProperty = sourceProperties.path(propertyEntry.getKey());
            if (sourceProperty.isMissingNode()) {
                violations.add(propertyPath + " is not defined by IATA class " + className);
                continue;
            }
            if (!profile.hasOntologyProperty(classIri, propertyEntry.getKey(), sourceProperty)) {
                violations.add(propertyPath + " has no matching property and range in the DXP ontology");
                continue;
            }
            validateProperty(propertyEntry.getValue(), sourceProperty, openApi, propertyPath, visited, depth + 1, violations);
        }
    }

    private void validateProperty(Schema<?> apiProperty, JsonNode sourceProperty, OpenAPI openApi, String path,
                                  Set<String> visited, int depth, List<String> violations) {
        if ("array".equals(sourceProperty.path("type").asText())) {
            var resolved = resolveSchema(apiProperty, openApi, path, violations);
            if (resolved == null || !"array".equals(resolved.getType()) || resolved.getItems() == null) {
                violations.add(path + " must be an array");
                return;
            }
            validateProperty(resolved.getItems(), sourceProperty.path("items"), openApi, path + "[]", visited, depth + 1, violations);
            return;
        }

        var expectedReference = referenceName(sourceProperty);
        if (expectedReference != null) {
            var actualReference = referenceName(apiProperty);
            if (actualReference != null && !expectedReference.equals(actualReference)) {
                violations.add(path + " must reference IATA class " + expectedReference + " instead of " + actualReference);
                return;
            }
            var nested = resolveSchema(apiProperty, openApi, path, violations);
            if (nested != null) {
                validateSchema(nested, OntologyProfile.IATA_NAMESPACE + expectedReference, openApi, path, visited, depth + 1, violations);
            }
            return;
        }

        var expectedType = sourceProperty.path("type").asText(null);
        var resolved = resolveSchema(apiProperty, openApi, path, violations);
        var actualType = resolved == null ? null : resolved.getType();
        if (expectedType != null && !expectedType.equals(actualType)) {
            violations.add(path + " must have type '" + expectedType + "' instead of '" + actualType + "'");
        }
    }

    private void validateExamples(io.swagger.v3.oas.models.media.MediaType mediaType, Schema<?> schema, OpenAPI openApi,
                                  String path, List<String> violations) {
        if (mediaType.getExample() != null) {
            validateExample(mapper.valueToTree(mediaType.getExample()), schema, openApi, path + " example", 0, violations);
        }
        if (mediaType.getExamples() != null) {
            mediaType.getExamples().forEach((name, example) -> {
                if (example.getExternalValue() != null) {
                    violations.add(path + " example '" + name + "' uses an unsupported externalValue");
                } else if (example.getValue() != null) {
                    validateExample(mapper.valueToTree(example.getValue()), schema, openApi,
                            path + " example '" + name + "'", 0, violations);
                }
            });
        }
        if (schema.getExample() != null) {
            validateExample(mapper.valueToTree(schema.getExample()), schema, openApi, path + " schema example", 0, violations);
        }
    }

    private void validateExample(JsonNode value, Schema<?> schema, OpenAPI openApi, String path, int depth, List<String> violations) {
        if (depth > MAX_SCHEMA_DEPTH) {
            violations.add(path + " exceeds the maximum supported example depth of " + MAX_SCHEMA_DEPTH);
            return;
        }
        var resolved = resolveSchema(schema, openApi, path, violations);
        if (resolved == null) {
            return;
        }
        if ("array".equals(resolved.getType())) {
            if (!value.isArray()) {
                violations.add(path + " must be an array");
            } else if (resolved.getItems() != null) {
                for (var index = 0; index < value.size(); index++) {
                    validateExample(value.get(index), resolved.getItems(), openApi, path + "[" + index + "]", depth + 1, violations);
                }
            }
        } else if (isObjectSchema(resolved)) {
            if (!value.isObject()) {
                violations.add(path + " must be an object");
                return;
            }
            if (resolved.getRequired() != null) {
                resolved.getRequired().stream().filter(required -> !value.has(required))
                        .forEach(required -> violations.add(path + " is missing required property '" + required + "'"));
            }
            for (var property : properties(resolved).entrySet()) {
                if (value.has(property.getKey())) {
                    validateExample(value.get(property.getKey()), property.getValue(), openApi,
                            path + "." + property.getKey(), depth + 1, violations);
                }
            }
        } else if (!matchesType(value, resolved.getType())) {
            violations.add(path + " does not match schema type '" + resolved.getType() + "'");
        }
    }

    private Schema<?> resolveSchema(Schema<?> schema, OpenAPI openApi, String path, List<String> violations) {
        if (schema == null || schema.get$ref() == null) {
            return schema;
        }
        var prefix = "#/components/schemas/";
        if (!schema.get$ref().startsWith(prefix) || openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            violations.add(path + " uses an unsupported external or unresolved schema reference: " + schema.get$ref());
            return null;
        }
        var resolved = openApi.getComponents().getSchemas().get(schema.get$ref().substring(prefix.length()));
        if (resolved == null) {
            violations.add(path + " references a missing schema: " + schema.get$ref());
        }
        return resolved;
    }

    private String referenceName(Schema<?> schema) {
        return schema != null && schema.get$ref() != null
                ? schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1)
                : null;
    }

    private String referenceName(JsonNode schema) {
        var reference = schema.path("$ref").asText(schema.path("x-iata-$ref").asText(null));
        return reference == null ? null : reference.substring(reference.lastIndexOf('/') + 1);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Schema<?>> properties(Schema<?> schema) {
        return schema.getProperties() == null ? Map.of() : (Map<String, Schema<?>>) (Map<?, ?>) schema.getProperties();
    }

    private boolean isObjectSchema(Schema<?> schema) {
        return "object".equals(schema.getType()) || schema.getProperties() != null;
    }

    private boolean isJsonMediaType(String value) {
        var mediaType = value.split(";", 2)[0].trim().toLowerCase();
        return "application/json".equals(mediaType) || "application/ld+json".equals(mediaType) || mediaType.endsWith("+json");
    }

    private boolean matchesType(JsonNode value, String type) {
        return switch (type == null ? "" : type) {
            case "array" -> value.isArray();
            case "object" -> value.isObject();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            default -> true;
        };
    }

    private record AnnotatedSchema(Schema<?> schema, String classIri) {
    }
}