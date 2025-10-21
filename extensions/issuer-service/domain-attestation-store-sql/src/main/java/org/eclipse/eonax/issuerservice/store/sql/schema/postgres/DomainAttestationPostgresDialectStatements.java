package org.eclipse.eonax.issuerservice.store.sql.schema.postgres;

import org.eclipse.edc.sql.translation.PostgresqlOperatorTranslator;
import org.eclipse.eonax.issuerservice.store.sql.schema.DomainAttestationBaseSqlDialectStatements;


public class DomainAttestationPostgresDialectStatements extends DomainAttestationBaseSqlDialectStatements {
    public DomainAttestationPostgresDialectStatements() {
        super(new PostgresqlOperatorTranslator());
    }
}
