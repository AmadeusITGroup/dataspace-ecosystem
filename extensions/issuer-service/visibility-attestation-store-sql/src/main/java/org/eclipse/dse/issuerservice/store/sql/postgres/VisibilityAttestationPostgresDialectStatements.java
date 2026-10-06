package org.eclipse.dse.issuerservice.store.sql.postgres;

import org.eclipse.dse.issuerservice.store.sql.schema.VisibilityAttestationBaseSqlDialectStatements;
import org.eclipse.edc.sql.dialect.PostgresDialect;
import org.eclipse.edc.sql.translation.PostgresqlOperatorTranslator;

public class VisibilityAttestationPostgresDialectStatements extends VisibilityAttestationBaseSqlDialectStatements {

    public VisibilityAttestationPostgresDialectStatements() {
        super(new PostgresqlOperatorTranslator());
    }

    @Override
    public String getFormatAsJsonOperator() {
        return PostgresDialect.getJsonCastOperator();
    }
}