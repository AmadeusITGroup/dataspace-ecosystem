/*
 *  Copyright (c) 2022 Daimler TSS GmbH
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Daimler TSS GmbH - Initial API and Implementation
 *       Microsoft Corporation - refactoring
 *
 */

package org.eclipse.eonax.issuerservice.store.sql;


import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.bootstrapper.SqlSchemaBootstrapper;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.eonax.issuerservice.store.sql.schema.MembershipAttestationStatements;
import org.eclipse.eonax.issuerservice.store.sql.schema.postgres.PostgresDialectStatements;
import org.eclipse.eonax.spi.issuerservice.MembershipAttestationStore;

@Extension(value = "SQL Membership Attestation Store")
public class SqlMembershipAttestationStoreExtension implements ServiceExtension {


    @Setting(description = "The datasource to be used", defaultValue = DataSourceRegistry.DEFAULT_DATASOURCE, key = "edc.sql.store.membership.datasource")
    private String dataSourceName;

    @Inject
    private DataSourceRegistry dataSourceRegistry;

    @Inject
    private TransactionContext transactionContext;

    @Inject(required = false)
    private MembershipAttestationStatements statements;

    @Inject
    private TypeManager typeManager;

    @Inject
    private QueryExecutor queryExecutor;

    @Inject
    private SqlSchemaBootstrapper sqlSchemaBootstrapper;

    @Provider
    public MembershipAttestationStore sqlMembershipAttestationStore() {
        sqlSchemaBootstrapper.addStatementFromResource(dataSourceName, "membership-attestation-schema.sql");
        return new SqlMembershipAttestationStore(dataSourceRegistry, dataSourceName, transactionContext, getStatementImpl(), typeManager.getMapper(), queryExecutor);
    }

    private MembershipAttestationStatements getStatementImpl() {
        return statements == null ? new PostgresDialectStatements() : statements;
    }

}
