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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for VaultDiscoveryService
 * Note: These are unit tests for the discovery logic, not integration tests with actual Vault
 */
class VaultDiscoveryServiceTest {

    @Test
    void testKafkaPropertiesValidation() {
        // Test KafkaProperties validation
        var validProps = new VaultDiscoveryService.KafkaProperties(
                "localhost:9092", "test-topic", "SASL_SSL", "PLAIN", "jaas-config", true, true
        );
        assertTrue(validProps.hasRequiredProperties());
        assertTrue(validProps.needsTls());
        assertTrue(validProps.needsSasl());
        
        var invalidProps = new VaultDiscoveryService.KafkaProperties();
        assertFalse(invalidProps.hasRequiredProperties());
        assertFalse(invalidProps.needsTls());
        assertFalse(invalidProps.needsSasl());
    }
    
    @Test
    void testEdrDiscoveryResultCreation() {
        var kafkaProps = new VaultDiscoveryService.KafkaProperties(
                "localhost:9092", "test-topic", "SASL_SSL", "PLAIN", "jaas-config", true, true
        );
        
        var result = new EdrDiscoveryResult("edr--test", true, true, true, kafkaProps);
        
        assertEquals("edr--test", result.getEdrKey());
        assertTrue(result.isKafkaEdr());
        assertTrue(result.isReadyForDeployment());
        assertTrue(result.needsTls());
        assertNotNull(result.getKafkaProperties());
        assertEquals("localhost:9092", result.getKafkaProperties().getBootstrapServers());
    }
    
    @Test
    void testEdrDiscoveryResultToString() {
        var result = new EdrDiscoveryResult("edr--test", true, false, false, null);
        String resultString = result.toString();
        
        assertNotNull(resultString);
        assertTrue(resultString.contains("edr--test"));
        assertTrue(resultString.contains("isKafka=true"));
        assertTrue(resultString.contains("readyForDeployment=false"));
    }
    
    @Test
    void testKafkaPropertiesDefaults() {
        var props = new VaultDiscoveryService.KafkaProperties(
                "localhost:9092", "test-topic", "PLAINTEXT", "PLAIN", "", false, false
        );
        
        assertEquals("PLAINTEXT", props.getSecurityProtocol());
        assertEquals("PLAIN", props.getSaslMechanism());
        assertFalse(props.needsTls());
        assertFalse(props.needsSasl());
    }
    
    @Test
    void testSecurityProtocolDetection() {
        // Test TLS detection
        var sslProps = new VaultDiscoveryService.KafkaProperties(
                "localhost:9092", "test-topic", "SSL", "PLAIN", "", true, false
        );
        assertTrue(sslProps.needsTls());
        
        var saslSslProps = new VaultDiscoveryService.KafkaProperties(
                "localhost:9092", "test-topic", "SASL_SSL", "PLAIN", "", true, true
        );
        assertTrue(saslSslProps.needsTls());
        assertTrue(saslSslProps.needsSasl());
        
        var plaintextProps = new VaultDiscoveryService.KafkaProperties(
                "localhost:9092", "test-topic", "PLAINTEXT", "PLAIN", "", false, false
        );
        assertFalse(plaintextProps.needsTls());
    }
}