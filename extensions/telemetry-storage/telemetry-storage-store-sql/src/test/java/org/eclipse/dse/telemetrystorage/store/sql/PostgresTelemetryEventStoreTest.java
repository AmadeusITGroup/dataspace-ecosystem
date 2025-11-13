package org.eclipse.dse.telemetrystorage.store.sql;


import org.eclipse.dse.spi.telemetrystorage.TelemetryEventStore;
import org.eclipse.dse.spi.telemetrystorage.TelemetryEventStoreTestBase;
import org.eclipse.dse.telemetrystorage.store.sql.postgres.PostgresDialectStatements;
import org.eclipse.dse.telemetrystorage.store.sql.schema.BaseSqlDialectStatements;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.testfixtures.PostgresqlStoreSetupExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;

@ComponentTest
@ExtendWith(PostgresqlStoreSetupExtension.class)
class PostgresTelemetryEventStoreTest extends TelemetryEventStoreTestBase {

    private final BaseSqlDialectStatements statements = new PostgresDialectStatements();

    private SqlTelemetryEventStore store;

    @BeforeEach
    void setUp(PostgresqlStoreSetupExtension extension, QueryExecutor queryExecutor) throws IOException {
        var typeManager = new JacksonTypeManager();
        store = new SqlTelemetryEventStore(extension.getDataSourceRegistry(), extension.getDatasourceName(),
                extension.getTransactionContext(), statements, typeManager.getMapper(), queryExecutor);
        var schema = TestUtils.getResourceFileContentAsString("telemetry-event-schema.sql");
        extension.runQuery(schema);
        // Insert elements into the participant_id table
        extension.runQuery("INSERT INTO participant_id (id, email, name, timestamp) VALUES ('participant1', 'participant1@example.com', 'participant1', now())");
        extension.runQuery("INSERT INTO participant_id (id, email, name, timestamp) VALUES ('participant2', 'participant2@example.com', 'participant2', now())");

    }

    @AfterEach
    void tearDown(PostgresqlStoreSetupExtension extension) {
        extension.runQuery("DROP TABLE " + statements.getTelemetryEventTable() + " CASCADE");
        extension.runQuery("DROP TABLE " + statements.getParticipant_idTable() + " CASCADE");
        extension.runQuery("DROP TABLE " + statements.getReportTable() + " CASCADE");
    }

    @Override
    protected TelemetryEventStore getStore() {
        return store;
    }
}
