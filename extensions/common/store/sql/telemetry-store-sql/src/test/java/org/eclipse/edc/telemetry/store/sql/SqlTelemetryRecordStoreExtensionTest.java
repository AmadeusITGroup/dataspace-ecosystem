package org.eclipse.edc.telemetry.store.sql;

import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.bootstrapper.SqlSchemaBootstrapper;
import org.eclipse.edc.sql.lease.spi.SqlLeaseContextBuilder;
import org.eclipse.edc.sql.lease.spi.SqlLeaseContextBuilderProvider;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class SqlTelemetryRecordStoreExtensionTest {

    private final SqlSchemaBootstrapper bootstrapper = mock();
    private final SqlLeaseContextBuilderProvider leaseProvider = mock();
    private final SqlLeaseContextBuilder leaseContextBuilder = mock();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(DataSourceRegistry.class, mock());
        context.registerService(TransactionContext.class, mock());
        context.registerService(TypeManager.class, new JacksonTypeManager());
        context.registerService(QueryExecutor.class, mock());
        context.registerService(SqlSchemaBootstrapper.class, bootstrapper);
        context.registerService(Clock.class, Clock.systemUTC());
        context.registerService(SqlLeaseContextBuilderProvider.class, leaseProvider);
        when(leaseProvider.createContextBuilder(anyString())).thenReturn(leaseContextBuilder);
    }

    @Test
    void shouldHaveCorrectName(SqlTelemetryRecordStoreExtension ext, ServiceExtensionContext context) {
        assertThat(ext.name()).isEqualTo("SQL Telemetry Record Store");
    }

}
