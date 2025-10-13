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

import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.testfixtures.LeaseUtil;
import org.eclipse.edc.sql.testfixtures.PostgresqlStoreSetupExtension;
import org.eclipse.edc.telemetry.store.sql.schema.TelemetryBaseSqlDialectStatements;
import org.eclipse.edc.telemetry.store.sql.schema.postgres.PostgresTelemetryRecordStatements;
import org.eclipse.eonax.edc.spi.telemetryagent.TelemetryStoreTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

@ComponentTest
@ExtendWith(PostgresqlStoreSetupExtension.class)
class PostgresTelemetryRecordStoreTest extends TelemetryStoreTestBase {

    private final TelemetryBaseSqlDialectStatements sqlStatements = new PostgresTelemetryRecordStatements();
    private LeaseUtil leaseUtil;
    private SqlTelemetryRecordStore telemetryStore;

    @BeforeEach
    void setUp(PostgresqlStoreSetupExtension extension, QueryExecutor queryExecutor) {

        var typeManager = new JacksonTypeManager();

        leaseUtil = new LeaseUtil(extension.getTransactionContext(), extension::getConnection, sqlStatements, clock);
        telemetryStore = new SqlTelemetryRecordStore(extension.getDataSourceRegistry(), extension.getDatasourceName(),
                extension.getTransactionContext(), typeManager.getMapper(), sqlStatements, queryExecutor, clock,
                "test-connector");

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

}


