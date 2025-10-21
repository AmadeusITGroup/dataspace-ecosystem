package org.eclipse.eonax.issuerservice.store.sql.schema.postgres;

import org.eclipse.edc.sql.translation.TranslationMapping;
import org.eclipse.eonax.issuerservice.store.sql.schema.DomainAttestationStatements;

public class DomainAttestationMapping extends TranslationMapping {
    public DomainAttestationMapping(DomainAttestationStatements statements) {
        add("id", statements.getIdColumn());
        add("holderId", statements.getHolderIdColumn());
        add("domain", statements.getDomainColumn());
    }
}
