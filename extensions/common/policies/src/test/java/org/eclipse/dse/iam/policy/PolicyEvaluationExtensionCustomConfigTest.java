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
 * Separate test class to test with custom configuration.
 * This demonstrates that different configurations can be used in different environments.
 */
@ExtendWith(DependencyInjectionExtension.class)
class PolicyEvaluationExtensionCustomConfigTest {

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
        
        // Register custom configuration to demonstrate override capability
        var customConfig = new DseNamespaceConfig(
                "https://w3id.org/custom/v0.0.1/ns/",
                "custom-policy",
                "https://w3id.org/custom/policy/"
        );
        context.registerService(DseNamespaceConfig.class, customConfig);
    }

    @Test
    void shouldRegisterCustomNamespace_whenCustomConfigProvided(ServiceExtensionContext context, PolicyEvaluationExtension extension) {
        extension.initialize(context);

        // Verify custom namespace is registered (not dse)
        verify(jsonLdService).registerNamespace(eq("custom-policy"), eq("https://w3id.org/custom/policy/"));
    }
}
