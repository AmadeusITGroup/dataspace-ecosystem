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

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;

import java.util.List;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * Service for checking the health and status of Kafka proxy deployments
 */
public class KubernetesCheckerService {
    
    private static final Logger LOGGER = Logger.getLogger(KubernetesCheckerService.class.getName());
    
    private final KubernetesClient kubernetesClient;
    private final String proxyNamespace;
    private final String participantId;
    
    public KubernetesCheckerService(KubernetesClient kubernetesClient, String proxyNamespace, String participantId) {
        this.kubernetesClient = kubernetesClient;
        this.proxyNamespace = proxyNamespace;
        this.participantId = participantId;
    }
    
    /**
     * Checks if the Kubernetes connection is working
     */
    public boolean checkKubernetesConnection() {
        try {
            // Try to list deployments in the target namespace to test connectivity
            kubernetesClient.apps().deployments().inNamespace(proxyNamespace).list();
            LOGGER.info("Kubernetes connection is healthy");
            return true;
        } catch (KubernetesClientException e) {
            LOGGER.severe(format("Kubernetes connection failed: %s", e.getMessage()));
            return false;
        }
    }
    
    /**
     * Checks the health of a specific proxy deployment
     */
    public ProxyHealth checkProxyHealth(String edrKey) {
        try {
            String proxyName = generateProxyName(edrKey);
            Deployment deployment = kubernetesClient.apps().deployments()
                    .inNamespace(proxyNamespace)
                    .withName(proxyName)
                    .get();
            
            if (deployment == null) {
                return new ProxyHealth(edrKey, false, "Deployment not found", 0, 0);
            }
            
            Integer replicas = deployment.getSpec().getReplicas();
            Integer readyReplicas = deployment.getStatus().getReadyReplicas();
            Integer availableReplicas = deployment.getStatus().getAvailableReplicas();
            
            boolean isHealthy = readyReplicas != null && readyReplicas.equals(replicas) &&
                              availableReplicas != null && availableReplicas.equals(replicas);
            
            String status;
            if (isHealthy) {
                status = "Healthy - All replicas ready";
            } else {
                status = format("Unhealthy - Ready: %d/%d, Available: %d/%d", 
                    readyReplicas != null ? readyReplicas : 0, replicas != null ? replicas : 0,
                    availableReplicas != null ? availableReplicas : 0, replicas != null ? replicas : 0);
            }
            
            return new ProxyHealth(edrKey, isHealthy, status, 
                    readyReplicas != null ? readyReplicas : 0,
                    replicas != null ? replicas : 0);
            
        } catch (Exception e) {
            String errorMessage = format("Error checking proxy health for EDR %s: %s", edrKey, e.getMessage());
            LOGGER.warning(errorMessage);
            return new ProxyHealth(edrKey, false, errorMessage, 0, 0);
        }
    }
    
    /**
     * Gets all managed proxy deployments
     */
    public List<ProxyHealth> checkAllProxies() {
        try {
            return kubernetesClient.apps().deployments()
                    .inNamespace(proxyNamespace)
                    .withLabel("managed-by", "edr-kubectl-deployer")
                    .list()
                    .getItems()
                    .stream()
                    .map(this::convertToProxyHealth)
                    .toList();
                    
        } catch (Exception e) {
            LOGGER.severe(format("Error checking all proxies: %s", e.getMessage()));
            return List.of();
        }
    }
    
    /**
     * Counts the total number of managed proxy deployments
     */
    public int getManagedProxyCount() {
        try {
            String safeParticipantId = generateSafeParticipantId(participantId);
            return kubernetesClient.apps().deployments()
                    .inNamespace(proxyNamespace)
                    .withLabel("managed-by", "edr-kubectl-deployer")
                    .withLabel("owner-participant", safeParticipantId)
                    .list()
                    .getItems()
                    .size();
        } catch (Exception e) {
            LOGGER.warning(format("Error counting managed proxies: %s", e.getMessage()));
            return 0;
        }
    }
    
