package org.eclipse.eonax.iam.core;

import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestContractNegotiationPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestTransferProcessPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.util.Set;

import static org.eclipse.edc.spi.core.CoreConstants.EONAX_VC_TYPE_SCOPE_ALIAS;


public class IatpPatchExtension implements ServiceExtension {

    private static final String READ_MEMBERSHIP_CREDENTIAL_SCOPE = "%s:MembershipCredential:read".formatted(EONAX_VC_TYPE_SCOPE_ALIAS);

    @Inject
    private PolicyEngine policyEngine;

    @Override
    public void initialize(ServiceExtensionContext context) {
        policyEngine.registerPostValidator(RequestCatalogPolicyContext.class, new DefaultScopeExtractor<>(Set.of(READ_MEMBERSHIP_CREDENTIAL_SCOPE)));
        policyEngine.registerPostValidator(RequestContractNegotiationPolicyContext.class, new DefaultScopeExtractor<>(Set.of(READ_MEMBERSHIP_CREDENTIAL_SCOPE)));
        policyEngine.registerPostValidator(RequestTransferProcessPolicyContext.class, new DefaultScopeExtractor<>(Set.of(READ_MEMBERSHIP_CREDENTIAL_SCOPE)));
    }
}

