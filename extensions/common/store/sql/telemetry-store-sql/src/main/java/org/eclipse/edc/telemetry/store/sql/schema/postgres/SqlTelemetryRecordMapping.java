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

import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.sql.lease.StatefulEntityMapping;
import org.eclipse.edc.sql.translation.JsonFieldTranslator;
import org.eclipse.edc.sql.translation.SqlOperator;
import org.eclipse.edc.sql.translation.WhereClause;
import org.eclipse.edc.telemetry.store.sql.schema.TelemetryRecordStatements;
import org.eclipse.eonax.edc.spi.telemetryagent.TelemetryRecord;
import org.eclipse.eonax.edc.spi.telemetryagent.TelemetryRecordStates;

import java.util.function.Function;

import static java.util.Objects.requireNonNullElse;

/**
 * Maps fields of a {@link TelemetryRecord} onto the corresponding SQL schema (= column names) enabling access through Postgres JSON operators where applicable
 */
public class SqlTelemetryRecordMapping extends StatefulEntityMapping {

    public SqlTelemetryRecordMapping(TelemetryRecordStatements statements) {
        super(statements, state -> TelemetryRecordStates.valueOf(state).code());
        add("properties", new JsonFieldTranslator(statements.getPropertiesColumn()));
    }

    /**
     * Returns a function that translates the right operand class into the left operand for the specified field path.
     * It tries to get the translator with the argument passed. If null is returned, it looks up into 'properties'.
     * If null is still returned, it looks into properties wrapping the left operand with '', to permit handling property keys that contain a dot.
     *
     * @param fieldPath the path name.
     * @return a function that translates the right operand class into the left operand.
     */
    @Override
    public Function<Class<?>, String> getFieldTranslator(String fieldPath) {
        Function<Class<?>, String> translator = super.getFieldTranslator(fieldPath);
        return requireNonNullElse(translator, fieldPath.contains("'")
                ? super.getFieldTranslator("properties.%s".formatted(fieldPath))
                : super.getFieldTranslator("properties.'%s'".formatted(fieldPath)));
    }

    /**
     * Permit to get the {@link WhereClause} for properties when only the property name is defined.
     * It tries to get it with the argument passed, if null is returned it looks up into 'properties', if null is returned
     * it looks into properties wrapping the left operand with '', to permit handling property keys that contain a dot.
     *
     * @param criterion the criterion.
     * @param operator  the operator.
     * @return the {@link WhereClause}.
     */
    @Override
    public WhereClause getWhereClause(Criterion criterion, SqlOperator operator) {
        return requireNonNullElse(super.getWhereClause(criterion, operator), criterion.getOperandLeft().toString().contains("'")
                ? super.getWhereClause(criterion.withLeftOperand("properties.%s".formatted(criterion.getOperandLeft())), operator)
                : super.getWhereClause(criterion.withLeftOperand("properties.'%s'".formatted(criterion.getOperandLeft())), operator));
    }

}
