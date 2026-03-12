/*
 *  Copyright (c) 2024 Dataspace Ecosystem
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Dataspace Ecosystem - initial API and implementation
 *       Based on Eclipse EDC Technology-Azure
 *
 */

package org.eclipse.edc.connector.provision.azure.blob;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.azure.blob.AzureBlobStoreSchema;
import org.eclipse.edc.azure.blob.AzureSasToken;
import org.eclipse.edc.azure.blob.api.BlobStoreApi;
import org.eclipse.edc.connector.dataplane.spi.provision.ProvisionResource;
import org.eclipse.edc.connector.dataplane.spi.provision.ProvisionedResource;
import org.eclipse.edc.connector.dataplane.spi.provision.Provisioner;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.types.domain.DataAddress;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.eclipse.edc.connector.provision.azure.blob.ObjectStorageResourceDefinitionGenerator.ACCOUNT_NAME_PROPERTY;
import static org.eclipse.edc.connector.provision.azure.blob.ObjectStorageResourceDefinitionGenerator.CONTAINER_NAME_PROPERTY;
import static org.eclipse.edc.connector.provision.azure.blob.ObjectStorageResourceDefinitionGenerator.FOLDER_NAME_PROPERTY;

/**
 * Provisioner for Azure Blob Storage resources.
 * 
 * <p>This provisioner generates SAS tokens for accessing Azure Blob Storage containers
 * on the consumer side of a data transfer. The generated SAS tokens are stored in the
 * vault and referenced in the provisioned DataAddress.</p>
 *
 * <p>The provisioner supports configuration of:</p>
 * <ul>
 *     <li>SAS token validity duration</li>
 *     <li>SAS token permissions (read, add, create, write, delete, list)</li>
 * </ul>
 */
public class ObjectStorageProvisioner implements Provisioner {

    private static final String DEFAULT_PERMISSIONS = "racwdl"; // read, add, create, write, delete, list
    private static final long DEFAULT_TOKEN_VALIDITY_SECONDS = 3600; // 1 hour

    private final BlobStoreApi blobStoreApi;
    private final Vault vault;
    private final Monitor monitor;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final String permissions;
    private final long tokenValiditySeconds;

    private ObjectStorageProvisioner(Builder builder) {
        this.blobStoreApi = builder.blobStoreApi;
        this.vault = builder.vault;
        this.monitor = builder.monitor;
        this.clock = builder.clock;
        this.objectMapper = builder.objectMapper;
        this.permissions = builder.permissions;
        this.tokenValiditySeconds = builder.tokenValiditySeconds;
    }

    @Override
    public String supportedType() {
        return AzureBlobStoreSchema.TYPE;
    }

    @Override
    public CompletableFuture<StatusResult<ProvisionedResource>> provision(ProvisionResource provisionResource) {
        var flowId = provisionResource.getFlowId();
        var accountName = (String) provisionResource.getProperty(ACCOUNT_NAME_PROPERTY);
        var containerName = (String) provisionResource.getProperty(CONTAINER_NAME_PROPERTY);
        var folderName = (String) provisionResource.getProperty(FOLDER_NAME_PROPERTY);

        monitor.debug(() -> "Provisioning Azure Storage resource for flow " + flowId +
                " - account: " + accountName + ", container: " + containerName);

        try {
            // Compute expiry once so both the SAS token and its serialized form share the exact same timestamp.
            var expiry = OffsetDateTime.now(clock).plusSeconds(tokenValiditySeconds);
            var sasToken = blobStoreApi.createContainerSasToken(accountName, containerName, permissions, expiry);

            // Store SAS token in vault as JSON
            var sasKeyName = generateSasKeyName(flowId, containerName);
            var tokenJson = serializeSasToken(sasToken, expiry);
            var storeResult = vault.storeSecret(sasKeyName, tokenJson);
            
            if (storeResult.failed()) {
                return completedFuture(StatusResult.failure(ResponseStatus.FATAL_ERROR,
                        "Failed to store SAS token in vault: " + storeResult.getFailureDetail()));
            }

            // Build the provisioned DataAddress with reference to the SAS key
            var dataAddressBuilder = DataAddress.Builder.newInstance()
                    .type(AzureBlobStoreSchema.TYPE)
                    .property(AzureBlobStoreSchema.ACCOUNT_NAME, accountName)
                    .property(AzureBlobStoreSchema.CONTAINER_NAME, containerName)
                    .keyName(sasKeyName);

            // Add folder name if specified
            if (folderName != null && !folderName.isBlank()) {
                dataAddressBuilder.property(AzureBlobStoreSchema.FOLDER_NAME, folderName);
            }

            // Build the provisioned resource
            var provisionedResource = ProvisionedResource.Builder.from(provisionResource)
                    .dataAddress(dataAddressBuilder.build())
                    .build();

            monitor.debug(() -> "Successfully provisioned Azure Storage resource for flow " + flowId);

            return completedFuture(StatusResult.success(provisionedResource));

        } catch (Exception e) {
            monitor.severe("Failed to provision Azure Storage resource for flow " + flowId, e);
            return completedFuture(StatusResult.failure(ResponseStatus.FATAL_ERROR,
                    "Failed to provision Azure Storage resource: " + e.getMessage()));
        }
    }

    private String serializeSasToken(String sasToken, OffsetDateTime expiry) throws JsonProcessingException {
        var token = new AzureSasToken(sasToken, expiry.toInstant().toEpochMilli());
        return objectMapper.writeValueAsString(token);
    }

    private String generateSasKeyName(String flowId, String containerName) {
        return "azure-sas-" + flowId + "-" + containerName;
    }

    public static Builder newInstance() {
        return new Builder();
    }

    public static class Builder {
        private BlobStoreApi blobStoreApi;
        private Vault vault;
        private Monitor monitor;
        private Clock clock = Clock.systemUTC();
        private ObjectMapper objectMapper;
        private String permissions = DEFAULT_PERMISSIONS;
        private long tokenValiditySeconds = DEFAULT_TOKEN_VALIDITY_SECONDS;

        private Builder() {
        }

        public Builder blobStoreApi(BlobStoreApi blobStoreApi) {
            this.blobStoreApi = blobStoreApi;
            return this;
        }

        public Builder vault(Vault vault) {
            this.vault = vault;
            return this;
        }

        public Builder monitor(Monitor monitor) {
            this.monitor = monitor;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public Builder permissions(String permissions) {
            this.permissions = permissions;
            return this;
        }

        public Builder tokenValiditySeconds(long tokenValiditySeconds) {
            this.tokenValiditySeconds = tokenValiditySeconds;
            return this;
        }

        public ObjectStorageProvisioner build() {
            if (blobStoreApi == null) {
                throw new IllegalStateException("BlobStoreApi is required");
            }
            if (vault == null) {
                throw new IllegalStateException("Vault is required");
            }
            if (monitor == null) {
                throw new IllegalStateException("Monitor is required");
            }
            if (objectMapper == null) {
                throw new IllegalStateException("ObjectMapper is required");
            }
            return new ObjectStorageProvisioner(this);
        }
    }
}
