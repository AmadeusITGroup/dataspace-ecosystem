package org.eclipse.dse.issuerservice.store.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.dse.issuerservice.store.sql.postgres.VisibilityAttestationPostgresDialectStatements;
import org.eclipse.dse.issuerservice.store.sql.schema.VisibilityAttestationStatements;
import org.eclipse.dse.spi.issuerservice.VisibilityAttestation;
import org.eclipse.dse.spi.issuerservice.VisibilityScope;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.ResultSetMapper;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Stream;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqlVisibilityAttestationStoreUnitTest {

    private static final String DATASOURCE_NAME = "test-ds";

    private final DataSourceRegistry dataSourceRegistry = mock();
    private final QueryExecutor queryExecutor = mock();
    private final DataSource dataSource = mock();
    private final Connection connection = mock();
    private final VisibilityAttestationStatements statements = new VisibilityAttestationPostgresDialectStatements();

    private SqlVisibilityAttestationStore store;

    @BeforeEach
    void setUp() throws SQLException {
        when(dataSourceRegistry.resolve(DATASOURCE_NAME)).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);

        store = new SqlVisibilityAttestationStore(dataSourceRegistry, DATASOURCE_NAME,
                new NoopTransactionContext(), statements, new ObjectMapper(), queryExecutor);
    }

    @SuppressWarnings("unchecked")
    @Test
    void findById_shouldMapVisibilityScope() {
        when(queryExecutor.single(any(Connection.class), anyBoolean(), any(ResultSetMapper.class), anyString(), eq("attestation-id")))
                .thenAnswer(invocation -> invocation.getArgument(2, ResultSetMapper.class)
                        .mapResultSet(mockResultSet("attestation-id", "holder-id", "{\"visibility_scope\":\"SUBSET\"}")));

        var result = store.findById("attestation-id");

        assertThat(result).isEqualTo(new VisibilityAttestation("attestation-id", "holder-id", VisibilityScope.SUBSET));
    }

    @SuppressWarnings("unchecked")
    @Test
    void query_shouldMapVisibilityScope() throws SQLException {
        var querySpec = QuerySpec.Builder.newInstance().build();
        when(queryExecutor.query(any(Connection.class), eq(true), any(ResultSetMapper.class), anyString(), any(Object[].class)))
                .thenAnswer(invocation -> Stream.of(invocation.getArgument(2, ResultSetMapper.class)
                        .mapResultSet(mockResultSet("attestation-id", "holder-id", "{\"visibility_scope\":\"NONE\"}"))));

        try (var result = store.query(querySpec)) {
            assertThat(result).containsExactly(new VisibilityAttestation("attestation-id", "holder-id", VisibilityScope.NONE));
        }
    }

    @Test
    void query_shouldRejectNullQuerySpec() {
        assertThatThrownBy(() -> store.query(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void query_shouldWrapSqlException() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection failed"));

        assertThatThrownBy(() -> store.query(QuerySpec.Builder.newInstance().build()))
                .isInstanceOf(EdcPersistenceException.class)
                .hasCauseInstanceOf(SQLException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void save_shouldPersistAttestation() throws SQLException {
        var attestation = new VisibilityAttestation("attestation-id", "holder-id", VisibilityScope.ALL);
        when(queryExecutor.query(any(Connection.class), anyBoolean(), any(ResultSetMapper.class), anyString(), eq("attestation-id")))
                .thenAnswer(invocation -> {
                    var resultSet = mock(ResultSet.class);
                    when(resultSet.getLong(1)).thenReturn(0L);
                    return Stream.of(invocation.getArgument(2, ResultSetMapper.class).mapResultSet(resultSet));
                });

        assertThat(store.save(attestation).succeeded()).isTrue();
    }

    @Test
    void save_shouldReturnAlreadyExists() {
        var attestation = new VisibilityAttestation("attestation-id", "holder-id", VisibilityScope.ALL);
        when(queryExecutor.query(any(Connection.class), anyBoolean(), any(ResultSetMapper.class), anyString(), eq("attestation-id")))
                .thenReturn(Stream.of(1L));

        assertThat(store.save(attestation).failed()).isTrue();
    }

    @Test
    void save_shouldWrapSqlException() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection failed"));

        assertThatThrownBy(() -> store.save(new VisibilityAttestation("attestation-id", "holder-id", VisibilityScope.ALL)))
                .isInstanceOf(EdcPersistenceException.class)
                .hasCauseInstanceOf(SQLException.class);
    }

    @Test
    void update_shouldReturnNotFound() {
        var attestation = new VisibilityAttestation("attestation-id", "holder-id", VisibilityScope.NONE);
        when(queryExecutor.query(any(Connection.class), anyBoolean(), any(ResultSetMapper.class), anyString(), eq("attestation-id")))
                .thenReturn(Stream.of(0L));

        assertThat(store.update(attestation).failed()).isTrue();
    }

    @Test
    void update_shouldPersistAttestation() {
        var attestation = new VisibilityAttestation("attestation-id", "holder-id", VisibilityScope.SUBSET);
        when(queryExecutor.query(any(Connection.class), anyBoolean(), any(ResultSetMapper.class), anyString(), eq("attestation-id")))
                .thenReturn(Stream.of(1L));

        assertThat(store.update(attestation).succeeded()).isTrue();
    }

    @Test
    void update_shouldWrapSqlException() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection failed"));

        assertThatThrownBy(() -> store.update(new VisibilityAttestation("attestation-id", "holder-id", VisibilityScope.ALL)))
                .isInstanceOf(EdcPersistenceException.class)
                .hasCauseInstanceOf(SQLException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteById_shouldReturnDeletedAttestation() {
        var attestation = new VisibilityAttestation("attestation-id", "holder-id", VisibilityScope.ALL);
        when(queryExecutor.single(any(Connection.class), anyBoolean(), any(ResultSetMapper.class), anyString(), eq("attestation-id")))
                .thenReturn(attestation);

        var result = store.deleteById("attestation-id");

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent()).isEqualTo(attestation);
    }

    @Test
    void deleteById_shouldReturnNotFound() {
        var result = store.deleteById("missing-id");

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("missing-id");
    }

    @Test
    void deleteById_shouldWrapSqlException() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection failed"));

        assertThatThrownBy(() -> store.deleteById("attestation-id"))
                .isInstanceOf(EdcPersistenceException.class)
                .hasCauseInstanceOf(SQLException.class);
    }

    @Test
    void findById_shouldWrapSqlException() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection failed"));

        assertThatThrownBy(() -> store.findById("attestation-id"))
                .isInstanceOf(EdcPersistenceException.class);
    }

    private ResultSet mockResultSet(String id, String holderId, String properties) throws SQLException {
        var resultSet = mock(ResultSet.class);
        when(resultSet.getString(statements.getIdColumn())).thenReturn(id);
        when(resultSet.getString(statements.getHolderIdColumn())).thenReturn(holderId);
        when(resultSet.getString(statements.getPropertiesColumn())).thenReturn(properties);
        return resultSet;
    }
}