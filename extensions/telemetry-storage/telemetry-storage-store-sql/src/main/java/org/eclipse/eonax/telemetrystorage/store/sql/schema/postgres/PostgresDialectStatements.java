package org.eclipse.eonax.telemetrystorage.store.sql.schema.postgres;

import org.eclipse.edc.sql.translation.PostgresqlOperatorTranslator;
import org.eclipse.eonax.telemetrystorage.store.sql.schema.BaseSqlDialectStatements;

/**
 * Contains Postgres-specific SQL statements
 */
public class PostgresDialectStatements extends BaseSqlDialectStatements {

    public PostgresDialectStatements() {
        super(new PostgresqlOperatorTranslator());
    }

}
