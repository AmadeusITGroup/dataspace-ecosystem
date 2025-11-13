package org.eclipse.edc.dse.telemetry.services.storage;

import org.eclipse.edc.spi.monitor.Monitor;

public class StorageServiceFactory {

    public static final String AZURITE = "azurite";
    public static final String AZURE = "azure";

    public static AzureStorageService create(Monitor monitor, String storageType, String azuriteConnectionString,
                                             String azuriteStorageContainer, String azureClientId, String azureClientSecret,
                                             String azureTenantId, String azureStorageContainer, String azureStorageEndpoint) {

        if (AZURITE.equalsIgnoreCase(storageType)) {
            return new AzuriteStorageService(monitor,
                    azuriteConnectionString,
                    azuriteStorageContainer
            );
        } else if (AZURE.equalsIgnoreCase(storageType)) {
            return new RealAzureStorageService(monitor,
                    azureClientId,
                    azureClientSecret,
                    azureTenantId,
                    azureStorageContainer,
                    azureStorageEndpoint
            );
        } else {
            throw new IllegalStateException("Unknown STORAGE_TYPE: " + storageType);
        }
    }
}
