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

import org.eclipse.dse.core.kafkaproxy.model.EdrProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EdrPropertiesTest {
    
    @Test
    void shouldDetectTlsEnabled_whenSecurityProtocolIsSsl() {
        var properties = new EdrProperties(
                "localhost:9092",
                "user",
                "pass",
                "SSL",
                "PLAIN",
                null,
                null,
                null,
                null,  // oauth2ClientId
                null,  // oauth2ClientSecret
                null,  // oauth2TenantId
                null   // oauth2Scope
        );
        
        assertThat(properties.isTlsEnabled()).isTrue();
    }
    
    @Test
    void shouldDetectTlsEnabled_whenSecurityProtocolIsSaslSsl() {
        var properties = new EdrProperties(
                "localhost:9092",
                "user",
                "pass",
                "SASL_SSL",
                "PLAIN",
                null,
                null,
                null,
                null,  // oauth2ClientId
                null,  // oauth2ClientSecret
                null,  // oauth2TenantId
                null   // oauth2Scope
        );
        
        assertThat(properties.isTlsEnabled()).isTrue();
    }
    
    @Test
    void shouldDetectTlsDisabled_whenSecurityProtocolIsPlaintext() {
        var properties = new EdrProperties(
                "localhost:9092",
                "user",
                "pass",
                "PLAINTEXT",
                "PLAIN",
                null,
                null,
                null,
                null,  // oauth2ClientId
                null,  // oauth2ClientSecret
                null,  // oauth2TenantId
                null   // oauth2Scope
        );
        
        assertThat(properties.isTlsEnabled()).isFalse();
    }
}