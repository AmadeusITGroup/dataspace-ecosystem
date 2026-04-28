package org.eclipse.dse.issuerservice.issuance.jsondatabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationContext;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.testfixtures.PostgresqlStoreSetupExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ComponentTest
@ExtendWith(PostgresqlStoreSetupExtension.class)
class JsonDatabaseAttestationSourceTest {

    private static final String TABLE_NAME = "test_attestation";
    private static final String HOLDER_ID = "participant-1";

    private JsonDatabaseAttestationSource source;

    @BeforeEach
    void setUp(PostgresqlStoreSetupExtension extension, QueryExecutor queryExecutor) {
        extension.runQuery("""
                CREATE TABLE IF NOT EXISTS test_attestation (
                    id VARCHAR PRIMARY KEY DEFAULT gen_random_uuid(),
                    holder_id VARCHAR NOT NULL,
                    name VARCHAR NOT NULL,
                    membership_type VARCHAR NOT NULL,
                    properties JSON NOT NULL DEFAULT '{}'::json
                )
                """);

        source = new JsonDatabaseAttestationSource(
                extension.getDatasourceName(), false, new ObjectMapper(),
                TABLE_NAME, extension.getDataSourceRegistry(),
                queryExecutor, extension.getTransactionContext(),
                "holder_id", "properties");
    }

    @AfterEach
    void tearDown(PostgresqlStoreSetupExtension extension) {
        extension.runQuery("DROP TABLE IF EXISTS " + TABLE_NAME);
    }

    @Test
    void shouldFlattenJsonProperties(PostgresqlStoreSetupExtension extension) {
        extension.runQuery("INSERT INTO test_attestation (holder_id, name, membership_type, properties) " +
                "VALUES ('" + HOLDER_ID + "', 'Test Corp', 'FullMember', " +
                "'{\"companySegment\": \"Airlines\", \"companySize\": \"Large\"}')");

        var context = mockContext(HOLDER_ID);
        var result = source.execute(context);

        assertThat(result.succeeded()).isTrue();
        var map = result.getContent();
        assertThat(map).containsEntry("name", "Test Corp");
        assertThat(map).containsEntry("membership_type", "FullMember");
        assertThat(map).containsEntry("companySegment", "Airlines");
        assertThat(map).containsEntry("companySize", "Large");
        assertThat(map).doesNotContainKey("properties");
    }

    @Test
    void shouldHandleEmptyJsonProperties(PostgresqlStoreSetupExtension extension) {
        extension.runQuery("INSERT INTO test_attestation (holder_id, name, membership_type, properties) " +
                "VALUES ('" + HOLDER_ID + "', 'Test Corp', 'FullMember', '{}')");

        var context = mockContext(HOLDER_ID);
        var result = source.execute(context);

        assertThat(result.succeeded()).isTrue();
        var map = result.getContent();
        assertThat(map).containsEntry("name", "Test Corp");
        assertThat(map).doesNotContainKey("properties");
    }

    @Test
    void shouldReturnEmptyMapWhenNotFoundAndNotRequired(PostgresqlStoreSetupExtension extension) {
        var context = mockContext("non-existent");
        var result = source.execute(context);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void shouldReturnFailureWhenNotFoundAndRequired(PostgresqlStoreSetupExtension extension, QueryExecutor queryExecutor) {
        var requiredSource = new JsonDatabaseAttestationSource(
                extension.getDatasourceName(), true, new ObjectMapper(),
                TABLE_NAME, extension.getDataSourceRegistry(),
                queryExecutor, extension.getTransactionContext(),
                "holder_id", "properties");

        var context = mockContext("non-existent");
        var result = requiredSource.execute(context);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void shouldIncludeAllRegularColumnsInResult(PostgresqlStoreSetupExtension extension) {
        extension.runQuery("INSERT INTO test_attestation (holder_id, name, membership_type, properties) " +
                "VALUES ('" + HOLDER_ID + "', 'Test Corp', 'FullMember', '{\"companySegment\": \"Airlines\"}')");

        var context = mockContext(HOLDER_ID);
        var result = source.execute(context);

        assertThat(result.succeeded()).isTrue();
        var map = result.getContent();
        assertThat(map).containsEntry("holder_id", HOLDER_ID);
        assertThat(map).containsKey("id");
    }

    private AttestationContext mockContext(String participantId) {
        var context = mock(AttestationContext.class);
        when(context.participantId()).thenReturn(participantId);
        return context;
    }
}
