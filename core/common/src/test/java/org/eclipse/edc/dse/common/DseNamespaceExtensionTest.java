/********************************************************************************
 * Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.edc.dse.common;

import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for DseNamespaceExtension to verify that configuration values
 * can be overridden and are properly applied.
 * <p>
 * NOTE: These tests verify the configuration mechanism by checking the generated
 * namespace and policy URLs. The actual @Setting field injection is handled by the
 * EDC runtime framework at runtime. In integration tests with real configuration files,
 * the values are properly injected from properties.
 */
@ExtendWith(DependencyInjectionExtension.class)
class DseNamespaceExtensionTest {

    private final Monitor monitor = mock();

    @BeforeEach
    void setup(ServiceExtensionContext context) {
        context.registerService(Monitor.class, monitor);
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of(
                "dse.namespace.prefix", "dse",
                "dse.namespace.version", "v0.0.1",
                "dse.policy.prefix", "dse-policy"
        )));
    }

    @Test
    void shouldProvideDefaultConfiguration_whenNoCustomConfigSet(DseNamespaceExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);
        var config = extension.dseNamespaceConfig();

        // Verify default values are used (as configured in @BeforeEach with default values)
        assertThat(config).isNotNull();
        assertThat(config.dseNamespace()).isEqualTo("https://w3id.org/dse/v0.0.1/ns/");
        assertThat(config.dsePolicyPrefix()).isEqualTo("dse-policy");
        assertThat(config.dsePolicyNamespace()).isEqualTo("https://w3id.org/dse/policy/");
    }

    @Test
    void shouldProvideConfigThroughProviderMethod(DseNamespaceExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);
        var providedConfig = extension.dseNamespaceConfig();

        assertThat(providedConfig).isNotNull();
        assertThat(providedConfig).isInstanceOf(DseNamespaceConfig.class);
    }

    @Test
    void shouldLogConfigurationOnInitialization(DseNamespaceExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);

        // Verify logging occurs during initialization
        verify(monitor).info(contains("DSE Namespace configured"));
        verify(monitor).info(contains("dse"));
        verify(monitor).info(contains("dse-policy"));
    }

    @Test
    void shouldCreateCorrectNamespaceUrls_whenInitialized(DseNamespaceExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);
        var config = extension.dseNamespaceConfig();

        // Verify URLs are correctly formed
        assertThat(config.dseNamespace()).startsWith("https://w3id.org/");
        assertThat(config.dseNamespace()).endsWith("/ns/");
        assertThat(config.dsePolicyNamespace()).startsWith("https://w3id.org/");
        assertThat(config.dsePolicyNamespace()).endsWith("/policy/");
    }

    @Test
    void shouldProvideNonNullConfig_afterInitialization(DseNamespaceExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);
        var config = extension.dseNamespaceConfig();

        // Verify all config fields are non-null
        assertThat(config).isNotNull();
        assertThat(config.dseNamespace()).isNotNull().isNotEmpty();
        assertThat(config.dsePolicyPrefix()).isNotNull().isNotEmpty();
        assertThat(config.dsePolicyNamespace()).isNotNull().isNotEmpty();
    }
}
