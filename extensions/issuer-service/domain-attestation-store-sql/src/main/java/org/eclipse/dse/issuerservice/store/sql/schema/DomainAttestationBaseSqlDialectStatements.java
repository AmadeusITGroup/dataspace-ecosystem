package org.eclipse.dse.issuerservice.store.sql.schema;

import org.eclipse.dse.issuerservice.store.sql.postgres.DomainAttestationMapping;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.sql.translation.SqlOperatorTranslator;
import org.eclipse.edc.sql.translation.SqlQueryStatement;

import static java.lang.String.format;

public class DomainAttestationBaseSqlDialectStatements implements DomainAttestationStatements {

    protected final SqlOperatorTranslator operatorTranslator;

    public DomainAttestationBaseSqlDialectStatements(SqlOperatorTranslator operatorTranslator) {
        this.operatorTranslator = operatorTranslator;
    }

    @Override
    public String getDeleteByIdTemplate() {
        return executeStatement().delete(getDomainAttestationTable(), getIdColumn());
    }

    @Override
    public String getFindByTemplate() {
        return format("SELECT * FROM %s WHERE %s = ?", getDomainAttestationTable(), getIdColumn());
    }

    @Override
    public String getInsertTemplate() {
        return executeStatement()
             .column(getHolderIdColumn())
             .column(getDomainColumn())
             .insertInto(getDomainAttestationTable());
    }

    @Override
    public String getCountTemplate() {
        return format("SELECT COUNT (%s) FROM %s WHERE %s = ?",
                getIdColumn(),
                getDomainAttestationTable(),
                getIdColumn());
    }

    @Override
    public String getUpdateTemplate() {
        return executeStatement()
                .column(getIdColumn())
                .column(getHolderIdColumn())
                .column(getDomainColumn())
                .update(getDomainAttestationTable(), getIdColumn());
    }

    @Override
    public SqlQueryStatement createQuery(QuerySpec querySpec) {
        var select = format("SELECT * FROM %s", getDomainAttestationTable());
        return new SqlQueryStatement(select, querySpec, new DomainAttestationMapping(this), operatorTranslator);
    }
}
