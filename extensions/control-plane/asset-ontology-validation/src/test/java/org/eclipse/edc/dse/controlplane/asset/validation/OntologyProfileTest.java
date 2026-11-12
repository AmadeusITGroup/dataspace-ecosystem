package org.eclipse.edc.dse.controlplane.asset.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OntologyProfileTest {

    private final OntologyProfile profile = OntologyProfile.load(new ObjectMapper());

    @Test
    void shouldLoadKnownClassAndDirectProperty() {
        var classIri = OntologyProfile.IATA_NAMESPACE + "Ack";
        var sourceProperty = profile.schema(classIri).orElseThrow().path("properties").path("statusMessageText");

        assertThat(profile.className(classIri)).contains("Ack");
        assertThat(profile.hasOntologyProperty(classIri, "statusMessageText", sourceProperty)).isTrue();
    }

    @Test
    void shouldResolvePropertyInheritedFromGeneratedSuperclass() {
        var classIri = OntologyProfile.IATA_NAMESPACE + "AccessGate";
        var sourceProperty = profile.schema(classIri).orElseThrow().path("properties").path("terminal");

        assertThat(profile.hasOntologyProperty(classIri, "terminal", sourceProperty)).isTrue();
    }
}