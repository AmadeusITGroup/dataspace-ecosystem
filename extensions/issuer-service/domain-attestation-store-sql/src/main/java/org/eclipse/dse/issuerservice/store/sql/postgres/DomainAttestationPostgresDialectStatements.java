package org.eclipse.dse.issuerservice.store.sql.postgres;

import org.eclipse.dse.issuerservice.store.sql.schema.DomainAttestationBaseSqlDialectStatements;
import org.eclipse.edc.sql.translation.PostgresqlOperatorTranslator;


public class DomainAttestationPostgresDialectStatements extends DomainAttestationBaseSqlDialectStatements {
    public DomainAttestationPostgresDialectStatements() {
        super(new PostgresqlOperatorTranslator());
    }
}
