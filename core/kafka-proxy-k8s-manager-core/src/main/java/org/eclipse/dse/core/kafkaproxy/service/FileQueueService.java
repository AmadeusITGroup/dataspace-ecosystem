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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * Service for processing deployment and cleanup queues from shared directory files
 */
public class FileQueueService {
    
    private static final Logger LOGGER = Logger.getLogger(FileQueueService.class.getName());
    
    private static final String DEPLOYMENT_QUEUE_FILE = "kafka_edrs_ready.txt";
    private static final String CLEANUP_QUEUE_FILE = "kafka_edrs_cleanup.txt";
    
    private final String sharedDir;
    
    public FileQueueService(String sharedDir) {
        this.sharedDir = sharedDir;
    }
    
    /**
     * Reads and processes the deployment queue file
     */
    public List<EdrQueueEntry> processDeploymentQueue() {
        Path queueFile = Paths.get(sharedDir, DEPLOYMENT_QUEUE_FILE);
        
        if (!Files.exists(queueFile)) {
            LOGGER.fine(format("Deployment queue file %s does not exist", queueFile));
            return List.of();
        }
        
        try {
            List<String> lines = Files.readAllLines(queueFile);
            List<EdrQueueEntry> entries = parseQueueEntries(lines);
            
            if (entries.isEmpty()) {
                LOGGER.fine("No EDR keys in deployment queue");
                return List.of();
            }
            
            LOGGER.info(format("Processing %d EDR entries from deployment queue: %s", 
                    entries.size(), entries.stream().map(EdrQueueEntry::getEdrKey).toList()));
            
            // Clear the queue file after reading
            clearQueueFile(queueFile);
            
            return entries;
            
        } catch (IOException e) {
            LOGGER.severe(format("Error reading deployment queue file: %s", e.getMessage()));
            return List.of();
        }
    }
    
    /**
     * Reads and processes the cleanup queue file
     */
    public List<EdrQueueEntry> processCleanupQueue() {
        Path queueFile = Paths.get(sharedDir, CLEANUP_QUEUE_FILE);
        
        if (!Files.exists(queueFile)) {
            LOGGER.fine(format("Cleanup queue file %s does not exist", queueFile));
            return List.of();
        }
        
        try {
            List<String> lines = Files.readAllLines(queueFile);
            List<EdrQueueEntry> entries = parseQueueEntries(lines);
            
            if (entries.isEmpty()) {
                LOGGER.fine("No EDR keys in cleanup queue");
                return List.of();
            }
            
            LOGGER.info(format("Processing %d EDR entries from cleanup queue: %s", 
                    entries.size(), entries.stream().map(EdrQueueEntry::getEdrKey).toList()));
            
            // Clear the queue file after reading
            clearQueueFile(queueFile);
            
            return entries;
            
        } catch (IOException e) {
            LOGGER.severe(format("Error reading cleanup queue file: %s", e.getMessage()));
            return List.of();
        }
    }
    
    /**
     * Updates the deployment status file for an EDR key
     */
    public void updateDeploymentStatus(String edrKey, boolean success) {
        try {
            Path statusFile = Paths.get(sharedDir, format("kafka_edr_%s_deployment_status.txt", edrKey));
            String status = success ? "success" : "failed";
            
            Files.writeString(statusFile, status);
            LOGGER.info(format("Updated deployment status for EDR %s: %s", edrKey, status));
            
        } catch (IOException e) {
            LOGGER.severe(format("Error updating deployment status for EDR %s: %s", edrKey, e.getMessage()));
        }
    }
    
    /**
     * Updates the cleanup status file for an EDR key
     */
    public void updateCleanupStatus(String edrKey, boolean success) {
        try {
            Path statusFile = Paths.get(sharedDir, format("kafka_edr_%s_cleanup_status.txt", edrKey));
            String status = success ? "success" : "failed";
            
            Files.writeString(statusFile, status);
            LOGGER.info(format("Updated cleanup status for EDR %s: %s", edrKey, status));
            
        } catch (IOException e) {
            LOGGER.severe(format("Error updating cleanup status for EDR %s: %s", edrKey, e.getMessage()));
        }
    }
    
    /**
     * Checks if the shared directory exists and is accessible
     */
    public boolean isSharedDirectoryAccessible() {
        try {
            Path sharedPath = Paths.get(sharedDir);
            
            if (!Files.exists(sharedPath)) {
                LOGGER.warning(format("Shared directory does not exist: %s", sharedDir));
                return false;
            }
            
            if (!Files.isDirectory(sharedPath)) {
                LOGGER.warning(format("Shared path is not a directory: %s", sharedDir));
                return false;
            }
            
            if (!Files.isReadable(sharedPath) || !Files.isWritable(sharedPath)) {
                LOGGER.warning(format("Shared directory is not readable/writable: %s", sharedDir));
                return false;
            }
            
            LOGGER.fine(format("Shared directory is accessible: %s", sharedDir));
            return true;
            
        } catch (Exception e) {
            LOGGER.severe(format("Error checking shared directory accessibility: %s", e.getMessage()));
            return false;
        }
    }
    
    /**
     * Gets the shared directory path
     */
    protected String getSharedDir() {
        return sharedDir;
    }
    
    private List<EdrQueueEntry> parseQueueEntries(List<String> lines) {
        List<EdrQueueEntry> entries = new ArrayList<>();
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            
            // Parse EDR entry (format: edr_key:needs_tls or just edr_key for backward compatibility)
            if (trimmed.contains(":")) {
                String[] parts = trimmed.split(":", 2);
                String edrKey = parts[0].trim();
                boolean needsTls = "true".equalsIgnoreCase(parts[1].trim());
                entries.add(new EdrQueueEntry(edrKey, needsTls));
            } else {
                // Backward compatibility: assume no TLS if not specified
                entries.add(new EdrQueueEntry(trimmed, false));
            }
        }
        
        return entries;
    }
    
    private void clearQueueFile(Path queueFile) {
        try {
            Files.writeString(queueFile, "");
            LOGGER.info(format("Queue file cleared: %s", queueFile));
        } catch (IOException e) {
            LOGGER.severe(format("Error clearing queue file %s: %s", queueFile, e.getMessage()));
        }
    }
    
    /**
     * Represents an entry in the EDR queue
     */
    public static class EdrQueueEntry {
        private final String edrKey;
        private final boolean needsTls;
        
        public EdrQueueEntry(String edrKey, boolean needsTls) {
            this.edrKey = edrKey;
            this.needsTls = needsTls;
        }
        
        public String getEdrKey() {
            return edrKey;
        }
        
        public boolean needsTls() {
            return needsTls;
        }
        
        @Override
        public String toString() {
            return format("EdrQueueEntry{edrKey='%s', needsTls=%s}", edrKey, needsTls);
        }
    }
}