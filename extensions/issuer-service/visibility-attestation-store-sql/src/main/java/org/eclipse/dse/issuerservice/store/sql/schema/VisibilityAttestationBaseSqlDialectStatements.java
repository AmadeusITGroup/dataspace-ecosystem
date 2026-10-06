package org.eclipse.dse.issuerservice.store.sql.schema;

import org.eclipse.dse.issuerservice.store.sql.postgres.VisibilityAttestationMapping;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.sql.translation.SqlOperatorTranslator;
import org.eclipse.edc.sql.translation.SqlQueryStatement;

import static java.lang.String.format;

public class VisibilityAttestationBaseSqlDialectStatements implements VisibilityAttestationStatements {

    protected final SqlOperatorTranslator operatorTranslator;

    public VisibilityAttestationBaseSqlDialectStatements(SqlOperatorTranslator operatorTranslator) {
        this.operatorTranslator = operatorTranslator;
    }

    @Override
    public String getDeleteByIdTemplate() {
        return executeStatement().delete(getVisibilityAttestationTable(), getIdColumn());
    }

    @Override
    public String getFindByTemplate() {
        return format("SELECT * FROM %s WHERE %s = ?", getVisibilityAttestationTable(), getIdColumn());
    }

    @Override
    public String getInsertTemplate() {
        return executeStatement()
                .column(getIdColumn())
                .column(getHolderIdColumn())
                .jsonColumn(getPropertiesColumn())
                .insertInto(getVisibilityAttestationTable());
    }

    @Override
    public String getCountTemplate() {
        return format("SELECT COUNT (%s) FROM %s WHERE %s = ?", getIdColumn(), getVisibilityAttestationTable(), getIdColumn());
    }

    @Override
    public String getUpdateTemplate() {
        return executeStatement()
                .column(getIdColumn())
                .column(getHolderIdColumn())
                .jsonColumn(getPropertiesColumn())
                .update(getVisibilityAttestationTable(), getIdColumn());
    }

    @Override
    public SqlQueryStatement createQuery(QuerySpec querySpec) {
        var select = format("SELECT * FROM %s", getVisibilityAttestationTable());
        return new SqlQueryStatement(select, querySpec, new VisibilityAttestationMapping(this), operatorTranslator);
    }
}