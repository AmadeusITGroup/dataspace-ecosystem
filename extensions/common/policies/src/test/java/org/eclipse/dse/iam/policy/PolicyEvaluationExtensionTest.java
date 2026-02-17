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

package org.eclipse.dse.iam.policy;

import org.eclipse.edc.dse.common.DseNamespaceConfig;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for PolicyEvaluationExtension to verify that DseNamespaceConfig
 * is properly injected and used to register the correct namespace.
 * <p>
 * These tests demonstrate that the policy evaluation uses the injected
 * DseNamespaceConfig. The config is injected when the extension is created,
 * which happens before the test method runs. In production, the config values
 * come from properties files (e.g., dse.namespace.prefix=custom).
 */
@ExtendWith(DependencyInjectionExtension.class)
class PolicyEvaluationExtensionTest {

    private final PolicyEngine policyEngine = mock();
    private final RuleBindingRegistry ruleBindingRegistry = mock();
    private final JsonLd jsonLdService = mock();
    private final Monitor monitor = mock();

    @BeforeEach
    void setup(ServiceExtensionContext context) {
        context.registerService(PolicyEngine.class, policyEngine);
        context.registerService(RuleBindingRegistry.class, ruleBindingRegistry);
        context.registerService(JsonLd.class, jsonLdService);
        context.registerService(Monitor.class, monitor);
        
        // Register a default DseNamespaceConfig
        // NOTE: This must be done in @BeforeEach before extension parameters are resolved
        var defaultConfig = new DseNamespaceConfig(
                "https://w3id.org/dse/v0.0.1/ns/",
                "dse-policy",
                "https://w3id.org/dse/policy/"
        );
        context.registerService(DseNamespaceConfig.class, defaultConfig);
    }

    @Test
    void shouldRegisterDefaultDseNamespace_whenDefaultConfigUsed(ServiceExtensionContext context, PolicyEvaluationExtension extension) {
        // Use the default config registered in @BeforeEach
        extension.initialize(context);

        // Verify the default DSE namespace is registered
        verify(jsonLdService).registerNamespace(eq("dse-policy"), eq("https://w3id.org/dse/policy/"));
    }

    @Test
    void shouldUseInjectedNamespaceConfig(ServiceExtensionContext context, PolicyEvaluationExtension extension) {
        // This test verifies that the DseNamespaceConfig is properly injected via @Inject
        extension.initialize(context);

        // If the config wasn't injected, this would throw NullPointerException
        // The fact that it completes means injection is working
        verify(jsonLdService).registerNamespace(eq("dse-policy"), eq("https://w3id.org/dse/policy/"));
    }
}
