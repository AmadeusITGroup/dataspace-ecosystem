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

import org.eclipse.dse.core.kafkaproxy.model.EdrDiscoveryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomaticDiscoveryQueueServiceTest {

    @Mock
    private VaultService vaultService;
    
    @Mock 
    private KubernetesCheckerService kubernetesChecker;
    
    private Path tempDir;
    private AutomaticDiscoveryQueueService discoveryQueueService;
    
    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("discovery-test");
        discoveryQueueService = new AutomaticDiscoveryQueueService(
            tempDir.toString(), 
            vaultService, 
            kubernetesChecker
        );
    }
    
    @Test
    void testAutomaticDiscoveryPopulatesQueue() {
        // Arrange
        EdrDiscoveryResult edr1 = new EdrDiscoveryResult("edr--test1", true, true, true, null);
        EdrDiscoveryResult edr2 = new EdrDiscoveryResult("edr--test2", true, true, false, null);
        List<EdrDiscoveryResult> discoveredEdrs = List.of(edr1, edr2);
        
        when(vaultService.discoverKafkaEdrs()).thenReturn(discoveredEdrs);
        lenient().when(vaultService.getCurrentEdrKeys()).thenReturn(List.of("edr--test1", "edr--test2"));
        lenient().when(kubernetesChecker.getDeployedProxyEdrKeys()).thenReturn(List.of());
        
        // Act
        discoveryQueueService.performAutomaticDiscovery();
        
        // Assert
        verify(vaultService).discoverKafkaEdrs();
        
        // Check if queue file was created with correct content
        Path queueFile = tempDir.resolve("kafka_edrs_ready.txt");
        assertTrue(Files.exists(queueFile));
        
        try {
            List<String> lines = Files.readAllLines(queueFile);
            assertEquals(2, lines.size());
            assertTrue(lines.contains("edr--test1:true"));
            assertTrue(lines.contains("edr--test2:false"));
        } catch (IOException e) {
            fail("Failed to read queue file: " + e.getMessage());
        }
    }
    
    @Test
    void testOrphanedProxyCleanup() {
        // Arrange
        List<String> currentEdrs = List.of("edr--active1", "edr--active2");
        List<String> deployedEdrs = List.of("edr--active1", "edr--orphaned1", "edr--orphaned2");
        
        // Provide non-empty kafkaEdrs so cleanup logic gets called
        var kafkaProps = new VaultDiscoveryService.KafkaProperties(
                "localhost:9092", "test-topic", "SASL_SSL", "PLAIN", "jaas-config", true, true
        );
        var edrResult = new EdrDiscoveryResult("edr--active1", true, true, false, kafkaProps);
        
        when(vaultService.discoverKafkaEdrs()).thenReturn(List.of(edrResult));
        lenient().when(vaultService.getCurrentEdrKeys()).thenReturn(currentEdrs);
        lenient().when(kubernetesChecker.getDeployedProxyEdrKeys()).thenReturn(deployedEdrs);
        
        // Act
        discoveryQueueService.performAutomaticDiscovery();
        
        // Assert
        Path cleanupFile = tempDir.resolve("kafka_edrs_cleanup.txt");
        assertTrue(Files.exists(cleanupFile));
        
        try {
            List<String> cleanupLines = Files.readAllLines(cleanupFile);
            assertEquals(2, cleanupLines.size());
            assertTrue(cleanupLines.contains("edr--orphaned1"));
            assertTrue(cleanupLines.contains("edr--orphaned2"));
        } catch (IOException e) {
            fail("Failed to read cleanup file: " + e.getMessage());
        }
    }
    

    
    @Test
    void testProcessDeploymentQueueWithDiscovery() {
        // Arrange
        EdrDiscoveryResult edr = new EdrDiscoveryResult("edr--test", true, true, false, null);
        when(vaultService.discoverKafkaEdrs()).thenReturn(List.of(edr));
        lenient().when(vaultService.getCurrentEdrKeys()).thenReturn(List.of("edr--test"));
        lenient().when(kubernetesChecker.getDeployedProxyEdrKeys()).thenReturn(List.of());
        
        // Act
        var queueEntries = discoveryQueueService.processDeploymentQueue();
        
        // Assert
        verify(vaultService).discoverKafkaEdrs();
        assertNotNull(queueEntries);
        // The queue should be populated and then processed
    }
}