package org.eclipse.dse.issuerservice.store.sql;

import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.bootstrapper.SqlSchemaBootstrapper;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(DependencyInjectionExtension.class)
class SqlVisibilityAttestationStoreExtensionTest {

    private final SqlSchemaBootstrapper bootstrapper = mock();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(DataSourceRegistry.class, mock());
        context.registerService(TransactionContext.class, mock());
        context.registerService(TypeManager.class, new JacksonTypeManager());
        context.registerService(QueryExecutor.class, mock());
        context.registerService(SqlSchemaBootstrapper.class, bootstrapper);
    }

    @Test
    void shouldProvideSqlStore(SqlVisibilityAttestationStoreExtension extension) {
        assertThat(extension.sqlVisibilityAttestationStore())
                .isInstanceOf(SqlVisibilityAttestationStore.class);
        verify(bootstrapper).addStatementFromResource(nullable(String.class), eq("visibility-attestation-schema.sql"));
    }
}