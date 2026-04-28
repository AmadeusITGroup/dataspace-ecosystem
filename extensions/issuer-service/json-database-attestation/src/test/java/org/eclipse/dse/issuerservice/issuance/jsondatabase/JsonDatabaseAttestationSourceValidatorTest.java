package org.eclipse.dse.issuerservice.issuance.jsondatabase;

import org.eclipse.edc.issuerservice.spi.issuance.model.AttestationDefinition;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonDatabaseAttestationSourceValidatorTest {

    private final JsonDatabaseAttestationSourceValidator validator = new JsonDatabaseAttestationSourceValidator();

    @Test
    void shouldSucceedWithValidDefinition() {
        var definition = mockDefinition("json-database", Map.of(
                "dataSourceName", "membership",
                "tableName", "membership_attestation",
                "propertiesColumn", "properties"
        ));

        var result = validator.validate(definition);

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void shouldFailWithWrongType() {
        var definition = mockDefinition("database", Map.of(
                "dataSourceName", "membership",
                "tableName", "membership_attestation",
                "propertiesColumn", "properties"
        ));

        var result = validator.validate(definition);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void shouldFailWithMissingDataSourceName() {
        var definition = mockDefinition("json-database", Map.of(
                "tableName", "membership_attestation",
                "propertiesColumn", "properties"
        ));

        var result = validator.validate(definition);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void shouldFailWithMissingTableName() {
        var definition = mockDefinition("json-database", Map.of(
                "dataSourceName", "membership",
                "propertiesColumn", "properties"
        ));

        var result = validator.validate(definition);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void shouldFailWithMissingPropertiesColumn() {
        var definition = mockDefinition("json-database", Map.of(
                "dataSourceName", "membership",
                "tableName", "membership_attestation"
        ));

        var result = validator.validate(definition);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void shouldFailWithBlankTableName() {
        var definition = mockDefinition("json-database", Map.of(
                "dataSourceName", "membership",
                "tableName", "  ",
                "propertiesColumn", "properties"
        ));

        var result = validator.validate(definition);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void shouldFailWithUnsafeTableName() {
        var definition = mockDefinition("json-database", Map.of(
                "dataSourceName", "membership",
                "tableName", "table; DROP TABLE users--",
                "propertiesColumn", "properties"
        ));

        var result = validator.validate(definition);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void shouldFailWithUnsafePropertiesColumn() {
        var definition = mockDefinition("json-database", Map.of(
                "dataSourceName", "membership",
                "tableName", "membership_attestation",
                "propertiesColumn", "col' OR '1'='1"
        ));

        var result = validator.validate(definition);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void shouldFailWithUnsafeIdColumn() {
        var definition = mockDefinition("json-database", Map.of(
                "dataSourceName", "membership",
                "tableName", "membership_attestation",
                "propertiesColumn", "properties",
                "idColumn", "id; DROP TABLE"
        ));

        var result = validator.validate(definition);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void shouldSucceedWithSafeIdColumn() {
        var definition = mockDefinition("json-database", Map.of(
                "dataSourceName", "membership",
                "tableName", "membership_attestation",
                "propertiesColumn", "properties",
                "idColumn", "holder_id"
        ));

        var result = validator.validate(definition);

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void shouldAcceptSchemaQualifiedTableName() {
        var definition = mockDefinition("json-database", Map.of(
                "dataSourceName", "membership",
                "tableName", "public.membership_attestation",
                "propertiesColumn", "properties"
        ));

        var result = validator.validate(definition);

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void shouldFailWhenDataSourceNameIsNotAString() {
        var definition = mockDefinition("json-database", Map.of(
                "dataSourceName", 42,
                "tableName", "membership_attestation",
                "propertiesColumn", "properties"
        ));

        var result = validator.validate(definition);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureMessages()).anyMatch(message -> message.contains("dataSourceName"));
    }

    @Test
    void shouldFailWhenRequiredFlagIsNotBooleanLike() {
        var definition = mockDefinition("json-database", Map.of(
                "dataSourceName", "membership",
                "tableName", "membership_attestation",
                "propertiesColumn", "properties",
                "required", "sometimes"
        ));

        var result = validator.validate(definition);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureMessages()).anyMatch(message -> message.contains("required"));
    }

    @Test
    void shouldAcceptBooleanStringForRequiredFlag() {
        var definition = mockDefinition("json-database", Map.of(
                "dataSourceName", "membership",
                "tableName", "membership_attestation",
                "propertiesColumn", "properties",
                "required", "false"
        ));

        var result = validator.validate(definition);

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void shouldFailWhenIdColumnIsNotAString() {
        var definition = mockDefinition("json-database", Map.of(
                "dataSourceName", "membership",
                "tableName", "membership_attestation",
                "propertiesColumn", "properties",
                "idColumn", 17
        ));

        var result = validator.validate(definition);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureMessages()).anyMatch(message -> message.contains("idColumn"));
    }

    private AttestationDefinition mockDefinition(String type, Map<String, Object> config) {
        var definition = mock(AttestationDefinition.class);
        when(definition.getAttestationType()).thenReturn(type);
        when(definition.getConfiguration()).thenReturn(new HashMap<>(config));
        return definition;
    }
}
