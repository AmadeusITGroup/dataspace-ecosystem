package org.eclipse.edc.dse.telemetry.services.storage;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.eclipse.edc.spi.monitor.Monitor;

public class RealAzureStorageService extends AzureStorageService {

    private final Monitor monitor;
    private final String clientId;
    private final String clientSecret;
    private final String tenantId;
    private final String containerName;
    private final String storageEndpoint;

    public RealAzureStorageService(Monitor monitor, String clientId, String clientSecret, String tenantId, String containerName, String storageEndpoint) {
        super();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tenantId = tenantId;
        this.containerName = containerName;
        this.monitor = monitor;
        this.storageEndpoint = storageEndpoint;
    }

    @Override
    public BlobContainerClient getContainer() {
        if (this.serviceClient == null) {
            ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .tenantId(tenantId)
                    .build();

            BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                    .endpoint(storageEndpoint)
                    .credential(credential)
                    .buildClient();

            // For real Azure storages im not creating the container automatically here in the code
            // I think its safer to create it manually
            this.serviceClient = serviceClient.getBlobContainerClient(containerName);
        }

        return this.serviceClient;
    }
}
