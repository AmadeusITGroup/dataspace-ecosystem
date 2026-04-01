/*
 *  Copyright (c) 2024 Eclipse Dataspace Connector Project
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Eclipse Dataspace Connector Project - initial implementation
 */

package org.eclipse.dse.core.kafkaproxy.service;

import org.eclipse.dse.core.kafkaproxy.service.FileQueueService.EdrQueueEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileQueueServiceTest {
    
    @TempDir
    Path tempDir;
    
    private FileQueueService fileQueueService;
    
    @BeforeEach
    void setUp() {
        fileQueueService = new FileQueueService(tempDir.toString());
    }
    
    @Test
    void shouldProcessDeploymentQueue_whenFileExists() throws Exception {
        // Arrange
        Path queueFile = tempDir.resolve("kafka_edrs_ready.txt");
        Files.writeString(queueFile, "edr1:true\nedr2:false\nedr3\n");
        
        // Act
        List<EdrQueueEntry> entries = fileQueueService.processDeploymentQueue();
        
        // Assert
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).getEdrKey()).isEqualTo("edr1");
        assertThat(entries.get(0).needsTls()).isTrue();
        assertThat(entries.get(1).getEdrKey()).isEqualTo("edr2");
        assertThat(entries.get(1).needsTls()).isFalse();
        assertThat(entries.get(2).getEdrKey()).isEqualTo("edr3");
        assertThat(entries.get(2).needsTls()).isFalse(); // backward compatibility
    }
    
    @Test
    void shouldReturnEmptyList_whenFileDoesNotExist() {
        // Act
        List<EdrQueueEntry> entries = fileQueueService.processDeploymentQueue();
        
        // Assert
        assertThat(entries).isEmpty();
    }
    
    @Test
    void shouldUpdateDeploymentStatus() throws Exception {
        // Act
        fileQueueService.updateDeploymentStatus("test-edr", true);
        
        // Assert
        Path statusFile = tempDir.resolve("kafka_edr_test-edr_deployment_status.txt");
        assertThat(Files.exists(statusFile)).isTrue();
        assertThat(Files.readString(statusFile)).isEqualTo("success");
    }
    
    @Test
    void shouldCheckSharedDirectoryAccessibility() {
        // Act & Assert
        assertThat(fileQueueService.isSharedDirectoryAccessible()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Path-traversal / path-manipulation security tests (CWE-22 / CWE-73)
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "updateDeploymentStatus rejects malicious key: [{0}]")
    @ValueSource(strings = {
            "../etc/passwd",
            "../../secret",
            "edr/../../etc/shadow",
            "edr\\..\\..",
            "edr%2F..%2F..",
            "edr key",         // spaces not allowed
            "edr.key",         // dots not allowed
            ""                 // empty string
    })
    void shouldRejectPathTraversalInDeploymentStatus(String maliciousKey) {
        // Invalid keys are rejected and logged internally — no exception propagates,
        // and no file must be written anywhere on the filesystem.
        fileQueueService.updateDeploymentStatus(maliciousKey, true);

        assertThat(tempDir.toFile().listFiles()).isEmpty();
    }

    @ParameterizedTest(name = "updateCleanupStatus rejects malicious key: [{0}]")
    @ValueSource(strings = {
            "../etc/passwd",
            "../../secret",
            "edr/../../etc/shadow",
            "edr\\..\\..",
            "edr%2F..%2F..",
            "edr key",
            "edr.key",
            ""
    })
    void shouldRejectPathTraversalInCleanupStatus(String maliciousKey) {
        // Invalid keys are rejected and logged internally — no exception propagates,
        // and no file must be written anywhere on the filesystem.
        fileQueueService.updateCleanupStatus(maliciousKey, true);

        assertThat(tempDir.toFile().listFiles()).isEmpty();
    }

    @Test
    void shouldAcceptValidAlphanumericEdrKey() throws Exception {
        // Valid keys should work without throwing
        fileQueueService.updateDeploymentStatus("edr-123_ABC", true);
        fileQueueService.updateCleanupStatus("edr-123_ABC", false);

        assertThat(Files.readString(tempDir.resolve("kafka_edr_edr-123_ABC_deployment_status.txt")))
                .isEqualTo("success");
        assertThat(Files.readString(tempDir.resolve("kafka_edr_edr-123_ABC_cleanup_status.txt")))
                .isEqualTo("failed");
    }
}