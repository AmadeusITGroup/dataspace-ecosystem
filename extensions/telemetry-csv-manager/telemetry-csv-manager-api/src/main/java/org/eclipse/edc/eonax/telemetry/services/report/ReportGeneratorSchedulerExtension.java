package org.eclipse.edc.eonax.telemetry.services.report;

import org.eclipse.edc.eonax.telemetry.repository.JpaUtil;
import org.eclipse.edc.eonax.telemetry.services.storage.AzureStorageService;
import org.eclipse.edc.eonax.telemetry.services.storage.StorageServiceFactory;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.time.Clock;

@Extension(value = "Report Generator Scheduler Extension")
public class ReportGeneratorSchedulerExtension implements ServiceExtension {

    public static final String PERSISTENCE_UNIT_NAME = "myPU";

    @Setting(description = "Datasource Default Url", key = "edc.datasource.default.url")
    public String datasourceDefaultUrl;

    @Setting(description = "Datasource Default User", key = "edc.datasource.default.user")
    public String datasourceDefaultUser;

    @Setting(description = "Datasource Default Password", key = "edc.datasource.default.password")
    public String datasourceDefaultPassword;

    @Setting(defaultValue = "azurite", description = "Blob Storage Type", key = "storage.type")
    public String blobStorageType;

    @Setting(description = "Azurite Connection String", key = "azurite.connection.string", required = false)
    public String azuriteConnectionString;

    @Setting(description = "Azurite Storage Container", key = "azurite.storage.container", required = false)
    public String azuriteStorageContainer;

    @Setting(description = "Azure Client Id", key = "azure.client.id", required = false)
    public String azureClientId;

    @Setting(description = "Azure Client Secret", key = "azure.client.secret", required = false)
    public String azureClientSecret;

    @Setting(description = "Azure Tenant Id", key = "azure.tenant.id", required = false)
    public String azureTenantId;

    @Setting(description = "Azure Storage Container", key = "azure.storage.container", required = false)
    public String azureStorageContainer;

    @Setting(description = "Azure Storage Endpoint", key = "azure.storage.endpoint", required = false)
    public String azureStorageEndpoint;

    @Inject
    private Monitor monitor;

    public static AzureStorageService azureStorageService;

    // TODO: This field is accessed directly in the Controller which is not a good practice, for now we will keep it
    // but we intend to solve this problem when we implement FDPT-84292
    public static ReportGeneratorScheduler scheduler;

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor = context.getMonitor();
        monitor.info("Initializing Report Generator Scheduler Extension...");
        azureStorageService = StorageServiceFactory.create(monitor, blobStorageType, azuriteConnectionString, azuriteStorageContainer,
                azureClientId, azureClientSecret, azureTenantId, azureStorageContainer, azureStorageEndpoint);
        JpaUtil.init(PERSISTENCE_UNIT_NAME, datasourceDefaultUrl, datasourceDefaultUser, datasourceDefaultPassword);

        scheduler = new ReportGeneratorScheduler(monitor, azureStorageService, Clock.systemDefaultZone());
        scheduler.start();
    }

    @Override
    public void shutdown() {
        if (scheduler != null) {
            scheduler.stop();
        }
        JpaUtil.shutdown();
    }
}
