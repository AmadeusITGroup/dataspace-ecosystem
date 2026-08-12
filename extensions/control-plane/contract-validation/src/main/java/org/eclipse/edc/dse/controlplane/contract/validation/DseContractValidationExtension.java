/*
 *  Copyright (c) 2025 Amadeus
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 */

package org.eclipse.edc.dse.controlplane.contract.validation;

import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.policy.PolicyEquality;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ContractValidationService;
import org.eclipse.edc.connector.controlplane.policy.contract.ContractExpiryCheckFunction;
import org.eclipse.edc.dse.controlplane.query.asset.DseAssetPropertyLookup;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

import static org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext.NEGOTIATION_SCOPE;
import static org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext.TRANSFER_SCOPE;
import static org.eclipse.edc.connector.controlplane.policy.contract.ContractExpiryCheckFunction.CONTRACT_EXPIRY_EVALUATION_KEY;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_USE_ACTION_ATTRIBUTE;

/**
 * Registers {@link DseContractValidationServiceImpl} as the {@link ContractValidationService} implementation,
 * overriding EDC's default {@code ContractCoreExtension}. Unlike the upstream service, the assetsSelector
 * check on the initial contract offer is evaluated in-memory against the already-resolved target
 * {@link org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset}, using the shared
 * {@link CriterionOperatorRegistry} (and whichever asset-aware {@code PropertyLookup} is registered on it,
 * e.g. {@code DseAssetPropertyLookup}), instead of re-querying the {@link AssetIndex} with the selector criteria.
 * <p>
 * EDC's own {@code ContractCoreExtension} registers {@code ContractValidationService} eagerly (not as a
 * {@code @Provider(isDefault = true)}), and it is deliberately kept on the runtime classpath here (rather than
 * excluded), because {@code control-plane-contract} also provides several other hard-required services
 * ({@code ConsumerOfferResolver}, {@code ContractNegotiationObservable}, {@code PolicyArchive}) and a
 * {@code TerminateNegotiationCommandHandler} registration that the rest of the control plane (e.g. Transfer
 * Core/Manager) needs; excluding the whole module breaks dependency injection at boot.
 * <p>
 * Instead, this extension injects an (unused) {@link ContractValidationService} field purely to create a
 * dependency edge on every extension that provides that type (i.e. {@code ContractCoreExtension}). EDC's
 * {@code DependencyGraph} sorts providers before their dependents, so this guarantees {@code ContractCoreExtension}
 * always {@code initialize()}s (and registers its implementation) before this extension does — meaning our
 * {@code context.registerService(...)} call always runs last and deterministically overwrites EDC's registration,
 * regardless of {@code ServiceLoader}/classpath ordering.
 * <p>
 * This extension replicates the parts of {@code ContractCoreExtension} that {@link DseContractValidationServiceImpl}
 * depends on for policy evaluation (policy scope registration and the contract-expiry rule/function binding);
 * those registrations are idempotent/harmless to run twice (repeated bindings), so no ordering concern applies there.
 */
@Provides({
        ContractValidationService.class
})
@Extension(value = DseContractValidationExtension.EXTENSION_NAME)
public class DseContractValidationExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "Dse Contract Validation";

    @Inject
    private AssetIndex assetIndex;

    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private TypeManager typeManager;

    @Inject
    private RuleBindingRegistry ruleBindingRegistry;

    /**
     * Unused; injecting this forces EDC's DependencyGraph to add a dependency edge from this extension to
     * every other extension providing {@link ContractValidationService} (i.e. {@code ContractCoreExtension}),
     * so that this extension always initializes (and overwrites the registration) after it, deterministically.
     */
    @Inject
    private ContractValidationService existingContractValidationService;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        policyEngine.registerScope(CatalogPolicyContext.CATALOG_SCOPE, CatalogPolicyContext.class);
        policyEngine.registerScope(NEGOTIATION_SCOPE, ContractNegotiationPolicyContext.class);
        policyEngine.registerScope(TRANSFER_SCOPE, TransferProcessPolicyContext.class);

        registerServices(context);
    }

    private void registerServices(ServiceExtensionContext context) {
        var policyEquality = new PolicyEquality(typeManager);
        var criterionOperatorRegistry = CriterionOperatorRegistryImpl.ofDefaults();
        criterionOperatorRegistry.registerPropertyLookup(new DseAssetPropertyLookup());
        var validationService = new DseContractValidationServiceImpl(assetIndex, policyEngine, policyEquality, criterionOperatorRegistry);
        context.registerService(ContractValidationService.class, validationService);

        // bind/register rule to evaluate contract expiry
        ruleBindingRegistry.bind(ODRL_USE_ACTION_ATTRIBUTE, TRANSFER_SCOPE);
        ruleBindingRegistry.bind(CONTRACT_EXPIRY_EVALUATION_KEY, TRANSFER_SCOPE);

        policyEngine.registerFunction(TransferProcessPolicyContext.class, Permission.class, CONTRACT_EXPIRY_EVALUATION_KEY,
                new ContractExpiryCheckFunction<>());
    }
}
