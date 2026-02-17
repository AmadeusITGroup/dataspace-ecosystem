package org.eclipse.dse.iam.policy;

import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.dse.common.DseNamespaceConfig;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.util.Set;

import static org.eclipse.dse.iam.policy.CatalogDiscoveryPolicyContext.CATALOG_DISCOVERY_SCOPE;
import static org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext.CATALOG_SCOPE;
import static org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext.NEGOTIATION_SCOPE;
import static org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext.TRANSFER_SCOPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_USE_ACTION_ATTRIBUTE;

public class PolicyEvaluationExtension implements ServiceExtension {

    private static final Set<String> RULE_SCOPES = Set.of(
            TRANSFER_SCOPE,
            CATALOG_SCOPE,
            NEGOTIATION_SCOPE,
            CATALOG_DISCOVERY_SCOPE
    );

    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private RuleBindingRegistry ruleBindingRegistry;

    @Inject
    private JsonLd jsonLdService;

    @Inject
    private Monitor monitor;

    @Inject
    private DseNamespaceConfig namespaceConfig;

    private PolicyConstants policyConstants;

    @Override
    public void initialize(ServiceExtensionContext context) {
        policyConstants = new PolicyConstants(namespaceConfig);
        registerNamespaces();
        registerFunctions();
        registerBindings();
    }

    private void registerNamespaces() {
        jsonLdService.registerNamespace(namespaceConfig.dsePolicyPrefix(), namespaceConfig.dsePolicyNamespace());
    }

    private void registerFunctions() {
        
        policyEngine.registerFunction(CatalogPolicyContext.class, Permission.class, new MembershipConstraintFunction<>(policyConstants));
        policyEngine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class, new MembershipConstraintFunction<>(policyConstants));
        policyEngine.registerFunction(TransferProcessPolicyContext.class, Permission.class, new MembershipConstraintFunction<>(policyConstants));
        policyEngine.registerFunction(CatalogDiscoveryPolicyContext.class, Permission.class, new MembershipConstraintFunction<>(policyConstants));

        policyEngine.registerFunction(CatalogPolicyContext.class, Permission.class, new JsonPathCredentialConstraintFunction<>(policyConstants));
        policyEngine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class, new JsonPathCredentialConstraintFunction<>(policyConstants));
        policyEngine.registerFunction(TransferProcessPolicyContext.class, Permission.class, new JsonPathCredentialConstraintFunction<>(policyConstants));
        policyEngine.registerFunction(CatalogDiscoveryPolicyContext.class, Permission.class, new JsonPathCredentialConstraintFunction<>(policyConstants));

        policyEngine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class, new CatalogDiscoveryConstraintFunction<>(policyConstants));
        policyEngine.registerFunction(TransferProcessPolicyContext.class, Permission.class, new CatalogDiscoveryConstraintFunction<>(policyConstants));
        policyEngine.registerFunction(CatalogDiscoveryPolicyContext.class, Permission.class, new CatalogDiscoveryConstraintFunction<>(policyConstants));
    }

    private void registerBindings() {
        ruleBindingRegistry.dynamicBind(s -> s.startsWith(policyConstants.getDseGenericClaimConstraint()) ? Set.of(NEGOTIATION_SCOPE, TRANSFER_SCOPE) : Set.of());
        ruleBindingRegistry.dynamicBind(s -> s.startsWith(policyConstants.getDseMembershipConstraint()) ? RULE_SCOPES : Set.of());
        ruleBindingRegistry.dynamicBind(s -> s.startsWith(policyConstants.getDseRestrictedCatalogDiscoveryConstraint()) ? Set.of(CATALOG_DISCOVERY_SCOPE, NEGOTIATION_SCOPE, TRANSFER_SCOPE) : Set.of());
        RULE_SCOPES.forEach(scope -> ruleBindingRegistry.bind(ODRL_USE_ACTION_ATTRIBUTE, scope));
    }

}
