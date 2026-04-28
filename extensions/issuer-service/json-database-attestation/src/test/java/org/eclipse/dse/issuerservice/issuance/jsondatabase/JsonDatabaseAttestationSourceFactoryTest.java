package org.eclipse.dse.issuerservice.issuance.jsondatabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.issuerservice.spi.issuance.model.AttestationDefinition;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonDatabaseAttestationSourceFactoryTest {

    private final TransactionContext transactionContext = mock();
    private final QueryExecutor queryExecutor = mock();
    private final DataSourceRegistry dataSourceRegistry = mock();
    private final JsonDatabaseAttestationSourceFactory factory =
            new JsonDatabaseAttestationSourceFactory(transactionContext, queryExecutor, dataSourceRegistry, new ObjectMapper());

    @Test
    void shouldCreateSourceWithAllConfig() {
        var definition = mockDefinition(Map.of(
                "dataSourceName", "membership",
                "tableName", "membership_attestation",
                "propertiesColumn", "properties",
                "idColumn", "custom_id",
                "required", true
        ));

        var source = factory.createSource(definition);

        assertThat(source).isInstanceOf(JsonDatabaseAttestationSource.class);
        assertThat(((JsonDatabaseAttestationSource) source).isRequired()).isTrue();
    }

    @Test
    void shouldDefaultIdColumnToHolderId() {
        var definition = mockDefinition(Map.of(
                "dataSourceName", "membership",
                "tableName", "membership_attestation",
                "propertiesColumn", "properties"
        ));

        var source = factory.createSource(definition);

        assertThat(source).isInstanceOf(JsonDatabaseAttestationSource.class);
        assertThat(((JsonDatabaseAttestationSource) source).isRequired()).isFalse();
    }

    @Test
    void shouldAcceptBooleanStringForRequiredFlag() {
        var definition = mockDefinition(Map.of(
                "dataSourceName", "membership",
                "tableName", "membership_attestation",
                "propertiesColumn", "properties",
                "required", "true"
        ));

        var source = factory.createSource(definition);

        assertThat(source).isInstanceOf(JsonDatabaseAttestationSource.class);
        assertThat(((JsonDatabaseAttestationSource) source).isRequired()).isTrue();
    }

    @Test
    void shouldFailWhenConfiguredValueHasUnexpectedType() {
        var definition = mockDefinition(Map.of(
                "dataSourceName", 42,
                "tableName", "membership_attestation",
                "propertiesColumn", "properties"
        ));

        assertThatThrownBy(() -> factory.createSource(definition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataSourceName")
                .hasMessageContaining("string");
    }

    private AttestationDefinition mockDefinition(Map<String, Object> config) {
        var definition = mock(AttestationDefinition.class);
        when(definition.getConfiguration()).thenReturn(config);
        return definition;
    }
}
