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

package org.eclipse.dse.core.kafkaproxy;

import org.eclipse.dse.core.kafkaproxy.model.DeploymentStatus;
import org.eclipse.dse.core.kafkaproxy.model.EdrProperties;
import org.eclipse.dse.core.kafkaproxy.service.AutomaticDiscoveryQueueService;
import org.eclipse.dse.core.kafkaproxy.service.FileQueueService;
import org.eclipse.dse.core.kafkaproxy.service.KubernetesCheckerService;
import org.eclipse.dse.core.kafkaproxy.service.KubernetesDeployerService;
import org.eclipse.dse.core.kafkaproxy.service.VaultService;
import org.eclipse.edc.spi.monitor.Monitor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static java.lang.String.format;

/**
 * Orchestrates the deployment and management of Kafka proxy instances
 */
public class KafkaProxyOrchestrator {
    
    private final VaultService vaultService;
    private final KubernetesDeployerService deployerService;
    private final KubernetesCheckerService checkerService;
    private final AutomaticDiscoveryQueueService fileQueueService;
    private final Monitor monitor;
    private final LockManager lockManager;
    private final boolean autoDiscoveryEnabled;
    
    public KafkaProxyOrchestrator(VaultService vaultService,
                                 KubernetesDeployerService deployerService,
                                 KubernetesCheckerService checkerService,
                                 AutomaticDiscoveryQueueService fileQueueService,
                                 Monitor monitor,
                                 boolean autoDiscoveryEnabled) {
        this.vaultService = vaultService;
        this.deployerService = deployerService;
        this.checkerService = checkerService;
        this.fileQueueService = fileQueueService;
        this.monitor = monitor;
        this.autoDiscoveryEnabled = autoDiscoveryEnabled;
        this.lockManager = new LockManager("/tmp/kubectl-deployer.lock");
    }
    
    /**
     * Main processing method that handles both deployment and cleanup queues
     */
    public void processQueues() {
        if (!lockManager.acquireLock()) {
            monitor.debug("Could not acquire lock, skipping this cycle");
            return;
        }
        
        try {
            monitor.debug("Processing deployment and cleanup queues");
            
            // Check Kubernetes connection first
            if (!checkerService.checkKubernetesConnection()) {
                monitor.severe("Kubernetes connection failed, skipping queue processing");
                return;
            }
            
            // Check shared directory accessibility
            if (!fileQueueService.isSharedDirectoryAccessible()) {
                monitor.severe("Shared directory not accessible, skipping queue processing");
                return;
            }
            
            // Process deployment queue
            processDeploymentQueue();
            
            // Process cleanup queue
            processCleanupQueue();
            
            // Log health summary
            logHealthSummary();
            
        } catch (Exception e) {
            monitor.severe(format("Error during queue processing: %s", e.getMessage()));
        } finally {
            lockManager.releaseLock();
        }
    }
    
    private void processDeploymentQueue() {
        List<FileQueueService.EdrQueueEntry> deploymentEntries = fileQueueService.processDeploymentQueue();
        
        if (deploymentEntries.isEmpty()) {
            monitor.debug("No entries in deployment queue");
            return;
        }
        
        monitor.info(format("Found %d deployment entries in queue", deploymentEntries.size()));
        
        // For single-proxy pattern: only deploy the NEWEST EDR, ignore all older ones
        if (deploymentEntries.size() > 1) {
            FileQueueService.EdrQueueEntry newestEntry = selectNewestEdr(deploymentEntries);
            monitor.info(format("Single-proxy mode: Deploying only the newest EDR: %s (ignoring %d older EDRs)", 
                    newestEntry.getEdrKey(), deploymentEntries.size() - 1));
            
            // Mark all other EDRs as "processed" (skip them) to prevent requeuing
            for (FileQueueService.EdrQueueEntry entry : deploymentEntries) {
                if (!entry.getEdrKey().equals(newestEntry.getEdrKey())) {
                    monitor.info(format("Skipping older EDR: %s", entry.getEdrKey()));
                    fileQueueService.updateDeploymentStatus(entry.getEdrKey(), true);
                }
            }
            
            // Deploy only the newest one
            processDeploymentEntry(newestEntry);
        } else {
            // Only one entry, process it normally
            processDeploymentEntry(deploymentEntries.get(0));
        }
    }
    
    /**
     * Selects the newest EDR from a list based on creation timestamp in vault.
     * Falls back to EDR key string comparison if timestamps are unavailable.
     */
    private FileQueueService.EdrQueueEntry selectNewestEdr(List<FileQueueService.EdrQueueEntry> entries) {
        FileQueueService.EdrQueueEntry newest = entries.get(0);
        long newestTimestamp = getEdrCreationTimestamp(newest.getEdrKey());
        
        for (int i = 1; i < entries.size(); i++) {
            FileQueueService.EdrQueueEntry candidate = entries.get(i);
            long candidateTimestamp = getEdrCreationTimestamp(candidate.getEdrKey());
            
            if (candidateTimestamp > newestTimestamp) {
                newest = candidate;
                newestTimestamp = candidateTimestamp;
            } else if (candidateTimestamp == newestTimestamp && candidateTimestamp == 0) {
                // If timestamps unavailable, use lexicographic comparison (EDR keys include UUIDs)
                if (candidate.getEdrKey().compareTo(newest.getEdrKey()) > 0) {
                    newest = candidate;
                }
            }
        }
        
        return newest;
    }
    
