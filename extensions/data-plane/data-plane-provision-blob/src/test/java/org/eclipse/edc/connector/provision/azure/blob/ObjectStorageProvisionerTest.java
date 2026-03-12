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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.azure.blob.AzureBlobStoreSchema;
import org.eclipse.edc.azure.blob.api.BlobStoreApi;
import org.eclipse.edc.connector.dataplane.spi.provision.ProvisionResource;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.security.Vault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.connector.provision.azure.blob.ObjectStorageResourceDefinitionGenerator.ACCOUNT_NAME_PROPERTY;
import static org.eclipse.edc.connector.provision.azure.blob.ObjectStorageResourceDefinitionGenerator.CONTAINER_NAME_PROPERTY;
import static org.eclipse.edc.connector.provision.azure.blob.ObjectStorageResourceDefinitionGenerator.FOLDER_NAME_PROPERTY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

class ObjectStorageProvisionerTest {

    private static final String TEST_ACCOUNT = "testaccount";
    private static final String TEST_CONTAINER = "testcontainer";
    private static final String TEST_FOLDER = "data/output/";
    private static final String TEST_SAS_TOKEN = "sv=2021-06-08&ss=b&srt=sco&sp=rwdlacup&se=2024-01-01T00:00:00Z&st=2023-01-01T00:00:00Z&spr=https&sig=signature";

