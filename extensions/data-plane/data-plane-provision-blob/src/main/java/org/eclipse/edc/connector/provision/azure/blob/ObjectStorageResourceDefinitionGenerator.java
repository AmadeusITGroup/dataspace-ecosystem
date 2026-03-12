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
import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.eclipse.edc.connector.dataplane.spi.provision.ProvisionResource;
import org.eclipse.edc.connector.dataplane.spi.provision.ResourceDefinitionGenerator;

/**
 * Generates resource definitions for consumer-side Azure Blob Storage provisioning.
 * 
 * <p>This generator creates {@link ProvisionResource} instances when the
 * data destination is an Azure Blob Storage container. The generated resource definition
 * is used by the {@link ObjectStorageProvisioner} to generate SAS tokens for accessing
 * the destination storage.</p>
 */
public class ObjectStorageResourceDefinitionGenerator implements ResourceDefinitionGenerator {

    /**
     * Property key for storage account name in the provision resource.
     */
    public static final String ACCOUNT_NAME_PROPERTY = "accountName";
    
    /**
     * Property key for container name in the provision resource.
     */
    public static final String CONTAINER_NAME_PROPERTY = "containerName";
    
    /**
     * Property key for folder name in the provision resource.
     */
    public static final String FOLDER_NAME_PROPERTY = "folderName";

    @Override
    public String supportedType() {
        return AzureBlobStoreSchema.TYPE;
    }

    @Override
    public ProvisionResource generate(DataFlow dataFlow) {
        var destination = dataFlow.getDestination();
        if (destination == null) {
            return null;
        }

        var accountName = destination.getStringProperty(AzureBlobStoreSchema.ACCOUNT_NAME);
        var containerName = destination.getStringProperty(AzureBlobStoreSchema.CONTAINER_NAME);
        var folderName = destination.getStringProperty(AzureBlobStoreSchema.FOLDER_NAME);

        if (accountName == null || containerName == null) {
            return null;
        }

        var builder = ProvisionResource.Builder.newInstance()
                .flowId(dataFlow.getId())
                .type(AzureBlobStoreSchema.TYPE)
                .dataAddress(destination)
                .property(ACCOUNT_NAME_PROPERTY, accountName)
                .property(CONTAINER_NAME_PROPERTY, containerName);

        if (folderName != null && !folderName.isBlank()) {
            builder.property(FOLDER_NAME_PROPERTY, folderName);
        }

        return builder.build();
    }
}
