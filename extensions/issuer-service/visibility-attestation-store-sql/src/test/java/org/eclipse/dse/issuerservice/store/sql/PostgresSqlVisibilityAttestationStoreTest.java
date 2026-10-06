package org.eclipse.dse.issuerservice.store.sql;

import org.eclipse.dse.issuerservice.store.sql.postgres.VisibilityAttestationPostgresDialectStatements;
import org.eclipse.dse.spi.issuerservice.VisibilityAttestationStore;
import org.eclipse.dse.spi.issuerservice.VisibilityAttestationStoreTestBase;
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
class PostgresSqlVisibilityAttestationStoreTest extends VisibilityAttestationStoreTestBase {

    private final VisibilityAttestationPostgresDialectStatements statements = new VisibilityAttestationPostgresDialectStatements();
    private SqlVisibilityAttestationStore store;

    @BeforeEach
    void setUp(PostgresqlStoreSetupExtension extension, QueryExecutor queryExecutor) throws IOException {
        var typeManager = new JacksonTypeManager();
        store = new SqlVisibilityAttestationStore(extension.getDataSourceRegistry(), extension.getDatasourceName(),
                extension.getTransactionContext(), statements, typeManager.getMapper(), queryExecutor);
        extension.runQuery(TestUtils.getResourceFileContentAsString("visibility-attestation-schema.sql"));
    }

    @AfterEach
    void tearDown(PostgresqlStoreSetupExtension extension) {
        extension.runQuery("DROP TABLE " + statements.getVisibilityAttestationTable() + " CASCADE");
    }

    @Override
    protected VisibilityAttestationStore getStore() {
        return store;
    }
}