package org.eclipse.edc.telemetry.store.sql.schema;

import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.telemetry.store.sql.schema.postgres.PostgresTelemetryRecordStatements;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryBaseSqlDialectStatementsTest {

    private final TelemetryBaseSqlDialectStatements statements = new PostgresTelemetryRecordStatements();

    @Test
    void createNextNotLeaseQuery_shouldIncludeResourceKindInJoin() {
        var query = QuerySpec.Builder.newInstance().limit(10).build();

        var result = statements.createNextNotLeaseQuery(query, System.currentTimeMillis());

        var sql = result.getQueryAsString();
        assertThat(sql).contains("LEFT JOIN");
        assertThat(sql).contains("resource_kind");
        assertThat(sql).contains("edc_telemetry_record");
    }

    @Test
    void createNextNotLeaseQuery_shouldIncludeNotLeasedFilter() {
        var query = QuerySpec.Builder.newInstance().limit(10).build();

        var result = statements.createNextNotLeaseQuery(query, System.currentTimeMillis());

        var sql = result.getQueryAsString();
        assertThat(sql).contains("IS NULL OR");
        assertThat(sql).contains("leased_at");
        assertThat(sql).contains("lease_duration");
    }

    @Test
    void createQuery_shouldReturnValidStatement() {
        var query = QuerySpec.Builder.newInstance().limit(5).build();

        var result = statements.createQuery(query);

        assertThat(result).isNotNull();
        assertThat(result.getQueryAsString()).contains("SELECT");
    }
}
