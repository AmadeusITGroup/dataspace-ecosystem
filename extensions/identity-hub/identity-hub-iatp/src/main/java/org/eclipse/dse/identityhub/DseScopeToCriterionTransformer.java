package org.eclipse.dse.identityhub;

import org.eclipse.edc.identityhub.spi.transformation.ScopeToCriterionTransformer;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.result.Result;

import java.util.List;

import static org.eclipse.edc.spi.core.CoreConstants.DSE_VC_TYPE_SCOPE_ALIAS;
import static org.eclipse.edc.spi.result.Result.failure;
import static org.eclipse.edc.spi.result.Result.success;

public class DseScopeToCriterionTransformer implements ScopeToCriterionTransformer {

    protected static final String TYPE_OPERAND = "verifiableCredential.credential.type";
    protected static final String CONTAINS_OPERATOR = "contains";
    protected static final String SCOPE_SEPARATOR = ":";
    private final List<String> allowedOperations = List.of("read", "*", "all");

    @Override
    public Result<Criterion> transform(String scope) {
        var tokens = tokenize(scope);
        if (tokens.failed()) {
            return failure("Scope string cannot be converted: %s".formatted(tokens.getFailureDetail()));
        }
        var credentialType = tokens.getContent()[1];
        return success(new Criterion(TYPE_OPERAND, CONTAINS_OPERATOR, credentialType));
    }

    protected Result<String[]> tokenize(String scope) {
        if (scope == null) return failure("Scope was null");

        var tokens = scope.split(SCOPE_SEPARATOR);
        if (tokens.length != 3) {
            return failure("Scope string has invalid format.");
        }
        if (!DSE_VC_TYPE_SCOPE_ALIAS.equalsIgnoreCase(tokens[0])) {
            return failure("Scope alias MUST be %s but was %s".formatted(DSE_VC_TYPE_SCOPE_ALIAS, tokens[0]));
        }
        if (!allowedOperations.contains(tokens[2])) {
            return failure("Invalid scope operation: " + tokens[2]);
        }

        return success(tokens);
    }
}