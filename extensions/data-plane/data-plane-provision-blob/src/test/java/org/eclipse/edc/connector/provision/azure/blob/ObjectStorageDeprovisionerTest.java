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
 *
 */

package org.eclipse.edc.connector.provision.azure.blob;

import org.eclipse.edc.azure.blob.AzureBlobStoreSchema;
import org.eclipse.edc.connector.dataplane.spi.provision.ProvisionResource;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.provision.azure.blob.ObjectStorageResourceDefinitionGenerator.CONTAINER_NAME_PROPERTY;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObjectStorageDeprovisionerTest {

    private static final String TEST_FLOW_ID = "flow-id";
    private static final String TEST_CONTAINER = "test-container";

    private final Vault vault = mock();
    private final Monitor monitor = mock();

    private ObjectStorageDeprovisioner deprovisioner;

    @BeforeEach
    void setUp() {
        deprovisioner = new ObjectStorageDeprovisioner(vault, monitor);
    }

    @Test
    @DisplayName("supportedType returns AzureStorage type")
    void supportedType_returnsAzureStorageType() {
        assertThat(deprovisioner.supportedType()).isEqualTo(AzureBlobStoreSchema.TYPE);
    }

    @Nested
    @DisplayName("deprovision")
    class Deprovision {

        @Test
        @DisplayName("deletes SAS token from vault and returns success")
        void deprovision_success_returnsDeprovisionedResource() {
            when(vault.deleteSecret(sasKeyName())).thenReturn(Result.success());

            var result = deprovisioner.deprovision(createProvisionResource()).join();

            assertThat(result.succeeded()).isTrue();
            assertThat(result.getContent()).isNotNull();
            verify(vault).deleteSecret(sasKeyName());
        }

        @Test
        @DisplayName("returns success even when vault deletion fails")
        void deprovision_vaultDeleteFails_stillReturnsSuccess() {
            when(vault.deleteSecret(sasKeyName())).thenReturn(Result.failure("vault unavailable"));

            var result = deprovisioner.deprovision(createProvisionResource()).join();

            assertThat(result.succeeded()).isTrue();
            assertThat(result.getContent()).isNotNull();
            verify(vault).deleteSecret(sasKeyName());
            verify(monitor).warning(contains("Failed to delete SAS token from vault"));
        }
    }

    private ProvisionResource createProvisionResource() {
        return ProvisionResource.Builder.newInstance()
                .flowId(TEST_FLOW_ID)
                .type(AzureBlobStoreSchema.TYPE)
                .dataAddress(DataAddress.Builder.newInstance()
                        .type(AzureBlobStoreSchema.TYPE)
                        .property(AzureBlobStoreSchema.CONTAINER_NAME, TEST_CONTAINER)
                        .build())
                .properties(Map.of(CONTAINER_NAME_PROPERTY, TEST_CONTAINER))
                .build();
    }

    private String sasKeyName() {
        return "azure-sas-" + TEST_FLOW_ID + "-" + TEST_CONTAINER;
    }
}
