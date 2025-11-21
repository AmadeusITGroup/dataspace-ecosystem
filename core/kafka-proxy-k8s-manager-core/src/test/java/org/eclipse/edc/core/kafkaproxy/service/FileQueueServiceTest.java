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
}