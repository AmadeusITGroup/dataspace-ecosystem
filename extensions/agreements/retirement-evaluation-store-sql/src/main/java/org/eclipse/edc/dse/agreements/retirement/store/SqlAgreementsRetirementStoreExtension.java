/********************************************************************************
 * Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.edc.dse.agreements.retirement.store;

import org.eclipse.edc.dse.agreements.retirement.spi.store.AgreementsRetirementStore;
import org.eclipse.edc.dse.agreements.retirement.spi.types.AgreementConstants;
import org.eclipse.edc.dse.agreements.retirement.store.sql.PostgresAgreementRetirementStatements;
import org.eclipse.edc.dse.agreements.retirement.store.sql.SqlAgreementsRetirementStatements;
import org.eclipse.edc.dse.agreements.retirement.store.sql.SqlAgreementsRetirementStore;
import org.eclipse.edc.dse.common.DseNamespaceConfig;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.bootstrapper.SqlSchemaBootstrapper;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;

@Extension(value = SqlAgreementsRetirementStoreExtension.NAME)
public class SqlAgreementsRetirementStoreExtension implements ServiceExtension {

    protected static final String NAME = "SQL Agreement Retirement Store.";

    @Setting(description = "The datasource to be used", defaultValue = DataSourceRegistry.DEFAULT_DATASOURCE, key = "edc.datasource.agreement.retirement")
    private String dataSourceName;
    
    @Inject
    private DataSourceRegistry dataSourceRegistry;

    @Inject
    private TransactionContext transactionContext;

    @Inject
    private TypeManager typeManager;

    @Inject
    private SqlSchemaBootstrapper sqlSchemaBootstrapper;

    @Inject
    private QueryExecutor queryExecutor;

    @Inject(required = false)
    private SqlAgreementsRetirementStatements statements;

    @Inject
    private DseNamespaceConfig dseNamespaceConfig;

    @Provider
    public AgreementsRetirementStore sqlStore(ServiceExtensionContext context) {
        sqlSchemaBootstrapper.addStatementFromResource(dataSourceName, "schema.sql");
        var agreementConstants = new AgreementConstants(dseNamespaceConfig);
        return new SqlAgreementsRetirementStore(dataSourceRegistry, dataSourceName, transactionContext,
                typeManager.getMapper(), queryExecutor, getStatements(), agreementConstants);
    }

    @Override
    public String name() {
        return NAME;
    }

    private SqlAgreementsRetirementStatements getStatements() {
        return statements == null ? new PostgresAgreementRetirementStatements() : statements;
    }
}
