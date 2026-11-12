package org.eclipse.edc.dse.controlplane.asset.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiOntologyValidatorTest {

    static final String VALID_DOCUMENT = """
            openapi: 3.0.3
            info:
              title: Ack API
              version: 1.0.0
            paths:
              /ack:
                get:
                  responses:
                    '200':
                      description: Acknowledged
                      content:
                        application/json:
                          schema:
                            $ref: '#/components/schemas/Ack'
                          example:
                            statusCode: OK
            components:
              schemas:
                Ack:
                  type: object
                  x-dxp-ontology-class: http://www.iata.org/ontology#Ack
                  required: [statusCode]
                  properties:
                    statusCode:
                      $ref: '#/components/schemas/Code'
                    statusMessageText:
                      $ref: '#/components/schemas/Text'
                Code:
                  type: string
                Text:
                  type: string
            """;

    private final OpenApiOntologyValidator validator = new OpenApiOntologyValidator(
            OntologyProfile.load(new ObjectMapper()), new ObjectMapper());

    @Test
    void shouldAcceptConformingResponseAndExample() {
        assertThat(validator.validate(VALID_DOCUMENT)).isEmpty();
    }

    @Test
    void shouldRejectExtraResponseProperty() {
        var document = VALID_DOCUMENT.replace("        statusCode:\n          $ref:",
                "        customField:\n          type: string\n        statusCode:\n          $ref:");

        assertThat(validator.validate(document)).anyMatch(message -> message.contains("customField is not defined"));
    }

    @Test
    void shouldRejectMissingRequiredProperty() {
        var document = VALID_DOCUMENT.replace("required: [statusCode]", "required: []");

        assertThat(validator.validate(document)).anyMatch(message -> message.contains("missing required IATA property 'statusCode'"));
    }

    @Test
    void shouldRejectInvalidExample() {
        var document = VALID_DOCUMENT.replace("statusCode: OK", "statusCode: 42");

        assertThat(validator.validate(document)).anyMatch(message -> message.contains("statusCode") && message.contains("schema type 'string'"));
    }

    @Test
    void shouldRequireAtLeastOneAnnotatedSuccessfulResponse() {
        var document = VALID_DOCUMENT.replace("      x-dxp-ontology-class: http://www.iata.org/ontology#Ack\n", "");

        assertThat(validator.validate(document)).anyMatch(message -> message.contains("at least one successful JSON response"));
    }
}