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

import org.eclipse.edc.azure.blob.AzureBlobStoreSchema;
import org.eclipse.edc.connector.dataplane.spi.provision.DeprovisionedResource;
import org.eclipse.edc.connector.dataplane.spi.provision.Deprovisioner;
import org.eclipse.edc.connector.dataplane.spi.provision.ProvisionResource;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.security.Vault;

import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.eclipse.edc.connector.provision.azure.blob.ObjectStorageResourceDefinitionGenerator.CONTAINER_NAME_PROPERTY;

/**
 * Deprovisioner for Azure Blob Storage resources.
 * 
 * <p>This deprovisioner cleans up SAS tokens from the vault when a data transfer
 * is completed or terminated.</p>
 */
public class ObjectStorageDeprovisioner implements Deprovisioner {

    private final Vault vault;
    private final Monitor monitor;

    public ObjectStorageDeprovisioner(Vault vault, Monitor monitor) {
        this.vault = vault;
        this.monitor = monitor;
    }

    @Override
    public String supportedType() {
        return AzureBlobStoreSchema.TYPE;
    }

    @Override
    public CompletableFuture<StatusResult<DeprovisionedResource>> deprovision(ProvisionResource provisionResource) {
        var flowId = provisionResource.getFlowId();
        var resourceId = provisionResource.getId();
        var containerName = (String) provisionResource.getProperty(CONTAINER_NAME_PROPERTY);

        monitor.debug(() -> "Deprovisioning Azure Storage resource " + resourceId +
                " for flow " + flowId);

        // Attempt to delete the SAS token from vault
        var sasKeyName = generateSasKeyName(flowId, containerName);
        var deleteResult = vault.deleteSecret(sasKeyName);

        if (deleteResult.failed()) {
            monitor.warning("Failed to delete SAS token from vault for flow " + flowId + 
                    ": " + deleteResult.getFailureDetail());
            // Continue with deprovisioning even if vault deletion fails - token will expire anyway
        }

        var deprovisionedResource = DeprovisionedResource.Builder.from(provisionResource)
                .build();

        monitor.debug(() -> "Successfully deprovisioned Azure Storage resource " + resourceId);

        return completedFuture(StatusResult.success(deprovisionedResource));
    }

    private String generateSasKeyName(String flowId, String containerName) {
        return "azure-sas-" + flowId + "-" + containerName;
    }
}