    /**
     * Gets the creation timestamp of an EDR from vault metadata.
     * Returns 0 if timestamp cannot be retrieved.
     */
    private long getEdrCreationTimestamp(String edrKey) {
        try {
            return vaultService.getEdrCreationTimestamp(edrKey);
        } catch (Exception e) {
            monitor.warning(format("Failed to get creation timestamp for EDR %s: %s", edrKey, e.getMessage()));
            return 0;
        }
    }
    
    private void processDeploymentEntry(FileQueueService.EdrQueueEntry entry) {
        String edrKey = entry.getEdrKey();
        
        try {
            monitor.info(format("Processing deployment for EDR: %s (TLS: %s)", edrKey, entry.needsTls()));
            
            // Check if already deployed
            if (deployerService.isProxyDeployed(edrKey)) {
                monitor.info(format("Proxy already deployed for EDR: %s", edrKey));
                fileQueueService.updateDeploymentStatus(edrKey, true);
                return;
            }
            
            // Get EDR properties from Vault
            EdrProperties properties = vaultService.getEdrProperties(edrKey);
            
            if (properties.getBootstrapServers().isEmpty()) {
                monitor.warning(format("No bootstrap servers found for EDR: %s, skipping deployment", edrKey));
                fileQueueService.updateDeploymentStatus(edrKey, false);
                return;
            }
            
            // Deploy the proxy
            DeploymentStatus status = deployerService.deployProxy(edrKey, properties);
            boolean success = status.getStatus() == DeploymentStatus.Status.DEPLOYED;
            
            // Update status file
            fileQueueService.updateDeploymentStatus(edrKey, success);
            
            if (success) {
                monitor.info(format("Successfully deployed proxy for EDR: %s", edrKey));
            } else {
                monitor.warning(format("Failed to deploy proxy for EDR: %s - %s", edrKey, status.getMessage()));
            }
            
        } catch (Exception e) {
            monitor.severe(format("Error processing deployment for EDR %s: %s", edrKey, e.getMessage()));
            fileQueueService.updateDeploymentStatus(edrKey, false);
        }
    }
    
    private void processCleanupQueue() {
        List<FileQueueService.EdrQueueEntry> cleanupEntries = fileQueueService.processCleanupQueue();
        
        if (cleanupEntries.isEmpty()) {
            monitor.debug("No entries in cleanup queue");
            return;
        }
        
        monitor.info(format("Processing %d cleanup entries", cleanupEntries.size()));
        
        for (FileQueueService.EdrQueueEntry entry : cleanupEntries) {
            processCleanupEntry(entry);
        }
    }
    
    private void processCleanupEntry(FileQueueService.EdrQueueEntry entry) {
        String edrKey = entry.getEdrKey();
        
        try {
            monitor.info(format("Processing cleanup for EDR: %s", edrKey));
            
            // Delete the proxy
            DeploymentStatus status = deployerService.deleteProxy(edrKey);
            boolean success = status.getStatus() == DeploymentStatus.Status.DELETED;
            
            // Update status file
            fileQueueService.updateCleanupStatus(edrKey, success);
            
            if (success) {
                monitor.info(format("Successfully cleaned up proxy for EDR: %s", edrKey));
            } else {
                monitor.warning(format("Failed to cleanup proxy for EDR: %s - %s", edrKey, status.getMessage()));
            }
            
        } catch (Exception e) {
            monitor.severe(format("Error processing cleanup for EDR %s: %s", edrKey, e.getMessage()));
            fileQueueService.updateCleanupStatus(edrKey, false);
        }
    }
    
    private void logHealthSummary() {
        try {
            int totalProxies = checkerService.getManagedProxyCount();
            List<String> failedDeployments = checkerService.getFailedDeployments();
            
            if (totalProxies > 0) {
                monitor.info(format("Health summary: %d managed proxies, %d failed deployments", 
                        totalProxies, failedDeployments.size()));
                        
                if (!failedDeployments.isEmpty()) {
                    monitor.warning(format("Failed deployments: %s", failedDeployments));
                }
            } else {
                monitor.debug("No managed proxies found");
            }
            
        } catch (Exception e) {
            monitor.warning(format("Error getting health summary: %s", e.getMessage()));
        }
    }
    
    /**
     * Simple file-based lock manager to prevent concurrent execution
     */
    private static class LockManager {
        private final Path lockFile;
        
        LockManager(String lockFilePath) {
            this.lockFile = Paths.get(lockFilePath);
        }
        
        public boolean acquireLock() {
            try {
                if (java.nio.file.Files.exists(lockFile)) {
                    // Check if lock is stale (older than 5 minutes)
                    long lastModified = java.nio.file.Files.getLastModifiedTime(lockFile).toMillis();
                    long now = System.currentTimeMillis();
                    if (now - lastModified > 300_000) { // 5 minutes
                        java.nio.file.Files.delete(lockFile);
                    } else {
                        return false; // Lock is still active
                    }
                }
                
                // Create lock file
                java.nio.file.Files.createFile(lockFile);
                return true;
                
            } catch (Exception e) {
                return false;
            }
        }
        
        public void releaseLock() {
            try {
                java.nio.file.Files.deleteIfExists(lockFile);
            } catch (Exception e) {
                // Ignore - lock will be cleaned up eventually
            }
        }
    }
}