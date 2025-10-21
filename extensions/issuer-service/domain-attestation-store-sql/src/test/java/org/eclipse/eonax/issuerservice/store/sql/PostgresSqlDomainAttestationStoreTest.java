package org.eclipse.eonax.issuerservice.store.sql;

import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.testfixtures.PostgresqlStoreSetupExtension;
import org.eclipse.eonax.issuerservice.store.sql.schema.postgres.DomainAttestationPostgresDialectStatements;
import org.eclipse.eonax.spi.issuerservice.DomainAttestationStore;
import org.eclipse.eonax.spi.issuerservice.DomainAttestationStoreTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;

@ComponentTest
@ExtendWith(PostgresqlStoreSetupExtension.class)
class PostgresSqlDomainAttestationStoreTest extends DomainAttestationStoreTestBase {
    private final DomainAttestationPostgresDialectStatements statements = new DomainAttestationPostgresDialectStatements();

    private SqlDomainAttestationStore store;

    @Override
    protected DomainAttestationStore getStore() {
        return store;
    }

    @BeforeEach
    void setUp(PostgresqlStoreSetupExtension extension, QueryExecutor queryExecutor) throws IOException {
        var typeManager = new JacksonTypeManager();
        store = new SqlDomainAttestationStore(
                extension.getDataSourceRegistry(),
                extension.getDatasourceName(),
                extension.getTransactionContext(),
                statements,
                typeManager.getMapper(),
                queryExecutor
        );
        var schema = TestUtils.getResourceFileContentAsString("domain-attestation-schema.sql");
        extension.runQuery(schema);
    }

    @AfterEach
    void tearDown(PostgresqlStoreSetupExtension extension) {
        extension.runQuery("DROP TABLE " + statements.getDomainAttestationTable() + " CASCADE");
    }
}