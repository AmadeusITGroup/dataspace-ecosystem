package org.eclipse.dse.issuerservice.issuance.jsondatabase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationContext;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationSource;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.store.AbstractSqlStore;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;

public class JsonDatabaseAttestationSource extends AbstractSqlStore implements AttestationSource {

    public static final String DATASOURCE_NAME = "dataSourceName";
    public static final String TABLE_NAME = "tableName";
    public static final String REQUIRED = "required";
    public static final String ID_COLUMN = "idColumn";
    public static final String PROPERTIES_COLUMN = "propertiesColumn";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final boolean required;
    private final String tableName;
    private final String idColumn;
    private final String propertiesColumn;

    public JsonDatabaseAttestationSource(String dataSourceName,
                                         boolean required,
                                         ObjectMapper objectMapper,
                                         String tableName,
                                         DataSourceRegistry dataSourceRegistry,
                                         QueryExecutor queryExecutor,
                                         TransactionContext transactionContext,
                                         String idColumn,
                                         String propertiesColumn) {
        super(dataSourceRegistry, dataSourceName, transactionContext, objectMapper, queryExecutor);
        this.required = required;
        this.tableName = tableName;
        this.idColumn = idColumn;
        this.propertiesColumn = propertiesColumn;
    }

    @Override
    public Result<Map<String, Object>> execute(AttestationContext context) {
        var participantId = context.participantContextId();
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                var query = "SELECT * FROM %s WHERE %s = ?".formatted(tableName, idColumn);
                var result = queryExecutor.single(connection, true, this::mapGenericResult, query, participantId);
                if (result == null) {
                    if (required) {
                        var msg = "No attestation found for participant '%s' in table '%s'"
                                .formatted(participantId, tableName);
                        return Result.failure(msg);
                    }
                    return Result.success(emptyMap());
                }
                return Result.success(result);
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }

    public boolean isRequired() {
        return required;
    }

    private Map<String, Object> mapGenericResult(ResultSet resultSet) {
        try {
            var map = new HashMap<String, Object>();
            var metaData = resultSet.getMetaData();
            var columnCount = metaData.getColumnCount();

            for (int i = 1; i <= columnCount; i++) {
                var columnName = metaData.getColumnName(i);
                var value = resultSet.getString(columnName);

                if (propertiesColumn != null && propertiesColumn.equals(columnName)) {
                    if (value != null && !value.isBlank()) {
                        Map<String, Object> jsonProperties = fromJson(value, MAP_TYPE);
                        jsonProperties.forEach(map::putIfAbsent);
                    }
                } else {
                    map.put(columnName, value);
                }
            }

            return map;
        } catch (Exception e) {
            throw new EdcPersistenceException(e);
        }
    }
}
