/*
 * Copyright (c) 2024 Dataspace Ecosystem
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contributors:
 *      Dataspace Ecosystem - initial API and implementation
 */

package org.eclipse.dse.watchdog;

import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Covers the interim (deprecated-for-removal) watchdog extension wiring; suppress [removal] warnings.
@SuppressWarnings("removal")
@ExtendWith(DependencyInjectionExtension.class)
class RecoveringCredentialWatchdogExtensionTest {

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        var monitor = mock(Monitor.class);
        when(monitor.withPrefix(org.mockito.ArgumentMatchers.anyString())).thenReturn(monitor);
        when(context.getMonitor()).thenReturn(monitor);
        // Disable the scheduler (period <= 0) so start() takes the no-op branch and needs no live thread.
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of("edc.iam.credential.status.check.period", "0")));
    }

    @Test
    void lifecycle_initializeStartShutdown_doesNotThrow(ServiceExtensionContext context, ObjectFactory factory) {
        var extension = factory.constructInstance(RecoveringCredentialWatchdogExtension.class);

        assertThatNoException().isThrownBy(() -> {
            extension.initialize(context);
            extension.start();
            extension.shutdown();
        });
    }

    @Test
    void lifecycle_withSchedulerEnabled_schedulesAndShutsDown(ServiceExtensionContext context, ObjectFactory factory) {
        // period > 0 enables the scheduled executor branch in initialize()/start().
        var monitor = mock(Monitor.class);
        when(monitor.withPrefix(org.mockito.ArgumentMatchers.anyString())).thenReturn(monitor);
        when(context.getMonitor()).thenReturn(monitor);
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of(
                "edc.iam.credential.status.check.period", "60",
                "edc.iam.credential.status.check.delay", "1")));

        var extension = factory.constructInstance(RecoveringCredentialWatchdogExtension.class);

        assertThatNoException().isThrownBy(() -> {
            extension.initialize(context);
            extension.start();
            extension.shutdown();
        });
    }
}
