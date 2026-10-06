package org.eclipse.dse.issuerservice.store.sql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.dse.issuerservice.store.sql.schema.VisibilityAttestationStatements;
import org.eclipse.dse.spi.issuerservice.VisibilityAttestation;
import org.eclipse.dse.spi.issuerservice.VisibilityAttestationStore;
import org.eclipse.dse.spi.issuerservice.VisibilityScope;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.store.AbstractSqlStore;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static java.lang.String.format;

public class SqlVisibilityAttestationStore extends AbstractSqlStore implements VisibilityAttestationStore {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String VISIBILITY_SCOPE = "visibility_scope";

    private final VisibilityAttestationStatements statements;

    public SqlVisibilityAttestationStore(DataSourceRegistry dataSourceRegistry, String dataSourceName,
                                         TransactionContext transactionContext, VisibilityAttestationStatements statements,
                                         ObjectMapper objectMapper, QueryExecutor queryExecutor) {
        super(dataSourceRegistry, dataSourceName, transactionContext, objectMapper, queryExecutor);
        this.statements = statements;
    }

    @Override
    public Stream<VisibilityAttestation> query(QuerySpec spec) {
        return transactionContext.execute(() -> {
            Objects.requireNonNull(spec);
            try {
                var query = statements.createQuery(spec);
                return queryExecutor.query(getConnection(), true, this::mapResultSet, query.getQueryAsString(), query.getParameters());
            } catch (SQLException exception) {
                throw new EdcPersistenceException(exception);
            }
        });
    }

    @Override
    public VisibilityAttestation findById(String id) {
        Objects.requireNonNull(id);
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                return findById(connection, id);
            } catch (Exception exception) {
                throw new EdcPersistenceException(exception);
            }
        });
    }

    @Override
    public StoreResult<Void> save(VisibilityAttestation attestation) {
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                if (existsById(connection, attestation.id())) {
                    return StoreResult.alreadyExists(format(VISIBILITY_ATTESTATION_ALREADY_EXISTS, attestation.id()));
                }
                queryExecutor.execute(connection, statements.getInsertTemplate(),
                        attestation.id(), attestation.holderId(), toJson(properties(attestation)));
                return StoreResult.success();
            } catch (Exception exception) {
                throw new EdcPersistenceException(exception.getMessage(), exception);
            }
        });
    }

    @Override
    public StoreResult<Void> update(VisibilityAttestation attestation) {
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                if (!existsById(connection, attestation.id())) {
                    return StoreResult.notFound(format(VISIBILITY_ATTESTATION_NOT_FOUND, attestation.id()));
                }
                queryExecutor.execute(connection, statements.getUpdateTemplate(),
                        attestation.id(), attestation.holderId(), toJson(properties(attestation)), attestation.id());
                return StoreResult.success();
            } catch (Exception exception) {
                throw new EdcPersistenceException(exception.getMessage(), exception);
            }
        });
    }

    @Override
    public StoreResult<VisibilityAttestation> deleteById(String id) {
        Objects.requireNonNull(id);
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                var attestation = findById(connection, id);
                if (attestation == null) {
                    return StoreResult.notFound(format(VISIBILITY_ATTESTATION_NOT_FOUND, id));
                }
                queryExecutor.execute(connection, statements.getDeleteByIdTemplate(), id);
                return StoreResult.success(attestation);
            } catch (Exception exception) {
                throw new EdcPersistenceException(exception.getMessage(), exception);
            }
        });
    }

    private VisibilityAttestation findById(Connection connection, String id) {
        return queryExecutor.single(connection, false, this::mapResultSet, statements.getFindByTemplate(), id);
    }

    private boolean existsById(Connection connection, String id) {
        try (var stream = queryExecutor.query(connection, false, SqlVisibilityAttestationStore::mapCount, statements.getCountTemplate(), id)) {
            return stream.findFirst().orElse(0L) > 0;
        }
    }

    private VisibilityAttestation mapResultSet(ResultSet resultSet) throws Exception {
        var properties = fromJson(resultSet.getString(statements.getPropertiesColumn()), MAP_TYPE);
        return new VisibilityAttestation(
                resultSet.getString(statements.getIdColumn()),
                resultSet.getString(statements.getHolderIdColumn()),
                VisibilityScope.valueOf(properties.get(VISIBILITY_SCOPE).toString()));
    }

    private Map<String, Object> properties(VisibilityAttestation attestation) {
        return Map.of(VISIBILITY_SCOPE, attestation.visibilityScope().name());
    }

    private static long mapCount(ResultSet resultSet) throws SQLException {
        return resultSet.getLong(1);
    }
}