package org.eclipse.edc.telemetry.store.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.dse.edc.spi.telemetryagent.TelemetryRecord;
import org.eclipse.edc.spi.persistence.LeaseContext;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.ResultSetMapper;
import org.eclipse.edc.sql.lease.spi.SqlLeaseContextBuilder;
import org.eclipse.edc.sql.translation.SqlQueryStatement;
import org.eclipse.edc.telemetry.store.sql.schema.TelemetryRecordStatements;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Stream;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SqlTelemetryRecordStoreUnitTest {

    private static final String DATASOURCE_NAME = "test-ds";

    private final DataSourceRegistry dataSourceRegistry = mock();
    private final DataSource dataSource = mock();
    private final Connection connection = mock();
    private final TelemetryRecordStatements statements = mock();
    private final SqlLeaseContextBuilder leaseContextBuilder = mock();
    private final LeaseContext leaseContext = mock();
    private final QueryExecutor queryExecutor = mock();
    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(42), ZoneOffset.UTC);

    private SqlTelemetryRecordStore store;

    @BeforeEach
    void setUp() throws SQLException {
        when(dataSourceRegistry.resolve(DATASOURCE_NAME)).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(leaseContextBuilder.withConnection(connection)).thenReturn(leaseContext);

        store = new SqlTelemetryRecordStore(
                dataSourceRegistry,
                DATASOURCE_NAME,
                new NoopTransactionContext(),
                new ObjectMapper(),
                statements,
                leaseContextBuilder,
                queryExecutor,
                clock
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void save_whenRecordDoesNotExist_shouldInsertWithoutBreakingLease() {
        var record = record("record-1");
        when(statements.getCountTelemetryRecordByIdClause()).thenReturn("count-sql");
        when(statements.getInsertTelemetryRecordTemplate()).thenReturn("insert-sql");
        when(queryExecutor.query(eq(connection), eq(false), any(ResultSetMapper.class), eq("count-sql"), eq(record.getId())))
                .thenReturn(Stream.of(0));
        when(queryExecutor.execute(eq(connection), eq("insert-sql"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        var result = store.save(record);

        assertThat(result.succeeded()).isTrue();
        verify(queryExecutor).execute(eq(connection), eq("insert-sql"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(leaseContext);
    }

    @Test
    @SuppressWarnings("unchecked")
    void save_whenRecordExists_shouldBreakLeaseAndUpdate() {
        var record = record("record-2");
        when(statements.getCountTelemetryRecordByIdClause()).thenReturn("count-sql");
        when(statements.getUpdateTelemetryRecordTemplate()).thenReturn("update-sql");
        when(queryExecutor.query(eq(connection), eq(false), any(ResultSetMapper.class), eq("count-sql"), eq(record.getId())))
                .thenReturn(Stream.of(1));
        when(leaseContext.breakLease(record.getId())).thenReturn(StoreResult.success());
        when(queryExecutor.execute(eq(connection), eq("update-sql"), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        var result = store.save(record);

        assertThat(result.succeeded()).isTrue();
        verify(leaseContext).breakLease(record.getId());
        verify(queryExecutor).execute(eq(connection), eq("update-sql"), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void nextNotLeased_shouldMaterializeResultsBeforeFilteringByLeaseAcquisition() {
        var first = record("record-1");
        var second = record("record-2");
        var statement = mock(SqlQueryStatement.class);

        when(statements.createNextNotLeaseQuery(any(), anyLong())).thenReturn(statement);
        when(statement.getQueryAsString()).thenReturn("next-not-leased-sql");
        when(statement.getParameters()).thenReturn(new Object[0]);
        when(queryExecutor.query(eq(connection), eq(true), any(ResultSetMapper.class), eq("next-not-leased-sql")))
                .thenReturn(Stream.of(first, second));
        when(leaseContext.acquireLease(first.getId())).thenReturn(StoreResult.success());
        when(leaseContext.acquireLease(second.getId())).thenReturn(StoreResult.alreadyLeased("already leased"));

        var result = store.nextNotLeased(10);

        assertThat(result).containsExactly(first);
        verify(statements).createNextNotLeaseQuery(any(), eq(42L));
        verify(leaseContext).acquireLease(first.getId());
        verify(leaseContext).acquireLease(second.getId());
    }

    private TelemetryRecord record(String id) {
        return TelemetryRecord.Builder.newInstance()
                .id(id)
                .type("TestType")
                .properties(Map.of("key", "value"))
                .build();
    }
}
