package org.eclipse.dse.issuerservice.store.sql.schema;

import org.eclipse.dse.issuerservice.store.sql.postgres.PostgresDialectStatements;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MembershipAttestationStatementsTest {

    private final MembershipAttestationStatements statements = new PostgresDialectStatements();

    @Test
    void getPropertiesColumn() {
        assertThat(statements.getPropertiesColumn()).isEqualTo("properties");
    }

    @Test
    void getInsertTemplate_shouldContainPropertiesColumn() {
        var template = statements.getInsertTemplate();
        assertThat(template).contains("properties");
    }

    @Test
    void getUpdateTemplate_shouldContainPropertiesColumn() {
        var template = statements.getUpdateTemplate();
        assertThat(template).contains("properties");
    }

    @Test
    void getFormatAsJsonOperator_shouldReturnPostgresJsonCast() {
        var operator = ((PostgresDialectStatements) statements).getFormatAsJsonOperator();
        assertThat(operator).isNotNull().isNotBlank();
    }
}
