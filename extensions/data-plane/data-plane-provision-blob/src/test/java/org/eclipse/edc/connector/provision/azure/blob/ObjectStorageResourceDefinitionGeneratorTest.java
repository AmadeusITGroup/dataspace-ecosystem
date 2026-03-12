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
import org.eclipse.edc.connector.dataplane.spi.DataFlow;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.provision.azure.blob.ObjectStorageResourceDefinitionGenerator.ACCOUNT_NAME_PROPERTY;
import static org.eclipse.edc.connector.provision.azure.blob.ObjectStorageResourceDefinitionGenerator.CONTAINER_NAME_PROPERTY;
import static org.eclipse.edc.connector.provision.azure.blob.ObjectStorageResourceDefinitionGenerator.FOLDER_NAME_PROPERTY;

class ObjectStorageResourceDefinitionGeneratorTest {

    private static final String TEST_FLOW_ID = "flow-id";
    private static final String TEST_ACCOUNT = "test-account";
    private static final String TEST_CONTAINER = "test-container";
    private static final String TEST_FOLDER = "test-folder";

    private final ObjectStorageResourceDefinitionGenerator generator = new ObjectStorageResourceDefinitionGenerator();

    @Test
    @DisplayName("supportedType returns AzureStorage type")
    void supportedType_returnsAzureStorageType() {
        assertThat(generator.supportedType()).isEqualTo(AzureBlobStoreSchema.TYPE);
    }

    @Nested
    @DisplayName("generate")
    class Generate {

        @Test
        @DisplayName("creates provision resource from destination")
        void generate_validDestination_returnsProvisionResource() {
            var destination = DataAddress.Builder.newInstance()
                    .type(AzureBlobStoreSchema.TYPE)
                    .property(AzureBlobStoreSchema.ACCOUNT_NAME, TEST_ACCOUNT)
                    .property(AzureBlobStoreSchema.CONTAINER_NAME, TEST_CONTAINER)
                    .property(AzureBlobStoreSchema.FOLDER_NAME, TEST_FOLDER)
                    .build();

            var resource = generator.generate(createDataFlow(destination));

            assertThat(resource).isNotNull();
            assertThat(resource.getFlowId()).isEqualTo(TEST_FLOW_ID);
            assertThat(resource.getDataAddress().getStringProperty(AzureBlobStoreSchema.ACCOUNT_NAME)).isEqualTo(TEST_ACCOUNT);
            assertThat(resource.getDataAddress().getStringProperty(AzureBlobStoreSchema.CONTAINER_NAME)).isEqualTo(TEST_CONTAINER);
            assertThat(resource.getDataAddress().getStringProperty(AzureBlobStoreSchema.FOLDER_NAME)).isEqualTo(TEST_FOLDER);
            assertThat(resource.getProperty(ACCOUNT_NAME_PROPERTY)).isEqualTo(TEST_ACCOUNT);
            assertThat(resource.getProperty(CONTAINER_NAME_PROPERTY)).isEqualTo(TEST_CONTAINER);
            assertThat(resource.getProperty(FOLDER_NAME_PROPERTY)).isEqualTo(TEST_FOLDER);
        }

        @Test
        @DisplayName("omits folder property when blank")
        void generate_blankFolder_omitsFolderProperty() {
            var destination = DataAddress.Builder.newInstance()
                    .type(AzureBlobStoreSchema.TYPE)
                    .property(AzureBlobStoreSchema.ACCOUNT_NAME, TEST_ACCOUNT)
                    .property(AzureBlobStoreSchema.CONTAINER_NAME, TEST_CONTAINER)
                    .property(AzureBlobStoreSchema.FOLDER_NAME, "   ")
                    .build();

            var resource = generator.generate(createDataFlow(destination));

            assertThat(resource).isNotNull();
            assertThat(resource.getProperty(FOLDER_NAME_PROPERTY)).isNull();
        }

        @Test
        @DisplayName("returns null when destination is missing")
        void generate_destinationIsNull_returnsNull() {
            var dataFlow = DataFlow.Builder.newInstance()
                    .id(TEST_FLOW_ID)
                    .destination(null)
                    .build();

            assertThat(generator.generate(dataFlow)).isNull();
        }

        @Test
        @DisplayName("returns null when account name is missing")
        void generate_missingAccountName_returnsNull() {
            var destination = DataAddress.Builder.newInstance()
                    .type(AzureBlobStoreSchema.TYPE)
                    .property(AzureBlobStoreSchema.CONTAINER_NAME, TEST_CONTAINER)
                    .build();

            assertThat(generator.generate(createDataFlow(destination))).isNull();
        }

        @Test
        @DisplayName("returns null when container name is missing")
        void generate_missingContainerName_returnsNull() {
            var destination = DataAddress.Builder.newInstance()
                    .type(AzureBlobStoreSchema.TYPE)
                    .property(AzureBlobStoreSchema.ACCOUNT_NAME, TEST_ACCOUNT)
                    .build();

            assertThat(generator.generate(createDataFlow(destination))).isNull();
        }
    }

    private DataFlow createDataFlow(DataAddress destination) {
        return DataFlow.Builder.newInstance()
                .id(TEST_FLOW_ID)
                .destination(destination)
                .build();
    }
}
