package org.eclipse.dse.issuerservice.issuance;

import org.eclipse.dse.issuerservice.issuance.jsondatabase.JsonDatabaseAttestationSourceFactory;
import org.eclipse.dse.issuerservice.issuance.jsondatabase.JsonDatabaseAttestationSourceValidator;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationDefinitionValidatorRegistry;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationSourceFactoryRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;

@Extension(JsonDatabaseAttestationExtension.NAME)
public class JsonDatabaseAttestationExtension implements ServiceExtension {

    public static final String NAME = "JSON Database Attestations Extension";
    public static final String JSON_DATABASE_ATTESTATION_TYPE = "json-database";

    @Inject
    private AttestationSourceFactoryRegistry registry;

    @Inject
    private AttestationDefinitionValidatorRegistry validatorRegistry;

    @Inject
    private TransactionContext transactionContext;

    @Inject
    private QueryExecutor queryExecutor;

    @Inject
    private DataSourceRegistry dataSourceRegistry;

    @Inject
    private TypeManager typeManager;

    @Override
    public void initialize(ServiceExtensionContext context) {
        registry.registerFactory(JSON_DATABASE_ATTESTATION_TYPE,
                new JsonDatabaseAttestationSourceFactory(transactionContext, queryExecutor, dataSourceRegistry, typeManager.getMapper()));
        validatorRegistry.registerValidator(JSON_DATABASE_ATTESTATION_TYPE,
                new JsonDatabaseAttestationSourceValidator());
    }
}
