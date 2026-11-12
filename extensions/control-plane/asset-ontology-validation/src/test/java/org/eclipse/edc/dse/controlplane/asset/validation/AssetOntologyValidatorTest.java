package org.eclipse.edc.dse.controlplane.asset.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset.EDC_ASSET_PROPERTIES;

class AssetOntologyValidatorTest {

    private final OpenApiOntologyValidator openApiValidator = new OpenApiOntologyValidator(
            OntologyProfile.load(new ObjectMapper()), new ObjectMapper());

    @Test
    void shouldSkipAssetWithoutProfileMarker() {
        var loader = new StubLoader();
        var validator = new AssetOntologyValidator(AssetOntologyValidationExtension.DEFAULT_PROFILE_IRI, loader, openApiValidator);
        var asset = Json.createObjectBuilder()
                .add(EDC_ASSET_PROPERTIES, Json.createArrayBuilder().add(Json.createObjectBuilder().add("name", "asset")))
                .build();

        assertThat(validator.validate(asset).succeeded()).isTrue();
        assertThat(loader.called).isFalse();
    }

    @Test
    void shouldFailClosedWhenOptedInAssetHasNoEndpointDescription() {
        var validator = new AssetOntologyValidator(AssetOntologyValidationExtension.DEFAULT_PROFILE_IRI,
                new StubLoader(), openApiValidator);

        assertThat(validator.validate(optedInAsset(null)).failed()).isTrue();
    }

    @Test
    void shouldReadExpandedJsonLdAndValidateReferencedDocument() {
        var loader = new StubLoader();
        loader.document = OpenApiOntologyValidatorTest.VALID_DOCUMENT;
        var validator = new AssetOntologyValidator(AssetOntologyValidationExtension.DEFAULT_PROFILE_IRI, loader, openApiValidator);

        assertThat(validator.validate(optedInAsset("https://api.example.com/openapi.yaml")).succeeded()).isTrue();
        assertThat(loader.called).isTrue();
    }

    private jakarta.json.JsonObject optedInAsset(String endpointDescription) {
        var service = Json.createObjectBuilder()
                .add(AssetOntologyValidator.DCT_CONFORMS_TO, Json.createArrayBuilder().add(Json.createObjectBuilder()
                        .add("@id", AssetOntologyValidationExtension.DEFAULT_PROFILE_IRI)));
        if (endpointDescription != null) {
            service.add(AssetOntologyValidator.DCAT_ENDPOINT_DESCRIPTION, Json.createArrayBuilder().add(Json.createObjectBuilder()
                    .add("@id", endpointDescription)));
        }
        return Json.createObjectBuilder()
                .add(EDC_ASSET_PROPERTIES, Json.createArrayBuilder().add(Json.createObjectBuilder()
                        .add(AssetOntologyValidator.DCAT_DISTRIBUTION, Json.createArrayBuilder().add(Json.createObjectBuilder()
                                .add(AssetOntologyValidator.DCAT_ACCESS_SERVICE, Json.createArrayBuilder().add(service))))))
                .build();
    }

    private static class StubLoader extends OpenApiDocumentLoader {
        private boolean called;
        private String document = "";

        StubLoader() {
            super(Set.of(), 1024, Duration.ofSeconds(1));
        }

        @Override
        String load(String location) {
            called = true;
            return document;
        }
    }
}