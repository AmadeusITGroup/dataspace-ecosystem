package org.eclipse.edc.dse.controlplane.asset.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DemoFixturesTest {

    private static final Path EXAMPLES = Path.of("examples");
    private final OpenApiOntologyValidator openApiValidator = new OpenApiOntologyValidator(
            OntologyProfile.load(new ObjectMapper()), new ObjectMapper());

    @Test
    void validAssetAndOpenApiShouldPass() throws IOException {
        var validator = new AssetOntologyValidator(AssetOntologyValidationExtension.DEFAULT_PROFILE_IRI,
                new FixtureLoader(), openApiValidator);

        assertThat(validator.validate(readAsset("valid-asset.json")).succeeded()).isTrue();
    }

    @Test
    void invalidAssetAndOpenApiShouldFailForExtraProperty() throws IOException {
        var validator = new AssetOntologyValidator(AssetOntologyValidationExtension.DEFAULT_PROFILE_IRI,
                new FixtureLoader(), openApiValidator);

        var result = validator.validate(readAsset("invalid-asset.json"));

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailure().getViolations())
                .anyMatch(violation -> violation.message().contains("debugCode is not defined by IATA class Ack"));
    }

    private JsonObject readAsset(String name) throws IOException {
        try (var reader = Json.createReader(Files.newBufferedReader(EXAMPLES.resolve(name)))) {
            return reader.readObject();
        }
    }

    private static class FixtureLoader extends OpenApiDocumentLoader {

        FixtureLoader() {
            super(Set.of(), 1024, Duration.ofSeconds(1));
        }

        @Override
        String load(String location) {
            var fileName = location.substring(location.lastIndexOf('/') + 1);
            try {
                return Files.readString(EXAMPLES.resolve(fileName));
            } catch (IOException exception) {
                throw new IllegalArgumentException(exception);
            }
        }
    }
}