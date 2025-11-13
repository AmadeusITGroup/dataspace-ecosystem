package org.eclipse.dse.issuerservice.store.sql.postgres;

import org.eclipse.dse.issuerservice.store.sql.schema.DomainAttestationStatements;
import org.eclipse.edc.sql.translation.TranslationMapping;

public class DomainAttestationMapping extends TranslationMapping {
    public DomainAttestationMapping(DomainAttestationStatements statements) {
        add("id", statements.getIdColumn());
        add("holderId", statements.getHolderIdColumn());
        add("domain", statements.getDomainColumn());
    }
}
