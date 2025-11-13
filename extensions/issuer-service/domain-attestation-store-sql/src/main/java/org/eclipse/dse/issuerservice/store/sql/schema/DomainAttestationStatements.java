package org.eclipse.dse.issuerservice.store.sql.schema;

import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.sql.statement.SqlStatements;
import org.eclipse.edc.sql.translation.SqlQueryStatement;

public interface DomainAttestationStatements extends SqlStatements {
    default String getDomainAttestationTable() {
        return "domain_attestation";
    }

    default String getIdColumn() {
        return "id";
    }

    default String getHolderIdColumn() {
        return "holder_id";
    }

    default String getDomainColumn() {
        return "domain";
    }

    String getDeleteByIdTemplate();

    String getFindByTemplate();

    String getInsertTemplate();

    String getCountTemplate();

    String getUpdateTemplate();

    SqlQueryStatement createQuery(QuerySpec querySpec);
}
