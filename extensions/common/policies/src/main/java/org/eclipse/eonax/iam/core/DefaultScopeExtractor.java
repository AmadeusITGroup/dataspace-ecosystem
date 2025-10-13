package org.eclipse.eonax.iam.core;

import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyValidatorRule;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.iam.RequestScope;

import java.util.Set;

import static java.lang.String.format;

public class DefaultScopeExtractor<C extends RequestPolicyContext> implements PolicyValidatorRule<C> {
    private final Set<String> defaultScopes;

    public DefaultScopeExtractor(Set<String> defaultScopes) {
        this.defaultScopes = defaultScopes;
    }

    @Override
    public Boolean apply(Policy policy, RequestPolicyContext policyContext) {
        var scopes = policyContext.requestScopeBuilder();
        if (scopes == null) {
            throw new EdcException(format("%s not set in policy context", RequestScope.Builder.class.getName()));
        }
        defaultScopes.forEach(scopes::scope);
        return true;
    }

}
