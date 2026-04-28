package org.eclipse.dse.issuerservice.store.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.dse.issuerservice.store.sql.postgres.PostgresDialectStatements;
import org.eclipse.dse.issuerservice.store.sql.schema.MembershipAttestationStatements;
import org.eclipse.dse.spi.issuerservice.MembershipAttestation;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.ResultSetMapper;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlMembershipAttestationStoreUnitTest {

    private static final String DATASOURCE_NAME = "test-ds";
    private final DataSourceRegistry dataSourceRegistry = mock();
    private final QueryExecutor queryExecutor = mock();
    private final DataSource dataSource = mock();
    private final Connection connection = mock();
    private final MembershipAttestationStatements statements = new PostgresDialectStatements();

    private SqlMembershipAttestationStore store;

    @BeforeEach
    void setUp() throws SQLException {
        when(dataSourceRegistry.resolve(DATASOURCE_NAME)).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);

        store = new SqlMembershipAttestationStore(
                dataSourceRegistry, DATASOURCE_NAME,
                new NoopTransactionContext(), statements,
                new ObjectMapper(), queryExecutor);
    }

    @SuppressWarnings("unchecked")
    @Test
    void findById_shouldMapResultWithProperties() {
        var id = "att-1";
        var now = Instant.now();
        when(queryExecutor.single(any(Connection.class), anyBoolean(), any(ResultSetMapper.class), anyString(), eq(id)))
                .thenAnswer(invocation -> {
                    var mapper = invocation.getArgument(2, ResultSetMapper.class);
                    var rs = mockResultSet(id, "holder-1", "Test", "FullMember", now, "{\"companySegment\":\"Airlines\"}");
                    return mapper.mapResultSet(rs);
                });

        var result = store.findById(id);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(id);
        assertThat(result.properties()).containsEntry("companySegment", "Airlines");
    }

    @SuppressWarnings("unchecked")
    @Test
    void findById_shouldHandleNullProperties() {
        var id = "att-2";
        var now = Instant.now();
        when(queryExecutor.single(any(Connection.class), anyBoolean(), any(ResultSetMapper.class), anyString(), eq(id)))
                .thenAnswer(invocation -> {
                    var mapper = invocation.getArgument(2, ResultSetMapper.class);
                    var rs = mockResultSet(id, "holder-1", "Test", "FullMember", now, null);
                    return mapper.mapResultSet(rs);
                });

        var result = store.findById(id);

        assertThat(result).isNotNull();
        assertThat(result.properties()).isEmpty();
    }

    @Test
    void save_shouldPersistWithProperties() {
        var attestation = new MembershipAttestation("att-3", "holder-1", "Test", "FullMember", Instant.now(), Map.of("key", "val"));
        when(queryExecutor.query(any(Connection.class), anyBoolean(), any(ResultSetMapper.class), anyString(), eq("att-3")))
                .thenReturn(Stream.of(0L));
        when(queryExecutor.execute(any(Connection.class), anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        var result = store.save(attestation);

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void save_shouldReturnAlreadyExists() {
        var attestation = new MembershipAttestation("att-4", "holder-1", "Test", "FullMember", Instant.now(), Map.of());
        when(queryExecutor.query(any(Connection.class), anyBoolean(), any(ResultSetMapper.class), anyString(), eq("att-4")))
                .thenReturn(Stream.of(1L));

        var result = store.save(attestation);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("att-4");
    }

    @SuppressWarnings("unchecked")
    @Test
    void update_shouldPersistWithProperties() {
        var attestation = new MembershipAttestation("att-5", "holder-1", "Test", "FullMember", Instant.now(), Map.of("key", "val"));
        when(queryExecutor.query(any(Connection.class), anyBoolean(), any(ResultSetMapper.class), anyString(), eq("att-5")))
                .thenReturn(Stream.of(1L));
        when(queryExecutor.execute(any(Connection.class), anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        var result = store.update(attestation);

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void update_shouldReturnNotFound() {
        var attestation = new MembershipAttestation("att-6", "holder-1", "Test", "FullMember", Instant.now(), Map.of());
        when(queryExecutor.query(any(Connection.class), anyBoolean(), any(ResultSetMapper.class), anyString(), eq("att-6")))
                .thenReturn(Stream.of(0L));

        var result = store.update(attestation);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("att-6");
    }

    @Test
    void findById_shouldThrowOnSqlException() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection failed"));

        assertThatThrownBy(() -> store.findById("any-id"))
                .isInstanceOf(EdcPersistenceException.class);
    }

    private ResultSet mockResultSet(String id, String holderId, String name, String membershipType, Instant startDate, String propertiesJson) throws SQLException {
        var rs = mock(ResultSet.class);
        when(rs.getString(statements.getIdColumn())).thenReturn(id);
        when(rs.getString(statements.getHolderIdColumn())).thenReturn(holderId);
        when(rs.getString(statements.getNameColumn())).thenReturn(name);
        when(rs.getString(statements.getMembershipTypeColumn())).thenReturn(membershipType);
        when(rs.getTimestamp(statements.getMembershipStartDateColumn())).thenReturn(Timestamp.from(startDate));
        when(rs.getString(statements.getPropertiesColumn())).thenReturn(propertiesJson);
        return rs;
    }
}
