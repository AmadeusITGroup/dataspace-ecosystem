package org.eclipse.edc.dse.telemetry.services.storage;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.eclipse.edc.spi.monitor.Monitor;

public class AzuriteStorageService extends AzureStorageService {

    private final Monitor monitor;
    private final String connectionString;
    private final String containerName;

    public AzuriteStorageService(Monitor monitor, String connectionString, String containerName) {
        super();
        this.connectionString = connectionString;
        this.containerName = containerName;
        this.monitor = monitor;
    }

    @Override
    public BlobContainerClient getContainer() {
        if (this.serviceClient == null) {
            this.monitor.info("Creating Blob Container Client for Azurite Storage");
            BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();
            this.serviceClient = serviceClient.getBlobContainerClient(containerName);
        }
        return this.serviceClient;
    }
}
