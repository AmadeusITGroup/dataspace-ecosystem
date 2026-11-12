package org.eclipse.edc.dse.controlplane.asset.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Exposes the ontology profiles available for asset publication validation.
 */
@Path("/v1alpha/ontologies")
@Produces(MediaType.APPLICATION_JSON)
public class OntologyApiController {

    static final String ONTOLOGY_ID = "inria-iata-24.4.0";
    static final String ONTOLOGY_NAME = "INRIA DXP IATA Open Air 24.4.0";
    static final String ONTOLOGY_DESCRIPTION =
            "INRIA-curated profile for validating OpenAPI response schemas against IATA Open Air 24.4.0.";
    static final String ONTOLOGY_VERSION = "24.4.0";
    static final String CLASS_ANNOTATION = "x-dxp-ontology-class";

    private final String profileIri;
    private final OntologyProfile profile;
    private final ObjectMapper mapper;

    public OntologyApiController(String profileIri, OntologyProfile profile, ObjectMapper mapper) {
        this.profileIri = profileIri;
        this.profile = profile;
        this.mapper = mapper;
    }

        /**
         * Lists concise descriptions of the available ontology profiles.
         */
    @GET
    public List<OntologyDescriptor> getOntologies() {
                return List.of(new OntologyDescriptor(ONTOLOGY_ID, ONTOLOGY_NAME, ONTOLOGY_DESCRIPTION));
    }

    /**
     * Returns the selected ontology profile and the schemas used to validate OpenAPI responses.
     */
    @GET
    @Path("/{id}")
    public Response getOntology(@PathParam("id") String id) {
        if (!ONTOLOGY_ID.equals(id)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Ontology '%s' was not found".formatted(id)))
                    .build();
        }

        var schema = mapper.createObjectNode()
                .set("components", mapper.createObjectNode().set("schemas", profile.curatedSchemas()));
        var details = new OntologyDetails(ONTOLOGY_ID, ONTOLOGY_NAME, ONTOLOGY_VERSION, profileIri,
                OntologyProfile.IATA_NAMESPACE, publicationGuide(), schema);
        return Response.ok(details).build();
    }

    private PublicationGuide publicationGuide() {
        var assetMetadata = mapper.createObjectNode();
        var dataService = mapper.createObjectNode();
        dataService.set("dcterms:conformsTo", mapper.createObjectNode().put("@id", profileIri));
        dataService.set("dcat:endpointDescription",
                mapper.createObjectNode().put("@id", "https://api.example.com/openapi.yaml"));
        assetMetadata.set("dcat:distribution", mapper.createArrayNode().add(
                mapper.createObjectNode().set("dcat:accessService", dataService)));

        var openApiSchema = mapper.createObjectNode()
                .put("type", "object")
                .put(CLASS_ANNOTATION, OntologyProfile.IATA_NAMESPACE + "Ack");

        return new PublicationGuide("dcterms:conformsTo", profileIri, "dcat:endpointDescription",
                CLASS_ANNOTATION, assetMetadata, openApiSchema);
    }

        public record OntologyDescriptor(String id, String ontologyName, String description) {
    }

    public record OntologyDetails(String id, String ontologyName, String version, String profileIri,
                                  String namespace, PublicationGuide publication, JsonNode openApiSchema) {
    }

    public record PublicationGuide(String triggerProperty, String triggerValue, String openApiDocumentProperty,
                                   String openApiClassAnnotation, JsonNode assetMetadataExample,
                                   JsonNode openApiSchemaExample) {
    }

    public record ErrorResponse(String message) {
    }
}