package org.eclipse.edc.dse.controlplane.asset.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

class OntologyProfile {

    static final String IATA_NAMESPACE = "http://www.iata.org/ontology#";
    private static final String ORIGINAL_SCHEMA = "/ontology/iata-open-air-24.4.0.json";
    private static final String CURATED_SCHEMA = "/ontology/inria-dxp-iata-24.4.0-curated.json";
    private static final String ONTOLOGY = "/ontology/inria-dxp-iata-24.4.0.nt";
    private static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema#";

    private final JsonNode schemas;
    private final JsonNode curatedSchemas;
    private final Model ontology;

    private OntologyProfile(JsonNode schemas, JsonNode curatedSchemas, Model ontology) {
        this.schemas = schemas;
        this.curatedSchemas = curatedSchemas;
        this.ontology = ontology;
    }

    static OntologyProfile load(ObjectMapper mapper) {
        try (var original = resource(ORIGINAL_SCHEMA);
                var curated = resource(CURATED_SCHEMA);
                var triples = resource(ONTOLOGY)) {
            var schemas = mapper.readTree(original).path("components").path("schemas");
            var curatedSchemas = mapper.readTree(curated).path("components").path("schemas");
            var ontology = ModelFactory.createDefaultModel();
            ontology.read(triples, null, "N-TRIPLES");
            if (schemas.isMissingNode() || !schemas.isObject() || curatedSchemas.isMissingNode() || !curatedSchemas.isObject()) {
                throw new IllegalStateException("Packaged IATA schemas do not contain components.schemas");
            }
            return new OntologyProfile(schemas, curatedSchemas, ontology);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load packaged DXP ontology profile", exception);
        }
    }

    Optional<JsonNode> schema(String classIri) {
        return className(classIri).map(schemas::path).filter(JsonNode::isObject);
    }

    JsonNode curatedSchemas() {
        return curatedSchemas;
    }

    Optional<String> className(String classIri) {
        if (classIri == null || !classIri.startsWith(IATA_NAMESPACE) || classIri.length() == IATA_NAMESPACE.length()) {
            return Optional.empty();
        }
        var name = classIri.substring(IATA_NAMESPACE.length());
        return ontology.contains(ontology.createResource(classIri), RDF.type, RDFS.Class) &&
            curatedSchemas.has(name) ? Optional.of(name) : Optional.empty();
    }

    boolean hasOntologyProperty(String classIri, String jsonProperty, JsonNode sourceProperty) {
        var expectedRange = expectedRange(sourceProperty);
        if (expectedRange == null) {
            return false;
        }

        for (var domain : domains(classIri)) {
            var properties = ontology.listResourcesWithProperty(RDFS.domain, domain);
            try {
                while (properties.hasNext()) {
                    var property = properties.next();
                    var localName = property.getURI().substring(IATA_NAMESPACE.length());
                    if ((localName.equals(jsonProperty) || localName.endsWith("." + jsonProperty)) &&
                            ontology.contains(property, RDFS.range, ontology.createResource(expectedRange))) {
                        return true;
                    }
                }
            } finally {
                properties.close();
            }
        }
        return false;
    }

    private Set<Resource> domains(String classIri) {
        var result = new HashSet<Resource>();
        var queue = new ArrayDeque<Resource>();
        queue.add(ontology.createResource(classIri));
        var generatedSubclassOf = ontology.createProperty(RDFS.getURI(), "subclassOf");
        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            if (!result.add(current)) {
                continue;
            }
            for (var predicate : Set.of(RDFS.subClassOf, generatedSubclassOf)) {
                var superclasses = ontology.listObjectsOfProperty(current, predicate);
                try {
                    superclasses.filterKeep(node -> node.isResource()).mapWith(node -> node.asResource()).forEachRemaining(queue::add);
                } finally {
                    superclasses.close();
                }
            }
        }
        return result;
    }

    private String expectedRange(JsonNode sourceProperty) {
        var value = sourceProperty;
        if ("array".equals(value.path("type").asText())) {
            value = value.path("items");
        }
        var reference = value.path("$ref").asText(value.path("x-iata-$ref").asText(null));
        if (reference != null) {
            return IATA_NAMESPACE + reference.substring(reference.lastIndexOf('/') + 1);
        }
        return switch (value.path("type").asText()) {
            case "boolean" -> XSD_NAMESPACE + "boolean";
            case "integer", "number" -> XSD_NAMESPACE + "integer";
            case "string" -> XSD_NAMESPACE + "string";
            default -> null;
        };
    }

    private static java.io.InputStream resource(String path) {
        var stream = OntologyProfile.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("Missing packaged ontology resource " + path);
        }
        return stream;
    }
}