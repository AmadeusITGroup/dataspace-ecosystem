package org.eclipse.eonax.telemetrystorage.store.sql.schema.postgres;

import org.eclipse.edc.sql.translation.TranslationMapping;
import org.eclipse.eonax.telemetrystorage.store.sql.schema.TelemetryEventStatements;

public class TelemetryEventMapping extends TranslationMapping {
    public TelemetryEventMapping(TelemetryEventStatements statements) {
        add("id", statements.getIdColumn());
        add("contractId", statements.getContractIdColumn());
        add("participantId", statements.getParticipantDidColumn());
        add("responseStatusCode", statements.getResponseStatusCodeColumn());
        add("msgSize", statements.getMsgSizeColumn());
        add("csvId", statements.getCsvIdColumn());
        add("timestamp", statements.getTimestampColumn());
    }
}