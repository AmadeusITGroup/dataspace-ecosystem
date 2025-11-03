package org.eclipse.edc.eonax.telemetry.services.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public abstract class AzureStorageService {
    BlobContainerClient serviceClient;

    protected AzureStorageService() {
    }

    public String upload(String path, byte[] data) {
        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            BlobClient blobClient = getContainer().getBlobClient(path);
            blobClient.upload(inputStream, data.length, false);
            return blobClient.getBlobUrl();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public InputStream download(String path) {
        return getContainer().getBlobClient(path).openInputStream();
    }

    public abstract BlobContainerClient getContainer();
}
