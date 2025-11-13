package org.eclipse.dse.iam.core;

import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.spi.iam.RequestScope;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultScopeExtractorTest {

    private static final Set<String> DEFAULT_SCOPES = Set.of("defaultScope1", "defaultScope2");

    private final DefaultScopeExtractor scopeExtractor = new DefaultScopeExtractor(DEFAULT_SCOPES);

    @Test
    void apply() {
        var existingScope = "aScope";
        var builder = RequestScope.Builder.newInstance().scope(existingScope);
        var policyContext = new TestPolicyContext(builder);

        var result = scopeExtractor.apply(null, policyContext);

        assertThat(result).isTrue();
        var requestScope = builder.build();
        assertThat(requestScope.getScopes())
                .hasSize(1 + DEFAULT_SCOPES.size())
                .containsAll(DEFAULT_SCOPES)
                .contains(existingScope);
    }

    public static class TestPolicyContext extends RequestPolicyContext {

        protected TestPolicyContext(RequestScope.Builder requestScopeBuilder) {
            super(null, requestScopeBuilder);
        }

        @Override
        public String scope() {
            return "test.scope";
        }
    }

}