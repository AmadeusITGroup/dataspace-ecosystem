package org.eclipse.dse.issuerservice.store.sql;

import org.eclipse.dse.issuerservice.store.sql.postgres.DomainAttestationPostgresDialectStatements;
import org.eclipse.dse.issuerservice.store.sql.schema.DomainAttestationStatements;
import org.eclipse.dse.spi.issuerservice.DomainAttestationStore;
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

@Extension(value = "SQL Domain Attestation Store")
public class SqlDomainAttestationStoreExtension implements ServiceExtension {

    @Setting(description = "The datasource to be used", defaultValue = DataSourceRegistry.DEFAULT_DATASOURCE, key = "edc.sql.store.domain.datasource")
    private String dataSourceName;

    @Inject
    private DataSourceRegistry dataSourceRegistry;

    @Inject
    private TransactionContext transactionContext;

    @Inject(required = false)
    private DomainAttestationStatements statements;

    @Inject
    private TypeManager typeManager;

    @Inject
    private QueryExecutor queryExecutor;

    @Inject
    private SqlSchemaBootstrapper sqlSchemaBootstrapper;

    @Provider
    public DomainAttestationStore sqlDomainAttestationStore() {
        sqlSchemaBootstrapper.addStatementFromResource(dataSourceName, "domain-attestation-schema.sql");
        return new SqlDomainAttestationStore(dataSourceRegistry, dataSourceName, transactionContext, getStatementImpl(), typeManager.getMapper(), queryExecutor);
    }

    private DomainAttestationStatements getStatementImpl() {
        return statements == null ? new DomainAttestationPostgresDialectStatements() : statements;
    }
}
