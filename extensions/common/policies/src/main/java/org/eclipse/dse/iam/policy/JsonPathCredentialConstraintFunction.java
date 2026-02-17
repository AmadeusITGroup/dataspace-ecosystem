package org.eclipse.dse.iam.policy;

import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.util.reflection.ReflectionUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.eclipse.edc.policy.model.Operator.EQ;
import static org.eclipse.edc.policy.model.Operator.IN;
import static org.eclipse.edc.policy.model.Operator.NEQ;

public class JsonPathCredentialConstraintFunction<C extends ParticipantAgentPolicyContext> extends AbstractDynamicCredentialConstraintFunction<C> {

    private static final List<Operator> SUPPORTED_OPERATORS = List.of(EQ, NEQ, IN);
    
    private final PolicyConstants policyConstants;

    public JsonPathCredentialConstraintFunction(PolicyConstants policyConstants) {
        this.policyConstants = policyConstants;
    }

    @Override
    public boolean evaluate(Object leftOperand, Operator operator, Object rightOperand, Permission rule, C policyContext) {
        if (!checkOperator(operator, policyContext, SUPPORTED_OPERATORS)) {
            return false;
        }

        if (!canHandle(leftOperand)) {
            policyContext.reportProblem("Invalid left-operand '%s'".formatted(leftOperand));
            return false;
        }

        // validate right operand for IN operator
        Object processedRightOperand = rightOperand;
        if (operator.equals(IN)) {
            var parsedRightOperand = parseRightOperandForInOperator(rightOperand, policyContext);
            if (parsedRightOperand == null) {
                return false;
            }
            processedRightOperand = parsedRightOperand;
        }

        var sanitizedLeftOperand = sanitizeLeftOperand((String) leftOperand);
        var parts = sanitizedLeftOperand.split("\\.");
        if (parts.length < 2) {
            policyContext.reportProblem("Left operand must contain at least two parts but were %s.".formatted(sanitizedLeftOperand));
            return false;
        }

        var credentialType = parts[0];
        final var path = String.join(".", Arrays.copyOfRange(parts, 1, parts.length));
        final var finalOperator = operator;
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

        final var finalPolicyContext = policyContext;
        final var credentialSubjects = membershipCredential.getCredentialSubject();
        if (credentialSubjects.isEmpty()) {
            finalPolicyContext.reportProblem("No credential subject found in Membership Credential");
            return false;
        }
        
        return evaluateClaims(path, credentialSubjects.get(0).getClaims(), finalOperator, processedRightOperand, finalPolicyContext);
    }

    @Override
    public boolean canHandle(Object leftOperand) {
        return (leftOperand instanceof String) && ((String) leftOperand).startsWith("%s.$".formatted(policyConstants.getDseGenericClaimConstraint()));
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

    /**
     * Parses the right operand for IN operator, handling both List types and array-formatted strings
     * like ['value1', 'value2'] or ["value1", "value2"]
     */
    @SuppressWarnings("unchecked")
    private List<String> parseRightOperandForInOperator(Object rightOperand, PolicyContext policyContext) {
        if (rightOperand instanceof List<?>) {
            try {
                return (List<String>) rightOperand;
            } catch (ClassCastException e) {
                policyContext.reportProblem("When operator is IN, right-operand List must contain String elements.");
                return null;
            }
        } else if (rightOperand instanceof String arrayString) {
            return parseArrayString(arrayString, policyContext);
        } else {
            policyContext.reportProblem("When operator is %s, right-operand must be of type List or array string but was '%s'.".formatted(IN.toString(), rightOperand.getClass()));
            return null;
        }
    }

    /**
     * Parses array strings like ['value1', 'value2'] or ["value1", "value2"] into a List of strings
     */
    private List<String> parseArrayString(String arrayString, PolicyContext policyContext) {
        var trimmed = arrayString.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            policyContext.reportProblem("Array string must be enclosed in square brackets: " + arrayString);
            return null;
        }
        
        var content = trimmed.substring(1, trimmed.length() - 1).trim();
        if (content.isEmpty()) {
            return List.of(); // Empty array
        }
        
        var elements = new ArrayList<String>();
        var parts = content.split(",");
        
        for (var part : parts) {
            var element = part.trim();
            // Remove surrounding quotes if present and matching
            if (element.length() > 1) {
                if ((element.startsWith("'") && element.endsWith("'")) || 
                        (element.startsWith("\"") && element.endsWith("\""))) {
                    element = element.substring(1, element.length() - 1);
                }
            }
            elements.add(element);
        }
        
        return elements;
    }

    private String sanitizeLeftOperand(String leftOperand) {
        return leftOperand.replace("%s.$.".formatted(policyConstants.getDseGenericClaimConstraint()), "");
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

