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
 *       Amadeus SA - initial API and implementation
 *
 */

package org.eclipse.edc.telemetry.store.sql;

import org.eclipse.dse.edc.spi.telemetryagent.TelemetryStoreTestBase;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.lease.SqlLeaseContextBuilderImpl;
import org.eclipse.edc.sql.testfixtures.LeaseUtil;
import org.eclipse.edc.sql.testfixtures.PostgresqlStoreSetupExtension;
import org.eclipse.edc.telemetry.store.sql.schema.TelemetryBaseSqlDialectStatements;
import org.eclipse.edc.telemetry.store.sql.schema.postgres.PostgresTelemetryRecordStatements;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.dse.edc.spi.telemetryagent.TelemetryRecordStates.RECEIVED;
import static org.eclipse.dse.edc.spi.telemetryagent.TelemetryRecordStates.SENT;

@ComponentTest
@ExtendWith(PostgresqlStoreSetupExtension.class)
class PostgresTelemetryRecordStoreTest extends TelemetryStoreTestBase {

    private final TelemetryBaseSqlDialectStatements sqlStatements = new PostgresTelemetryRecordStatements();
    private LeaseUtil leaseUtil;
    private SqlTelemetryRecordStore telemetryStore;

    @BeforeEach
    void setUp(PostgresqlStoreSetupExtension extension, QueryExecutor queryExecutor) {

        var typeManager = new JacksonTypeManager();
        var leaseContextBuilder = SqlLeaseContextBuilderImpl.with(extension.getTransactionContext(), CONNECTOR_NAME, sqlStatements.getTelemetryRecordTable(), sqlStatements, clock, queryExecutor);

        leaseUtil = new LeaseUtil(extension.getTransactionContext(), extension::getConnection, sqlStatements.getTelemetryRecordTable(), sqlStatements, clock);
        telemetryStore = new SqlTelemetryRecordStore(extension.getDataSourceRegistry(), extension.getDatasourceName(),
                extension.getTransactionContext(), typeManager.getMapper(), sqlStatements, leaseContextBuilder, queryExecutor, clock);

        var schema = TestUtils.getResourceFileContentAsString("telemetry-record-schema.sql");
        extension.runQuery(schema);
    }

    @AfterEach
    void tearDown(PostgresqlStoreSetupExtension setupExtension) {
        setupExtension.runQuery("DROP TABLE " + sqlStatements.getTelemetryRecordTable() + " CASCADE");
    }

    @Override
    protected SqlTelemetryRecordStore getTelemetryStore() {
        return telemetryStore;
    }

    @Override
    protected void leaseEntity(String recordId, String owner, Duration duration) {
        leaseUtil.leaseEntity(recordId, owner, duration);
    }

    @Override
    protected boolean isLeasedBy(String participantId, String owner) {
        return leaseUtil.isLeased(participantId, owner);
    }

    @Test
    void nextNotLeased_whenFilteringByState_shouldUseValidSql() {
        var sent = getRecord(SENT.code());
        var received = getRecord(RECEIVED.code());
        getTelemetryStore().save(sent);
        getTelemetryStore().save(received);

        var records = getTelemetryStore().nextNotLeased(10, new Criterion("state", "=", RECEIVED.code()));

        assertThat(records).extracting("id").containsExactly(received.getId());
    }

}
