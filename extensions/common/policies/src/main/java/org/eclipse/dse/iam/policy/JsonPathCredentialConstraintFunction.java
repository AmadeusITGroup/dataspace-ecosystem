package org.eclipse.dse.iam.policy;

import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.util.reflection.ReflectionUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.eclipse.dse.iam.policy.PolicyConstants.DSE_GENERIC_CLAIM_CONSTRAINT;
import static org.eclipse.edc.policy.model.Operator.EQ;
import static org.eclipse.edc.policy.model.Operator.IN;
import static org.eclipse.edc.policy.model.Operator.NEQ;

public class JsonPathCredentialConstraintFunction<C extends ParticipantAgentPolicyContext> extends AbstractDynamicCredentialConstraintFunction<C> {

    private static final List<Operator> SUPPORTED_OPERATORS = List.of(EQ, NEQ, IN);

    @Override
    public boolean evaluate(Object leftOperand, Operator operator, Object rightOperand, Permission rule, C policyContext) {
        if (!checkOperator(operator, policyContext, SUPPORTED_OPERATORS)) {
            return false;
        }

        if (!canHandle(leftOperand)) {
            policyContext.reportProblem("Invalid left-operand '%s'".formatted(leftOperand));
            return false;
        }

        // we do not support list-type right-operands
        if (operator.equals(IN) && !(rightOperand instanceof List<?>)) {
            policyContext.reportProblem("When operator is %s, right-operand must be of type List but were '%s'.".formatted(IN.toString(), rightOperand.getClass()));
            return false;
        }

        var sanitizedLeftOperand = sanitizeLeftOperand((String) leftOperand);
        var parts = sanitizedLeftOperand.split("\\.");
        if (parts.length < 2) {
            policyContext.reportProblem("Left operand must contain at least two parts but were %s.".formatted(sanitizedLeftOperand));
            return false;
        }

        var credentialType = parts[0];
        var path = String.join(".", Arrays.copyOfRange(parts, 1, parts.length));
        var credentials = getCredentialList(policyContext.participantAgent());
        if (credentials.failed()) {
            policyContext.reportProblem("Failed to get Credentials list.");
            return false;
        }

        var membershipCredential = credentials.getContent().stream()
                .filter(new CredentialTypePredicate(credentialType))
                .findFirst()
                .orElse(null);
        if (membershipCredential == null) {
            policyContext.reportProblem("Failed to find %s.".formatted(credentialType));
            return false;
        }

        return membershipCredential.getCredentialSubject().stream()
                .findFirst()
                .map(credentialSubject -> evaluateClaims(path, credentialSubject.getClaims(), operator, rightOperand, policyContext))
                .orElseGet(() -> {
                    policyContext.reportProblem("No credential subject found in Membership Credential");
                    return false;
                });
    }

    @Override
    public boolean canHandle(Object leftOperand) {
        return (leftOperand instanceof String) && ((String) leftOperand).startsWith("%s.$".formatted(DSE_GENERIC_CLAIM_CONSTRAINT));
    }


    private boolean evaluateClaims(String path, Map<String, Object> claims, Operator operator, Object right, PolicyContext policyContext) {
        var sanitized = sanitizeClaims(claims);
        var leftValue = ReflectionUtil.getFieldValue(path, sanitized);
        if (!(leftValue instanceof String)) {
            policyContext.reportProblem("Missing string field '%s' in claims".formatted(path));
            return false;
        }
        return evaluateField((String) leftValue, operator, right);
    }

    private boolean evaluateField(String left, Operator operator, Object right) {
        return switch (operator) {
            case EQ -> left.equals(right);
            case NEQ -> !left.equals(right);
            case IN -> ((List<String>) right).contains(left);
            default -> false;
        };
    }

    private String sanitizeLeftOperand(String leftOperand) {
        return leftOperand.replace("%s.$.".formatted(DSE_GENERIC_CLAIM_CONSTRAINT), "");
    }

    private Map<String, Object> sanitizeClaims(Map<String, Object> claims) {
        var sanitized = new HashMap<String, Object>();
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            String key = entry.getKey();
            int lastSlashIndex = key.lastIndexOf('/');
            if (lastSlashIndex != -1) {
                key = key.substring(lastSlashIndex + 1);
            }
            sanitized.put(key, entry.getValue());
        }
        return sanitized;
    }

}

