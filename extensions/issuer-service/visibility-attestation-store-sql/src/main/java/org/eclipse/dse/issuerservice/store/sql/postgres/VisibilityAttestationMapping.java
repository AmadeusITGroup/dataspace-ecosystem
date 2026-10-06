package org.eclipse.dse.issuerservice.store.sql.postgres;

import org.eclipse.dse.issuerservice.store.sql.schema.VisibilityAttestationStatements;
import org.eclipse.edc.sql.translation.JsonFieldTranslator;
import org.eclipse.edc.sql.translation.TranslationMapping;

public class VisibilityAttestationMapping extends TranslationMapping {

    public VisibilityAttestationMapping(VisibilityAttestationStatements statements) {
        add("id", statements.getIdColumn());
        add("holderId", statements.getHolderIdColumn());
        add("properties", new JsonFieldTranslator(statements.getPropertiesColumn()));
    }
}