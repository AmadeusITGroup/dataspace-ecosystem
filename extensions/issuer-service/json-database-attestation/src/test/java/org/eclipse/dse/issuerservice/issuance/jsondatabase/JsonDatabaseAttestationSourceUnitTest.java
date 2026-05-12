package org.eclipse.dse.issuerservice.issuance.jsondatabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationContext;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.ResultSetMapper;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonDatabaseAttestationSourceUnitTest {

    private static final String TABLE_NAME = "test_table";
    private static final String ID_COLUMN = "holder_id";
    private static final String PROPERTIES_COLUMN = "properties";
    private static final String DATASOURCE_NAME = "test-ds";
    private static final String PARTICIPANT_ID = "participant-1";

    private final DataSourceRegistry dataSourceRegistry = mock();
    private final QueryExecutor queryExecutor = mock();
    private final DataSource dataSource = mock();
    private final Connection connection = mock();

    private JsonDatabaseAttestationSource source;
    private JsonDatabaseAttestationSource requiredSource;

    @BeforeEach
    void setUp() throws SQLException {
        when(dataSourceRegistry.resolve(DATASOURCE_NAME)).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);

        source = new JsonDatabaseAttestationSource(
                DATASOURCE_NAME, false, new ObjectMapper(),
                TABLE_NAME, dataSourceRegistry,
                queryExecutor, new NoopTransactionContext(),
                ID_COLUMN, PROPERTIES_COLUMN);

        requiredSource = new JsonDatabaseAttestationSource(
                DATASOURCE_NAME, true, new ObjectMapper(),
                TABLE_NAME, dataSourceRegistry,
                queryExecutor, new NoopTransactionContext(),
                ID_COLUMN, PROPERTIES_COLUMN);
    }

    @Test
    void execute_shouldReturnSuccess_whenResultFound() {
        var expected = Map.<String, Object>of("name", "Test Corp", "companySegment", "Airlines");
        when(queryExecutor.single(any(Connection.class), anyBoolean(), any(), anyString(), eq(PARTICIPANT_ID)))
                .thenReturn(expected);

        var result = source.execute(mockContext(PARTICIPANT_ID));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent()).containsEntry("name", "Test Corp");
        assertThat(result.getContent()).containsEntry("companySegment", "Airlines");
    }

    @Test
    void execute_shouldReturnEmptyMap_whenNotFoundAndNotRequired() {
        when(queryExecutor.single(any(Connection.class), anyBoolean(), any(), anyString(), eq(PARTICIPANT_ID)))
                .thenReturn(null);

        var result = source.execute(mockContext(PARTICIPANT_ID));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void execute_shouldReturnFailure_whenNotFoundAndRequired() {
        when(queryExecutor.single(any(Connection.class), anyBoolean(), any(), anyString(), eq(PARTICIPANT_ID)))
                .thenReturn(null);

        var result = requiredSource.execute(mockContext(PARTICIPANT_ID));

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains(PARTICIPANT_ID).contains(TABLE_NAME);
    }

    @Test
    void execute_shouldThrowEdcPersistenceException_whenSqlExceptionOccurs() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection failed"));

        assertThatThrownBy(() -> source.execute(mockContext(PARTICIPANT_ID)))
                .isInstanceOf(EdcPersistenceException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_shouldFlattenJsonPropertiesColumn() throws Exception {
        when(queryExecutor.single(any(Connection.class), anyBoolean(), any(ResultSetMapper.class), anyString(), eq(PARTICIPANT_ID)))
                .thenAnswer(invocation -> {
                    var mapper = invocation.getArgument(2, ResultSetMapper.class);
                    var rs = mockResultSet(
                            new String[]{"holder_id", "name", "properties"},
                            new String[]{PARTICIPANT_ID, "Test Corp", "{\"companySegment\": \"Airlines\", \"channel\": \"Direct\"}"}
                    );
                    return mapper.mapResultSet(rs);
                });

        var result = source.execute(mockContext(PARTICIPANT_ID));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent()).containsEntry("holder_id", PARTICIPANT_ID);
        assertThat(result.getContent()).containsEntry("name", "Test Corp");
        assertThat(result.getContent()).containsEntry("companySegment", "Airlines");
        assertThat(result.getContent()).containsEntry("channel", "Direct");
        assertThat(result.getContent()).doesNotContainKey("properties");
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_shouldHandleEmptyJsonProperties() throws Exception {
        when(queryExecutor.single(any(Connection.class), anyBoolean(), any(ResultSetMapper.class), anyString(), eq(PARTICIPANT_ID)))
                .thenAnswer(invocation -> {
                    var mapper = invocation.getArgument(2, ResultSetMapper.class);
                    var rs = mockResultSet(
                            new String[]{"holder_id", "name", "properties"},
                            new String[]{PARTICIPANT_ID, "Test Corp", "{}"}
                    );
                    return mapper.mapResultSet(rs);
                });

        var result = source.execute(mockContext(PARTICIPANT_ID));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent()).containsEntry("name", "Test Corp");
        assertThat(result.getContent()).doesNotContainKey("properties");
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_shouldHandleNullPropertiesColumn() throws Exception {
        when(queryExecutor.single(any(Connection.class), anyBoolean(), any(ResultSetMapper.class), anyString(), eq(PARTICIPANT_ID)))
                .thenAnswer(invocation -> {
                    var mapper = invocation.getArgument(2, ResultSetMapper.class);
                    var rs = mockResultSet(
                            new String[]{"holder_id", "name", "properties"},
                            new String[]{PARTICIPANT_ID, "Test Corp", null}
                    );
                    return mapper.mapResultSet(rs);
                });

        var result = source.execute(mockContext(PARTICIPANT_ID));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent()).containsEntry("holder_id", PARTICIPANT_ID);
        assertThat(result.getContent()).containsEntry("name", "Test Corp");
        assertThat(result.getContent()).doesNotContainKey("properties");
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_shouldNotOverwriteColumnValuesWithJsonProperties() throws Exception {
        when(queryExecutor.single(any(Connection.class), anyBoolean(), any(ResultSetMapper.class), anyString(), eq(PARTICIPANT_ID)))
                .thenAnswer(invocation -> {
                    var mapper = invocation.getArgument(2, ResultSetMapper.class);
                    var rs = mockResultSet(
                            new String[]{"holder_id", "name", "properties"},
                            new String[]{PARTICIPANT_ID, "Test Corp", "{\"holder_id\": \"malicious-id\", \"channel\": \"Direct\"}"}
                    );
                    return mapper.mapResultSet(rs);
                });

        var result = source.execute(mockContext(PARTICIPANT_ID));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent()).containsEntry("holder_id", PARTICIPANT_ID);
        assertThat(result.getContent()).containsEntry("channel", "Direct");
    }

    @Test
    void isRequired_shouldReturnConfiguredValue() {
        assertThat(source.isRequired()).isFalse();
        assertThat(requiredSource.isRequired()).isTrue();
    }

    private AttestationContext mockContext(String participantId) {
        var context = mock(AttestationContext.class);
        when(context.participantContextId()).thenReturn(participantId);
        return context;
    }

    private ResultSet mockResultSet(String[] columns, String[] values) throws SQLException {
        var rs = mock(ResultSet.class);
        var metaData = mock(ResultSetMetaData.class);
        when(rs.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(columns.length);
        for (int i = 0; i < columns.length; i++) {
            when(metaData.getColumnName(i + 1)).thenReturn(columns[i]);
            when(rs.getString(columns[i])).thenReturn(values[i]);
        }
        return rs;
    }
}
