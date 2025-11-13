package org.eclipse.dse.telemetrystorage.store.sql.postgres;

import org.eclipse.dse.telemetrystorage.store.sql.schema.TelemetryEventStatements;
import org.eclipse.edc.sql.translation.TranslationMapping;

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