    /**
     * Checks if any deployments are in a failed state
     */
    public List<String> getFailedDeployments() {
        try {
            String safeParticipantId = generateSafeParticipantId(participantId);
            return kubernetesClient.apps().deployments()
                    .inNamespace(proxyNamespace)
                    .withLabel("managed-by", "edr-kubectl-deployer")
                    .withLabel("owner-participant", safeParticipantId)
                    .list()
                    .getItems()
                    .stream()
                    .filter(deployment -> {
                        Integer replicas = deployment.getSpec().getReplicas();
                        Integer readyReplicas = deployment.getStatus().getReadyReplicas();
                        return readyReplicas == null || !readyReplicas.equals(replicas);
                    })
                    .map(deployment -> deployment.getMetadata().getName())
                    .toList();
                    
        } catch (Exception e) {
            LOGGER.warning(format("Error getting failed deployments: %s", e.getMessage()));
            return List.of();
        }
    }
    
    /**
     * Gets list of deployed proxy EDR keys for cleanup purposes
     */
    public List<String> getDeployedProxyEdrKeys() {
        try {
            String safeParticipantId = generateSafeParticipantId(participantId);
            return kubernetesClient.apps().deployments()
                    .inNamespace(proxyNamespace)
                    .withLabel("managed-by", "edr-kubectl-deployer")
                    .withLabel("owner-participant", safeParticipantId)
                    .list()
                    .getItems()
                    .stream()
                    .map(deployment -> deployment.getMetadata().getLabels().get("edr-id"))
                    .filter(edrId -> edrId != null && !edrId.isEmpty())
                    .toList();
                    
        } catch (Exception e) {
            LOGGER.warning(format("Error getting deployed proxy EDR keys: %s", e.getMessage()));
            return List.of();
        }
    }
    
    /**
     * Generates a safe Kubernetes-compatible identifier from participant ID
     */
    private String generateSafeParticipantId(String participantId) {
        if (participantId == null || participantId.isEmpty()) {
            return "default";
        }
        
        // Extract meaningful part from DID or URL
        String safeName = participantId;
        
        // Clean for Kubernetes naming requirements
        safeName = safeName.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        
        // Limit length for Kubernetes
        if (safeName.length() > 15) {
            safeName = safeName.substring(0, 15);
        }
        
        return safeName.isEmpty() ? "default" : safeName;
    }
    
    private ProxyHealth convertToProxyHealth(Deployment deployment) {
        String edrKey = deployment.getMetadata().getLabels().get("edr-id");
        if (edrKey == null) {
            edrKey = "unknown";
        }
        
        Integer replicas = deployment.getSpec().getReplicas();
        Integer readyReplicas = deployment.getStatus().getReadyReplicas();
        Integer availableReplicas = deployment.getStatus().getAvailableReplicas();
        
        boolean isHealthy = readyReplicas != null && readyReplicas.equals(replicas) &&
                          availableReplicas != null && availableReplicas.equals(replicas);
        
        String status;
        if (isHealthy) {
            status = "Healthy";
        } else {
            status = format("Ready: %d/%d, Available: %d/%d", 
                readyReplicas != null ? readyReplicas : 0, replicas != null ? replicas : 0,
                availableReplicas != null ? availableReplicas : 0, replicas != null ? replicas : 0);
        }
        
        return new ProxyHealth(edrKey, isHealthy, status,
                readyReplicas != null ? readyReplicas : 0,
                replicas != null ? replicas : 0);
    }
    
    private String generateProxyName(String edrKey) {
        return format("kafka-proxy-%s", edrKey.toLowerCase().replace("_", "-"));
    }
    
    /**
     * Represents the health status of a Kafka proxy deployment
     */
    public static class ProxyHealth {
        private final String edrKey;
        private final boolean healthy;
        private final String status;
        private final int readyReplicas;
        private final int totalReplicas;
        
        public ProxyHealth(String edrKey, boolean healthy, String status, int readyReplicas, int totalReplicas) {
            this.edrKey = edrKey;
            this.healthy = healthy;
            this.status = status;
            this.readyReplicas = readyReplicas;
            this.totalReplicas = totalReplicas;
        }
        
        public String getEdrKey() {
            return edrKey;
        }
        
        public boolean isHealthy() {
            return healthy;
        }
        
        public String getStatus() {
            return status;
        }
        
        public int getReadyReplicas() {
            return readyReplicas;
        }
        
        public int getTotalReplicas() {
            return totalReplicas;
        }
        
        @Override
        public String toString() {
            return format("ProxyHealth{edrKey='%s', healthy=%s, status='%s', replicas=%d/%d}", 
                    edrKey, healthy, status, readyReplicas, totalReplicas);
        }
    }
}