/*
 *  Copyright (c) 2024 Amadeus SA
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       2024 Amadeus SA - initial implementation
 *
 */

package org.eclipse.edc.telemetry.store.sql.schema.postgres;

import org.eclipse.edc.sql.dialect.PostgresDialect;
import org.eclipse.edc.sql.translation.PostgresqlOperatorTranslator;
import org.eclipse.edc.telemetry.store.sql.schema.TelemetryBaseSqlDialectStatements;

public class PostgresTelemetryRecordStatements extends TelemetryBaseSqlDialectStatements {

    public PostgresTelemetryRecordStatements() {
        super(new PostgresqlOperatorTranslator());
    }

    @Override
    public String getFormatAsJsonOperator() {
        return PostgresDialect.getJsonCastOperator();
    }
}
