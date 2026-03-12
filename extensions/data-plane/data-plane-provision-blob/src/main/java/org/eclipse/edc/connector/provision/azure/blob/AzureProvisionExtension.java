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

import org.eclipse.edc.azure.blob.api.BlobStoreApi;
import org.eclipse.edc.connector.dataplane.spi.provision.ProvisionerManager;
import org.eclipse.edc.connector.dataplane.spi.provision.ResourceDefinitionGeneratorManager;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

import java.time.Clock;

/**
 * Extension that provides provisioning support for Azure Blob Storage transfers.
 * 
 * <p>This extension registers:</p>
 * <ul>
 *     <li>{@link ObjectStorageResourceDefinitionGenerator} - Generates resource definitions for Azure destinations</li>
 *     <li>{@link ObjectStorageProvisioner} - Creates SAS tokens for accessing Azure storage</li>
 *     <li>{@link ObjectStorageDeprovisioner} - Cleans up SAS tokens from the vault</li>
 * </ul>
 *
 * <p>Configuration:</p>
 * <ul>
 *     <li>{@code edc.azure.provision.sas.token.validity} - SAS token validity in seconds (default: 3600)</li>
 *     <li>{@code edc.azure.provision.sas.permissions} - SAS token permissions (default: "racwdl")</li>
 * </ul>
 */
@Extension(value = AzureProvisionExtension.NAME)
public class AzureProvisionExtension implements ServiceExtension {

    public static final String NAME = "Azure Blob Storage Provisioning Extension";

    private static final long DEFAULT_TOKEN_VALIDITY_SECONDS = 3600;
    private static final String DEFAULT_PERMISSIONS = "racwdl";

    @Setting(description = "SAS token validity in seconds", defaultValue = "3600", key = "edc.azure.provision.sas.token.validity")
    private long tokenValiditySeconds;

    @Setting(description = "SAS token permissions", defaultValue = "racwdl", key = "edc.azure.provision.sas.permissions")
    private String permissions;

    @Inject
    private BlobStoreApi blobStoreApi;

    @Inject
    private Vault vault;

    @Inject
    private ResourceDefinitionGeneratorManager resourceDefinitionGeneratorManager;

    @Inject
    private ProvisionerManager provisionerManager;

    @Inject
    private TypeManager typeManager;

    @Inject
    private Clock clock;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor().withPrefix("AzureProvision");

        // Register the resource definition generator for consumer-side provisioning
        var generator = new ObjectStorageResourceDefinitionGenerator();
        resourceDefinitionGeneratorManager.registerConsumerGenerator(generator);
        monitor.debug("Registered ObjectStorageResourceDefinitionGenerator for consumer-side provisioning");

        // Build and register the provisioner
        var objectMapper = typeManager.getMapper();
        var provisioner = ObjectStorageProvisioner.newInstance()
                .blobStoreApi(blobStoreApi)
                .vault(vault)
                .monitor(monitor)
                .clock(clock)
                .objectMapper(objectMapper)
                .permissions(permissions)
                .tokenValiditySeconds(tokenValiditySeconds)
                .build();
        provisionerManager.register(provisioner);
        monitor.debug("Registered ObjectStorageProvisioner");

        // Build and register the deprovisioner
        var deprovisioner = new ObjectStorageDeprovisioner(vault, monitor);
        provisionerManager.register(deprovisioner);
        monitor.debug("Registered ObjectStorageDeprovisioner");

        monitor.info("Azure Blob Storage Provisioning Extension initialized with token validity: " + 
                tokenValiditySeconds + "s, permissions: " + permissions);
    }
}
