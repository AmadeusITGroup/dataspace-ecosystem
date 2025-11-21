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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Enhanced queue service that combines automatic vault discovery with file-based queues
 */
public class AutomaticDiscoveryQueueService extends FileQueueService {
    
    private static final Logger LOGGER = Logger.getLogger(AutomaticDiscoveryQueueService.class.getName());
    private final VaultService vaultService;
    private final KubernetesCheckerService kubernetesChecker;
    
    public AutomaticDiscoveryQueueService(String sharedDir, VaultService vaultService, 
                                        KubernetesCheckerService kubernetesChecker) {
        super(sharedDir);
        this.vaultService = vaultService;
        this.kubernetesChecker = kubernetesChecker;
    }
    
    /**
     * Performs automatic discovery and populates queues based on vault contents
     */
    public void performAutomaticDiscovery() {
        try {
            List<EdrDiscoveryResult> kafkaEdrs = vaultService.discoverKafkaEdrs();
            
            if (!kafkaEdrs.isEmpty()) {
                LOGGER.fine(format("Discovered %d Kafka EDRs ready for deployment", kafkaEdrs.size()));
                
                // Populate deployment queue with discovered EDRs
                populateDeploymentQueue(kafkaEdrs);
            } else {
                LOGGER.fine("No new Kafka EDRs discovered");
            }
            
            // ALWAYS perform orphaned proxy cleanup, regardless of whether there are active Kafka EDRs
            // This ensures orphaned proxies are cleaned up even when all EDRs are deleted
            performOrphanedProxyCleanup(kafkaEdrs);
            
        } catch (Exception e) {
            LOGGER.severe(format("Error during automatic discovery: %s", e.getMessage()));
        }
    }
    
    /**
     * Populates the deployment queue with ready Kafka EDRs
     */
    private void populateDeploymentQueue(List<EdrDiscoveryResult> kafkaEdrs) {
        try {
            Path queueFile = Paths.get(getSharedDir(), "kafka_edrs_ready.txt");
            
            // Filter EDRs that are ready for deployment
            List<String> queueEntries = kafkaEdrs.stream()
                    .filter(EdrDiscoveryResult::isReadyForDeployment)
                    .map(edr -> format("%s:%s", edr.getEdrKey(), edr.needsTls() ? "true" : "false"))
                    .collect(Collectors.toList());
            
            if (!queueEntries.isEmpty()) {
                Files.write(queueFile, queueEntries);
                LOGGER.info(format("Populated deployment queue with %d EDRs: %s", 
                        queueEntries.size(), 
                        kafkaEdrs.stream().map(EdrDiscoveryResult::getEdrKey).collect(Collectors.toList())));
            }
            
        } catch (IOException e) {
            LOGGER.severe(format("Error populating deployment queue: %s", e.getMessage()));
        }
    }
    
    /**
     * Identifies and queues orphaned proxies for cleanup
     */
    private void performOrphanedProxyCleanup(List<EdrDiscoveryResult> activeKafkaEdrs) {
        try {
            // Get currently deployed proxies
            List<String> deployedProxies = kubernetesChecker.getDeployedProxyEdrKeys();
            LOGGER.info(format("Currently deployed proxies: %d - %s", deployedProxies.size(), deployedProxies));
            
            // Get active EDR keys from vault
            List<String> activeEdrKeys = activeKafkaEdrs.stream()
                    .map(EdrDiscoveryResult::getEdrKey)
                    .collect(Collectors.toList());
            LOGGER.info(format("Active Kafka EDR keys in vault: %d - %s", activeEdrKeys.size(), activeEdrKeys));
            
            // Check for deleted EDR keys (automatic deletion detection)
            List<String> deletedEdrKeys = vaultService.getDeletedEdrKeys();
            LOGGER.info(format("Deleted EDR keys detected: %d - %s", deletedEdrKeys.size(), deletedEdrKeys));
            
            // Find orphaned proxies (deployed but not in vault or not Kafka EDRs)
            List<String> orphanedProxies = deployedProxies.stream()
                    .filter(edrKey -> !activeEdrKeys.contains(edrKey))
                    .collect(Collectors.toList());
            LOGGER.info(format("Orphaned proxies (deployed but not active): %d - %s", orphanedProxies.size(), orphanedProxies));
            
            // Add deleted EDR keys to cleanup list
            List<String> allCleanupKeys = new ArrayList<>();
            allCleanupKeys.addAll(orphanedProxies);
            allCleanupKeys.addAll(deletedEdrKeys);
            
            // Remove duplicates
            allCleanupKeys = allCleanupKeys.stream().distinct().collect(Collectors.toList());
            
            if (!allCleanupKeys.isEmpty()) {
                LOGGER.info(format("Total proxies queued for cleanup: %d (orphaned: %d, deleted EDRs: %d): %s", 
                        allCleanupKeys.size(), orphanedProxies.size(), deletedEdrKeys.size(), allCleanupKeys));
                
                // Write to cleanup queue
                Path cleanupFile = Paths.get(getSharedDir(), "kafka_edrs_cleanup.txt");
                Files.write(cleanupFile, allCleanupKeys);
                
                LOGGER.info(format("Wrote %d proxies to cleanup queue file: %s", allCleanupKeys.size(), cleanupFile));
            } else {
                LOGGER.fine("No orphaned proxies or deleted EDRs found for cleanup");
            }
            
        } catch (Exception e) {
            LOGGER.severe(format("Error during orphaned proxy cleanup: %s", e.getMessage()));
            e.printStackTrace();
        }
    }
    
    /**
     * Initializes the EDR key tracking for deletion detection
     * Should be called once at service startup
     */
    public void initializeEdrTracking() {
        vaultService.refreshKnownEdrKeys();
    }
    
    @Override
    public List<EdrQueueEntry> processDeploymentQueue() {
        // First, perform automatic discovery if enabled
        performAutomaticDiscovery();
        
        // Then process the queue as normal
        return super.processDeploymentQueue();
    }
}