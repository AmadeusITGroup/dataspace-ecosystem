package org.eclipse.dse.issuerservice.store.sql;

import org.eclipse.dse.issuerservice.store.sql.postgres.VisibilityAttestationPostgresDialectStatements;
import org.eclipse.dse.issuerservice.store.sql.schema.VisibilityAttestationStatements;
import org.eclipse.dse.spi.issuerservice.VisibilityAttestationStore;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.bootstrapper.SqlSchemaBootstrapper;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;

@Extension(value = "SQL Visibility Attestation Store")
public class SqlVisibilityAttestationStoreExtension implements ServiceExtension {

    @Setting(description = "The datasource to be used", defaultValue = DataSourceRegistry.DEFAULT_DATASOURCE,
            key = "edc.sql.store.visibility.datasource")
    private String dataSourceName;

    @Inject
    private DataSourceRegistry dataSourceRegistry;

    @Inject
    private TransactionContext transactionContext;

    @Inject(required = false)
    private VisibilityAttestationStatements statements;

    @Inject
    private TypeManager typeManager;

    @Inject
    private QueryExecutor queryExecutor;

    @Inject
    private SqlSchemaBootstrapper sqlSchemaBootstrapper;

    @Provider
    public VisibilityAttestationStore sqlVisibilityAttestationStore() {
        sqlSchemaBootstrapper.addStatementFromResource(dataSourceName, "visibility-attestation-schema.sql");
        return new SqlVisibilityAttestationStore(dataSourceRegistry, dataSourceName, transactionContext,
                statements == null ? new VisibilityAttestationPostgresDialectStatements() : statements,
                typeManager.getMapper(), queryExecutor);
    }
}