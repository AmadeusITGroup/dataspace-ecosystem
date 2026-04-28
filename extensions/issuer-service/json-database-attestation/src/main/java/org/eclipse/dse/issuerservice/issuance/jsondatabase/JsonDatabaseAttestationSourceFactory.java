package org.eclipse.dse.issuerservice.issuance.jsondatabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationSource;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationSourceFactory;
import org.eclipse.edc.issuerservice.spi.issuance.model.AttestationDefinition;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;

import static org.eclipse.dse.issuerservice.issuance.jsondatabase.JsonDatabaseAttestationSource.DATASOURCE_NAME;
import static org.eclipse.dse.issuerservice.issuance.jsondatabase.JsonDatabaseAttestationSource.ID_COLUMN;
import static org.eclipse.dse.issuerservice.issuance.jsondatabase.JsonDatabaseAttestationSource.PROPERTIES_COLUMN;
import static org.eclipse.dse.issuerservice.issuance.jsondatabase.JsonDatabaseAttestationSource.REQUIRED;
import static org.eclipse.dse.issuerservice.issuance.jsondatabase.JsonDatabaseAttestationSource.TABLE_NAME;

public class JsonDatabaseAttestationSourceFactory implements AttestationSourceFactory {

    private final TransactionContext transactionContext;
    private final QueryExecutor queryExecutor;
    private final ObjectMapper objectMapper;
    private final DataSourceRegistry dataSourceRegistry;

    public JsonDatabaseAttestationSourceFactory(TransactionContext transactionContext,
                                                QueryExecutor queryExecutor,
                                                DataSourceRegistry dataSourceRegistry,
                                                ObjectMapper objectMapper) {
        this.transactionContext = transactionContext;
        this.queryExecutor = queryExecutor;
        this.objectMapper = objectMapper;
        this.dataSourceRegistry = dataSourceRegistry;
    }

    @Override
    public AttestationSource createSource(AttestationDefinition definition) {
        var config = definition.getConfiguration();
        var required = getBoolean(config.get(REQUIRED), REQUIRED, false);
        var dataSourceName = getRequiredString(config.get(DATASOURCE_NAME), DATASOURCE_NAME);
        var tableName = getRequiredString(config.get(TABLE_NAME), TABLE_NAME);
        var idColumn = getString(config.get(ID_COLUMN), ID_COLUMN, "holder_id");
        var propertiesColumn = getRequiredString(config.get(PROPERTIES_COLUMN), PROPERTIES_COLUMN);

        return new JsonDatabaseAttestationSource(
                dataSourceName, required, objectMapper, tableName,
                dataSourceRegistry, queryExecutor, transactionContext,
                idColumn, propertiesColumn);
    }

    private String getRequiredString(Object value, String key) {
        var resolved = getString(value, key, null);
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalArgumentException("Configuration property '%s' must be a non-blank string".formatted(key));
        }
        return resolved;
    }

    private String getString(Object value, String key, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof String stringValue) {
            return stringValue;
        }
        throw new IllegalArgumentException("Configuration property '%s' must be a string but was %s"
                .formatted(key, value.getClass().getSimpleName()));
    }

    private boolean getBoolean(Object value, String key, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            if ("true".equalsIgnoreCase(stringValue)) {
                return true;
            }
            if ("false".equalsIgnoreCase(stringValue)) {
                return false;
            }
        }
        throw new IllegalArgumentException("Configuration property '%s' must be a boolean or 'true'/'false' string but was %s"
                .formatted(key, value.getClass().getSimpleName()));
    }
}
