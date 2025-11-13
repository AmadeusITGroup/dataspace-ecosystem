package org.eclipse.dse.issuerservice.store.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.dse.issuerservice.store.sql.schema.DomainAttestationStatements;
import org.eclipse.dse.spi.issuerservice.DomainAttestation;
import org.eclipse.dse.spi.issuerservice.DomainAttestationStore;
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
import java.util.Objects;
import java.util.stream.Stream;

import static java.lang.String.format;

public class SqlDomainAttestationStore extends AbstractSqlStore implements DomainAttestationStore {

    private final DomainAttestationStatements statements;

    public SqlDomainAttestationStore(DataSourceRegistry dataSourceRegistry, String dataSourceName,
                                     TransactionContext transactionContext, DomainAttestationStatements statements,
                                     ObjectMapper objectMapper, QueryExecutor queryExecutor) {
        super(dataSourceRegistry, dataSourceName, transactionContext, objectMapper, queryExecutor);
        this.statements = statements;
    }


    @Override
    public Stream<DomainAttestation> query(QuerySpec spec) {
        return transactionContext.execute(() -> {
            Objects.requireNonNull(spec);

            try {
                var queryStmt = statements.createQuery(spec);
                return queryExecutor.query(getConnection(), true, this::mapResultSet, queryStmt.getQueryAsString(), queryStmt.getParameters());
            } catch (SQLException exception) {
                throw new EdcPersistenceException(exception);
            }
        });
    }

    @Override
    public DomainAttestation findById(String definitionId) {
        Objects.requireNonNull(definitionId);
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                return findById(connection, definitionId);
            } catch (Exception exception) {
                throw new EdcPersistenceException(exception);
            }
        });
    }

    @Override
    public StoreResult<Void> save(DomainAttestation attestation) {
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                insertInternal(connection, attestation);
                return StoreResult.success();
            } catch (Exception e) {
                throw new EdcPersistenceException(e.getMessage(), e);
            }
        });
    }

    @Override
    public StoreResult<Void> update(DomainAttestation attestation) {
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                if (existsById(connection, attestation.id())) {
                    updateInternal(connection, attestation);
                    return StoreResult.success();
                } else {
                    return StoreResult.notFound(format(ATTESTATION_NOT_FOUND, attestation.id()));
                }
            } catch (Exception e) {
                throw new EdcPersistenceException(e.getMessage(), e);
            }
        });
    }

    @Override
    public StoreResult<DomainAttestation> deleteById(String id) {
        Objects.requireNonNull(id);
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                var entity = findById(connection, id);
                if (entity != null) {
                    queryExecutor.execute(connection, statements.getDeleteByIdTemplate(), id);
                    return StoreResult.success(entity);
                } else {
                    return StoreResult.notFound(format(ATTESTATION_NOT_FOUND, id));
                }

            } catch (Exception e) {
                throw new EdcPersistenceException(e.getMessage(), e);
            }
        });
    }

    private DomainAttestation mapResultSet(ResultSet resultSet) throws SQLException {
        return new DomainAttestation(
                resultSet.getString(statements.getIdColumn()),
                resultSet.getString(statements.getHolderIdColumn()),
                resultSet.getString(statements.getDomainColumn()));
    }

    private DomainAttestation findById(Connection connection, String id) {
        var sql = statements.getFindByTemplate();
        return queryExecutor.single(connection, false, this::mapResultSet, sql, id);
    }

    private boolean existsById(Connection connection, String definitionId) {
        var sql = statements.getCountTemplate();
        try (var stream = queryExecutor.query(connection, false, this::mapCount, sql, definitionId)) {
            return stream.findFirst().orElse(0L) > 0;
        }
    }

    private long mapCount(ResultSet resultSet) throws SQLException {
        return resultSet.getLong(1);
    }

    private void insertInternal(Connection connection, DomainAttestation attestation) {
        transactionContext.execute(() -> {
            queryExecutor.execute(connection, statements.getInsertTemplate(),
                    attestation.holderId(),
                    attestation.domain());
        });
    }

    private void updateInternal(Connection connection, DomainAttestation attestation) {
        Objects.requireNonNull(attestation);
        queryExecutor.execute(connection, statements.getUpdateTemplate(),
                attestation.id(),
                attestation.holderId(),
                attestation.domain(),
                attestation.id());
    }
}
