package org.eclipse.dse.telemetrystorage.store.sql.postgres;

import org.eclipse.dse.telemetrystorage.store.sql.schema.BaseSqlDialectStatements;
import org.eclipse.edc.sql.translation.PostgresqlOperatorTranslator;

/**
 * Contains Postgres-specific SQL statements
 */
public class PostgresDialectStatements extends BaseSqlDialectStatements {

    public PostgresDialectStatements() {
        super(new PostgresqlOperatorTranslator());
    }

}
