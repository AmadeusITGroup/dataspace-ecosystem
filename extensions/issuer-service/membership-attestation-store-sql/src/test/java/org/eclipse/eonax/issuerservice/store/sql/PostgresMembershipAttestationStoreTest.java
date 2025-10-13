package org.eclipse.eonax.issuerservice.store.sql;


import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.testfixtures.PostgresqlStoreSetupExtension;
import org.eclipse.eonax.issuerservice.store.sql.schema.BaseSqlDialectStatements;
import org.eclipse.eonax.issuerservice.store.sql.schema.postgres.PostgresDialectStatements;
import org.eclipse.eonax.spi.issuerservice.MembershipAttestationStore;
import org.eclipse.eonax.spi.issuerservice.MembershipAttestationStoreTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;

@ComponentTest
@ExtendWith(PostgresqlStoreSetupExtension.class)
class PostgresMembershipAttestationStoreTest extends MembershipAttestationStoreTestBase {

    private final BaseSqlDialectStatements statements = new PostgresDialectStatements();

    private SqlMembershipAttestationStore store;

    @BeforeEach
    void setUp(PostgresqlStoreSetupExtension extension, QueryExecutor queryExecutor) throws IOException {
        var typeManager = new JacksonTypeManager();
        store = new SqlMembershipAttestationStore(extension.getDataSourceRegistry(), extension.getDatasourceName(),
                extension.getTransactionContext(), statements, typeManager.getMapper(), queryExecutor);
        var schema = TestUtils.getResourceFileContentAsString("membership-attestation-schema.sql");
        extension.runQuery(schema);
    }

    @AfterEach
    void tearDown(PostgresqlStoreSetupExtension extension) {
        extension.runQuery("DROP TABLE " + statements.getMembershipAttestationTable() + " CASCADE");
    }

    @Override
    protected MembershipAttestationStore getStore() {
        return store;
    }
}
