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

package org.eclipse.dse.core.kafkaproxy.model;

import org.eclipse.dse.core.kafkaproxy.service.VaultDiscoveryService;

/**
 * Represents the result of discovering an EDR in Vault
 */
public class EdrDiscoveryResult {
    
    private final String edrKey;
    private final boolean isKafkaEdr;
    private final boolean isReadyForDeployment;
    private final boolean needsTls;
    private final VaultDiscoveryService.KafkaProperties kafkaProperties;
    
    public EdrDiscoveryResult(String edrKey, boolean isKafkaEdr, boolean isReadyForDeployment, 
                            boolean needsTls, VaultDiscoveryService.KafkaProperties kafkaProperties) {
        this.edrKey = edrKey;
        this.isKafkaEdr = isKafkaEdr;
        this.isReadyForDeployment = isReadyForDeployment;
        this.needsTls = needsTls;
        this.kafkaProperties = kafkaProperties;
    }
    
    public String getEdrKey() {
        return edrKey;
    }
    
    public boolean isKafkaEdr() {
        return isKafkaEdr;
    }
    
    public boolean isReadyForDeployment() {
        return isReadyForDeployment;
    }
    
    public boolean needsTls() {
        return needsTls;
    }
    
    public VaultDiscoveryService.KafkaProperties getKafkaProperties() {
        return kafkaProperties;
    }
    
    @Override
    public String toString() {
        return String.format("EdrDiscoveryResult{edrKey='%s', isKafka=%s, readyForDeployment=%s, needsTls=%s}", 
                edrKey, isKafkaEdr, isReadyForDeployment, needsTls);
    }
}