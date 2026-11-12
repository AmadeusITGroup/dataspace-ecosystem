package org.eclipse.edc.dse.controlplane.asset.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.dse.controlplane.asset.validation.AssetOntologyValidationExtension.DEFAULT_PROFILE_IRI;
import static org.eclipse.edc.dse.controlplane.asset.validation.OntologyApiController.ONTOLOGY_ID;

class OntologyApiControllerTest {

    private OntologyApiController controller;

    @BeforeEach
    void setUp() {
        var mapper = new ObjectMapper();
        controller = new OntologyApiController(DEFAULT_PROFILE_IRI, OntologyProfile.load(mapper), mapper);
    }

    @Test
    void shouldListConciseOntologyDescriptions() {
        var ontologies = controller.getOntologies();

        assertThat(ontologies).hasSize(1);
        var ontology = ontologies.get(0);
        assertThat(ontology.id()).isEqualTo(ONTOLOGY_ID);
        assertThat(ontology.ontologyName()).isEqualTo("INRIA DXP IATA Open Air 24.4.0");
        assertThat(ontology.description()).contains("IATA Open Air 24.4.0");
    }

    @Test
    void shouldReturnOntologySchema() {
        try (var response = controller.getOntology(ONTOLOGY_ID)) {
            assertThat(response.getStatus()).isEqualTo(200);
            var details = (OntologyApiController.OntologyDetails) response.getEntity();
            assertThat(details.namespace()).isEqualTo(OntologyProfile.IATA_NAMESPACE);
                assertThat(details.publication().triggerValue()).isEqualTo(DEFAULT_PROFILE_IRI);
                assertThat(details.publication().openApiDocumentProperty()).isEqualTo("dcat:endpointDescription");
                assertThat(details.publication().openApiClassAnnotation()).isEqualTo("x-dxp-ontology-class");
            assertThat(details.openApiSchema().at("/components/schemas/Ack/title").asText())
                    .isEqualTo("Acknowledgement");
        }
    }

    @Test
    void shouldReturnNotFoundForUnknownOntology() {
        try (var response = controller.getOntology("unknown")) {
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(response.getEntity()).isEqualTo(
                    new OntologyApiController.ErrorResponse("Ontology 'unknown' was not found"));
        }
    }
}