    private final BlobStoreApi blobStoreApi = mock();
    private final Vault vault = mock();
    private final Monitor monitor = mock();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock fixedClock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneId.of("UTC"));

    private ObjectStorageProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = ObjectStorageProvisioner.newInstance()
                .blobStoreApi(blobStoreApi)
                .vault(vault)
                .monitor(monitor)
                .clock(fixedClock)
                .objectMapper(objectMapper)
                .permissions("racwdl")
                .tokenValiditySeconds(3600)
                .build();
    }

    @Test
    @DisplayName("supportedType returns AzureStorage type")
    void supportedType_returnsAzureStorageType() {
        assertThat(provisioner.supportedType()).isEqualTo(AzureBlobStoreSchema.TYPE);
    }

    @Nested
    @DisplayName("provision")
    class Provision {

        @Test
        @DisplayName("successfully provisions Azure storage resource")
        void provision_success_returnsProvisionedResource() {
            when(blobStoreApi.createContainerSasToken(eq(TEST_ACCOUNT), eq(TEST_CONTAINER), eq("racwdl"), any(OffsetDateTime.class)))
                    .thenReturn(TEST_SAS_TOKEN);
            when(vault.storeSecret(anyString(), anyString())).thenReturn(Result.success());

            var provisionResource = createProvisionResource(TEST_ACCOUNT, TEST_CONTAINER, TEST_FOLDER);

            var result = provisioner.provision(provisionResource).join();

            assertThat(result.succeeded()).isTrue();
            var provisionedResource = result.getContent();
            assertThat(provisionedResource).isNotNull();
            assertThat(provisionedResource.getDataAddress()).isNotNull();
            assertThat(provisionedResource.getDataAddress().getType()).isEqualTo(AzureBlobStoreSchema.TYPE);
            assertThat(provisionedResource.getDataAddress().getStringProperty(AzureBlobStoreSchema.ACCOUNT_NAME))
                    .isEqualTo(TEST_ACCOUNT);
            assertThat(provisionedResource.getDataAddress().getStringProperty(AzureBlobStoreSchema.CONTAINER_NAME))
                    .isEqualTo(TEST_CONTAINER);
            assertThat(provisionedResource.getDataAddress().getStringProperty(AzureBlobStoreSchema.FOLDER_NAME))
                    .isEqualTo(TEST_FOLDER);
            assertThat(provisionedResource.getDataAddress().getKeyName()).isNotNull();
        }

        @Test
        @DisplayName("provisions without folder name when not provided")
        void provision_withoutFolderName_returnsProvisionedResourceWithoutFolder() {
            when(blobStoreApi.createContainerSasToken(eq(TEST_ACCOUNT), eq(TEST_CONTAINER), eq("racwdl"), any(OffsetDateTime.class)))
                    .thenReturn(TEST_SAS_TOKEN);
            when(vault.storeSecret(anyString(), anyString())).thenReturn(Result.success());

            var provisionResource = createProvisionResource(TEST_ACCOUNT, TEST_CONTAINER, null);

            var result = provisioner.provision(provisionResource).join();

            assertThat(result.succeeded()).isTrue();
            var provisionedResource = result.getContent();
            assertThat(provisionedResource.getDataAddress().getStringProperty(AzureBlobStoreSchema.FOLDER_NAME))
                    .isNull();
        }

        @Test
        @DisplayName("stores SAS token in vault as JSON")
        void provision_storesSasTokenAsJson() {
            when(blobStoreApi.createContainerSasToken(eq(TEST_ACCOUNT), eq(TEST_CONTAINER), eq("racwdl"), any(OffsetDateTime.class)))
                    .thenReturn(TEST_SAS_TOKEN);
            when(vault.storeSecret(anyString(), anyString())).thenReturn(Result.success());

            var provisionResource = createProvisionResource(TEST_ACCOUNT, TEST_CONTAINER, TEST_FOLDER);

            provisioner.provision(provisionResource).join();

            // Verify the SAS token is stored as JSON format
            verify(vault).storeSecret(contains("azure-sas-"), contains("\"sas\":"));
        }

        @Test
        @DisplayName("generates unique key name for SAS token")
        void provision_generatesUniqueKeyName() {
            when(blobStoreApi.createContainerSasToken(eq(TEST_ACCOUNT), eq(TEST_CONTAINER), eq("racwdl"), any(OffsetDateTime.class)))
                    .thenReturn(TEST_SAS_TOKEN);
            when(vault.storeSecret(anyString(), anyString())).thenReturn(Result.success());

            var flowId = "test-flow-123";
            var provisionResource = ProvisionResource.Builder.newInstance()
                    .flowId(flowId)
                    .properties(Map.of(
                            ACCOUNT_NAME_PROPERTY, TEST_ACCOUNT,
                            CONTAINER_NAME_PROPERTY, TEST_CONTAINER
                    ))
                    .build();

            var result = provisioner.provision(provisionResource).join();

            assertThat(result.succeeded()).isTrue();
            var keyName = result.getContent().getDataAddress().getKeyName();
            assertThat(keyName).contains("azure-sas-");
            assertThat(keyName).contains(flowId);
            assertThat(keyName).contains(TEST_CONTAINER);
        }

        @Test
        @DisplayName("returns failure when vault store fails")
        void provision_vaultStoreFails_returnsFailure() {
            when(blobStoreApi.createContainerSasToken(eq(TEST_ACCOUNT), eq(TEST_CONTAINER), eq("racwdl"), any(OffsetDateTime.class)))
                    .thenReturn(TEST_SAS_TOKEN);
            when(vault.storeSecret(anyString(), anyString()))
                    .thenReturn(Result.failure("Vault connection error"));

            var provisionResource = createProvisionResource(TEST_ACCOUNT, TEST_CONTAINER, TEST_FOLDER);

            var result = provisioner.provision(provisionResource).join();

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureDetail()).contains("Failed to store SAS token in vault");
        }

        @Test
        @DisplayName("returns failure when SAS token generation throws exception")
        void provision_sasGenerationFails_returnsFailure() {
            when(blobStoreApi.createContainerSasToken(anyString(), anyString(), anyString(), any(OffsetDateTime.class)))
                    .thenThrow(new RuntimeException("Storage account not found"));

            var provisionResource = createProvisionResource(TEST_ACCOUNT, TEST_CONTAINER, TEST_FOLDER);

            var result = provisioner.provision(provisionResource).join();

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureDetail()).contains("Failed to provision Azure Storage resource");
        }

        @Test
        @DisplayName("serialized SAS token expiry matches the expiry used to create the token")
        void provision_serializedExpiryMatchesTokenExpiry() throws Exception {
            when(blobStoreApi.createContainerSasToken(eq(TEST_ACCOUNT), eq(TEST_CONTAINER), eq("racwdl"), any(OffsetDateTime.class)))
                    .thenReturn(TEST_SAS_TOKEN);
            when(vault.storeSecret(anyString(), anyString())).thenReturn(Result.success());

            var provisionResource = createProvisionResource(TEST_ACCOUNT, TEST_CONTAINER, TEST_FOLDER);

            provisioner.provision(provisionResource).join();

            // The expected expiry is exactly what provision() computes from the fixed clock.
            var expectedExpiryMs = OffsetDateTime.now(fixedClock).plusSeconds(3600).toInstant().toEpochMilli();

            verify(vault).storeSecret(anyString(), argThat(json -> {
                try {
                    var node = objectMapper.readTree(json);
                    return node.get("expiration").asLong() == expectedExpiryMs;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        @Test
        @DisplayName("uses configured token validity")
        void provision_usesConfiguredTokenValidity() {
            var customProvisioner = ObjectStorageProvisioner.newInstance()
                    .blobStoreApi(blobStoreApi)
                    .vault(vault)
                    .monitor(monitor)
                    .clock(fixedClock)
                    .objectMapper(objectMapper)
                    .tokenValiditySeconds(7200) // 2 hours
                    .build();

            when(blobStoreApi.createContainerSasToken(anyString(), anyString(), anyString(), any(OffsetDateTime.class)))
                    .thenReturn(TEST_SAS_TOKEN);
            when(vault.storeSecret(anyString(), anyString())).thenReturn(Result.success());

            var provisionResource = createProvisionResource(TEST_ACCOUNT, TEST_CONTAINER, TEST_FOLDER);

            customProvisioner.provision(provisionResource).join();

            // Verify the expiry time is 2 hours from the fixed clock time
            var expectedExpiry = OffsetDateTime.now(fixedClock).plusSeconds(7200);
            verify(blobStoreApi).createContainerSasToken(eq(TEST_ACCOUNT), eq(TEST_CONTAINER), anyString(), eq(expectedExpiry));
        }

        @Test
        @DisplayName("uses configured permissions")
        void provision_usesConfiguredPermissions() {
            var customProvisioner = ObjectStorageProvisioner.newInstance()
                    .blobStoreApi(blobStoreApi)
                    .vault(vault)
                    .monitor(monitor)
                    .clock(fixedClock)
                    .objectMapper(objectMapper)
                    .permissions("rw") // read-write only
                    .build();

            when(blobStoreApi.createContainerSasToken(anyString(), anyString(), anyString(), any(OffsetDateTime.class)))
                    .thenReturn(TEST_SAS_TOKEN);
            when(vault.storeSecret(anyString(), anyString())).thenReturn(Result.success());

            var provisionResource = createProvisionResource(TEST_ACCOUNT, TEST_CONTAINER, TEST_FOLDER);

            customProvisioner.provision(provisionResource).join();

            verify(blobStoreApi).createContainerSasToken(eq(TEST_ACCOUNT), eq(TEST_CONTAINER), eq("rw"), any(OffsetDateTime.class));
        }
    }

    @Nested
    @DisplayName("Builder validation")
    class BuilderValidation {

        @Test
        @DisplayName("throws exception when BlobStoreApi is missing")
        void build_missingBlobStoreApi_throwsException() {
            assertThatThrownBy(() -> ObjectStorageProvisioner.newInstance()
                    .vault(vault)
                    .monitor(monitor)
                    .objectMapper(objectMapper)
                    .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("BlobStoreApi is required");
        }

        @Test
        @DisplayName("throws exception when Vault is missing")
        void build_missingVault_throwsException() {
            assertThatThrownBy(() -> ObjectStorageProvisioner.newInstance()
                    .blobStoreApi(blobStoreApi)
                    .monitor(monitor)
                    .objectMapper(objectMapper)
                    .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Vault is required");
        }

        @Test
        @DisplayName("throws exception when Monitor is missing")
        void build_missingMonitor_throwsException() {
            assertThatThrownBy(() -> ObjectStorageProvisioner.newInstance()
                    .blobStoreApi(blobStoreApi)
                    .vault(vault)
                    .objectMapper(objectMapper)
                    .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Monitor is required");
        }

        @Test
        @DisplayName("throws exception when ObjectMapper is missing")
        void build_missingObjectMapper_throwsException() {
            assertThatThrownBy(() -> ObjectStorageProvisioner.newInstance()
                    .blobStoreApi(blobStoreApi)
                    .vault(vault)
                    .monitor(monitor)
                    .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ObjectMapper is required");
        }

        @Test
        @DisplayName("uses default values when not specified")
        void build_usesDefaults() {
            var prov = ObjectStorageProvisioner.newInstance()
                    .blobStoreApi(blobStoreApi)
                    .vault(vault)
                    .monitor(monitor)
                    .objectMapper(objectMapper)
                    .build();

            assertThat(prov).isNotNull();
        }
    }

    private ProvisionResource createProvisionResource(String accountName, String containerName, String folderName) {
        var properties = new java.util.HashMap<String, Object>();
        properties.put(ACCOUNT_NAME_PROPERTY, accountName);
        properties.put(CONTAINER_NAME_PROPERTY, containerName);
        if (folderName != null) {
            properties.put(FOLDER_NAME_PROPERTY, folderName);
        }

        return ProvisionResource.Builder.newInstance()
                .flowId(UUID.randomUUID().toString())
                .properties(properties)
                .build();
    }
}
