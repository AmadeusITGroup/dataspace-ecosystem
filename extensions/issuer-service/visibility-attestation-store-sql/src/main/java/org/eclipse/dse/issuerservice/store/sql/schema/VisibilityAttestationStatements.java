package org.eclipse.dse.issuerservice.store.sql.schema;

import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.sql.statement.SqlStatements;
import org.eclipse.edc.sql.translation.SqlQueryStatement;

public interface VisibilityAttestationStatements extends SqlStatements {

    default String getVisibilityAttestationTable() {
        return "visibility_attestation";
    }

    default String getIdColumn() {
        return "id";
    }

    default String getHolderIdColumn() {
        return "holder_id";
    }

    default String getPropertiesColumn() {
        return "properties";
    }

    String getDeleteByIdTemplate();

    String getFindByTemplate();

    String getInsertTemplate();

    String getCountTemplate();

    String getUpdateTemplate();

    SqlQueryStatement createQuery(QuerySpec querySpec);
}