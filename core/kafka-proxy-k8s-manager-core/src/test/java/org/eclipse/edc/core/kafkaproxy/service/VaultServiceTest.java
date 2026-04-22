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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VaultServiceTest {

    @Mock
    private VaultDiscoveryService discoveryService;

    private VaultService vaultService;

    @BeforeEach
    void setUp() {
        vaultService = new VaultService(discoveryService);
    }

    @Test
    void isEdrTaggedForDeployment_returnsTrue_whenEdrAlreadyProcessed() {
        when(discoveryService.isEdrAlreadyProcessed("edr--test")).thenReturn(true);

        assertTrue(vaultService.isEdrTaggedForDeployment("edr--test"));
        verify(discoveryService).isEdrAlreadyProcessed("edr--test");
    }

    @Test
    void isEdrTaggedForDeployment_returnsFalse_whenEdrNotYetProcessed() {
        when(discoveryService.isEdrAlreadyProcessed("edr--test")).thenReturn(false);

        assertFalse(vaultService.isEdrTaggedForDeployment("edr--test"));
        verify(discoveryService).isEdrAlreadyProcessed("edr--test");
    }

    @Test
    void isEdrTaggedForDeployment_returnsFalse_whenDiscoveryServiceThrows() {
        when(discoveryService.isEdrAlreadyProcessed("edr--error"))
                .thenThrow(new RuntimeException("vault unreachable"));

        assertFalse(vaultService.isEdrTaggedForDeployment("edr--error"));
        verify(discoveryService).isEdrAlreadyProcessed("edr--error");
    }
}
