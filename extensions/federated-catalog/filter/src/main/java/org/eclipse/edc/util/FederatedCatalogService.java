package org.eclipse.edc.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.dse.iam.policy.CatalogDiscoveryPolicyContext;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.AbstractResult;
import org.eclipse.edc.spi.result.Result;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.eclipse.edc.FilterConstants.CATALOG_ACTION;
import static org.eclipse.edc.FilterConstants.CATALOG_AND;
import static org.eclipse.edc.FilterConstants.CATALOG_CONSTRAINT;
import static org.eclipse.edc.FilterConstants.CATALOG_CONTEXT;
import static org.eclipse.edc.FilterConstants.CATALOG_DATASET;
import static org.eclipse.edc.FilterConstants.CATALOG_ID;
import static org.eclipse.edc.FilterConstants.CATALOG_LEFT_OPERAND;
import static org.eclipse.edc.FilterConstants.CATALOG_OPERATOR;
import static org.eclipse.edc.FilterConstants.CATALOG_OR;
import static org.eclipse.edc.FilterConstants.CATALOG_PERMISSION;
import static org.eclipse.edc.FilterConstants.CATALOG_POLICY;
import static org.eclipse.edc.FilterConstants.CATALOG_RIGHT_OPERAND;
import static org.eclipse.edc.FilterConstants.DSPACE_PARTICIPANTDID;
import static org.eclipse.edc.FilterConstants.VC_CLAIMS;


public class FederatedCatalogService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PolicyEngine policyEngine;
    private final Monitor monitor;
    private final AuthorityCatalogFilterDidResolver didResolver;

    public FederatedCatalogService(PolicyEngine policyEngine, ObjectMapper objectMapper, Monitor monitor, AuthorityCatalogFilterDidResolver didResolver) {
        this.policyEngine = policyEngine;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.monitor = monitor;
        this.didResolver = didResolver;
    }

    public List<JsonNode> fetchAndFilterCatalog(ClaimToken participantVcs, String participantDid) throws Exception {
        CatalogDiscoveryPolicyContext policyContext = createContext(participantVcs);
        JsonNode root = retrieveCatalog();
        return filterCatalog(root, policyContext, participantDid);
    }

    protected CatalogDiscoveryPolicyContext createContext(ClaimToken participantVcs) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(VC_CLAIMS, participantVcs.getClaim(VC_CLAIMS));
        ParticipantAgent agent = new ParticipantAgent(claims, Collections.emptyMap());
        return new CatalogDiscoveryPolicyContext(agent);
    }

    private JsonNode retrieveCatalog() throws IOException, InterruptedException {
        Result<String> urlResult = didResolver.fetchCatalogFilterUrl();
        if (urlResult.failed()) {
            throw new RuntimeException("Failed to resolve catalog URL: " + urlResult.getFailureMessages());
        }
        String catalogUrl = urlResult.getContent();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(catalogUrl))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to query catalog: " + response.body());
        }
        JsonNode root = objectMapper.readTree(response.body());
        if (!root.isArray()) {
            throw new RuntimeException("Unexpected catalog response structure: expected array");
        }
        return root;
    }

    protected List<JsonNode> filterCatalog(JsonNode root, CatalogDiscoveryPolicyContext policyContext, String participantDid) {
        List<JsonNode> filtered = new ArrayList<>();
        for (JsonNode catalogEntry : root) {
            ObjectNode filteredCatalog = catalogEntry.deepCopy();
            JsonNode identity = catalogEntry.get(DSPACE_PARTICIPANTDID);
            if (identity != null && identity.asText().equals(participantDid)) {
                filtered.add(filteredCatalog);
                continue;
            }
            JsonNode datasets = catalogEntry.get(CATALOG_DATASET);
            if (datasets == null || !datasets.isArray() || datasets.isEmpty()) {
                filtered.add(filteredCatalog);
                continue;
            }
            ArrayNode filteredDatasets = filterDataAssets(datasets, policyContext, catalogEntry.get(CATALOG_CONTEXT));
            filteredCatalog.set(CATALOG_DATASET, filteredDatasets);
            filtered.add(filteredCatalog);
        }
        return filtered;
    }

    private ArrayNode filterDataAssets(JsonNode datasets, CatalogDiscoveryPolicyContext policyContext, JsonNode catalogContext) {
        ArrayNode filteredDatasets = objectMapper.createArrayNode();
        for (JsonNode dataset : datasets) {
            JsonNode policyNode = dataset.get(CATALOG_POLICY);
            if (policyNode == null) {
                continue;
            }
            JsonNode permissions = policyNode.get(CATALOG_PERMISSION);
            if (permissions == null) {
                continue;
            }
            List<JsonNode> permissionList = new ArrayList<>();
            if (permissions.isArray()) {
                permissions.forEach(permissionList::add);
            } else {
                permissionList.add(permissions);
            }
            List<Result<Void>> filteredPermissions = permissionList.stream().map(p -> {
                return applyPermissions(p, catalogContext, policyContext);
            }).toList();
            if (filteredPermissions.stream().noneMatch(AbstractResult::failed)) {
                filteredDatasets.add(dataset);
            }
        }
        return filteredDatasets;
    }

    private Result<Void> applyPermissions(JsonNode permission, JsonNode context, CatalogDiscoveryPolicyContext policyContext) {
        JsonNode constraints = permission.get(CATALOG_CONSTRAINT);
        if (constraints == null) {
            return Result.failure("No constraints found in permission");
        }

        JsonNode actionType = permission.get(CATALOG_ACTION);
        if (actionType == null) {
            return Result.failure("No action found in permission");
        }

        String expandedActionType = expandNamespace(actionType.get(CATALOG_ID).asText(), context);

        return evaluateConstraintTree(constraints, context, expandedActionType, policyContext);
    }

    private Result<Void> evaluateConstraintTree(JsonNode constraintNode, JsonNode context, String actionType, CatalogDiscoveryPolicyContext policyContext) {
        if (constraintNode.has(CATALOG_OR)) {
            return evaluateOrConstraint(constraintNode.get(CATALOG_OR), context, actionType, policyContext);
        }

        if (constraintNode.has(CATALOG_AND)) {
            return evaluateAndConstraint(constraintNode.get(CATALOG_AND), context, actionType, policyContext);
        }

        return evaluateAtomicConstraint(constraintNode, context, actionType, policyContext);
    }

    private Result<Void> evaluateOrConstraint(JsonNode orArray, JsonNode context, String actionType, CatalogDiscoveryPolicyContext policyContext) {
        if (!orArray.isArray()) {
            return Result.failure("Invalid OR constraint: expected array");
        }

        if (orArray.isEmpty()) {
            return Result.failure("Empty OR constraint array");
        }

        List<String> allFailureMessages = new ArrayList<>();

        for (JsonNode branch : orArray) {
            Result<Void> branchResult = evaluateConstraintTree(branch, context, actionType, policyContext);
            if (branchResult.succeeded()) {
                monitor.debug("OR constraint: branch succeeded, overall OR constraint passes");
                return Result.success();
            }
            allFailureMessages.addAll(branchResult.getFailureMessages());
        }

        String combinedFailures = String.join("; ", allFailureMessages);
        monitor.debug("OR constraint: all branches failed - " + combinedFailures);
        return Result.failure("OR constraint failed: all branches failed - " + combinedFailures);
    }

    private Result<Void> evaluateAndConstraint(JsonNode andArray, JsonNode context, String actionType, CatalogDiscoveryPolicyContext policyContext) {
        if (!andArray.isArray()) {
            return Result.failure("Invalid AND constraint: expected array");
        }

        if (andArray.isEmpty()) {
            return Result.failure("Empty AND constraint array");
        }

        for (JsonNode branch : andArray) {
            Result<Void> branchResult = evaluateConstraintTree(branch, context, actionType, policyContext);
            if (branchResult.failed()) {
                monitor.debug("AND constraint: branch failed, overall AND constraint fails");
                return branchResult;
            }
        }

        monitor.debug("AND constraint: all branches succeeded");
        return Result.success();
    }

    private Result<Void> evaluateAtomicConstraint(JsonNode constraintNode, JsonNode context, String actionType, CatalogDiscoveryPolicyContext policyContext) {
        JsonNode leftOperandNode = constraintNode.get(CATALOG_LEFT_OPERAND);
        JsonNode rightOperandNode = constraintNode.get(CATALOG_RIGHT_OPERAND);
        JsonNode operatorNode = constraintNode.get(CATALOG_OPERATOR);

        if (leftOperandNode == null || rightOperandNode == null || operatorNode == null) {
            return Result.failure("Malformed atomic constraint: missing leftOperand, rightOperand, or operator");
        }

        JsonNode leftOperandId = leftOperandNode.get(CATALOG_ID);
        JsonNode operatorId = operatorNode.get(CATALOG_ID);

        if (leftOperandId == null || operatorId == null) {
            return Result.failure("Malformed atomic constraint: missing @id in leftOperand or operator");
        }

        String leftOperand = leftOperandId.asText();
        String expandedLeftOperand = expandNamespace(leftOperand, context);

        String rightOperand = rightOperandNode.asText();
        String operatorStr = operatorId.asText();

        LiteralExpression leftExpression = new LiteralExpression(expandedLeftOperand);
        LiteralExpression rightExpression = new LiteralExpression(rightOperand);

        Operator operator = mapOperator(operatorStr);

        Permission perm = Permission.Builder.newInstance()
                .action(Action.Builder.newInstance()
                        .type(actionType)
                        .build())
                .constraint(AtomicConstraint.Builder.newInstance()
                        .leftExpression(leftExpression)
                        .operator(operator)
                        .rightExpression(rightExpression)
                        .build())
                .build();

        Policy policy = Policy.Builder.newInstance()
                .permission(perm)
                .build();

        Result<Void> evaluationResult = policyEngine.evaluate(policy, policyContext);

        if (evaluationResult.failed()) {
            monitor.debug(String.format("Atomic constraint failed: %s %s %s - %s",
                    expandedLeftOperand, operator, rightOperand,
                    String.join("; ", evaluationResult.getFailureMessages())));
        } else {
            monitor.debug(String.format("Atomic constraint succeeded: %s %s %s",
                    expandedLeftOperand, operator, rightOperand));
        }

        return evaluationResult;
    }

    private String expandNamespace(String value, JsonNode contextNode) {
        if (value.contains(":")) {
            String[] parts = value.split(":", 2);
            String prefix = parts[0];
            String localPart = parts[1];

            if (contextNode != null && contextNode.has(prefix)) {
                String namespace = contextNode.get(prefix).asText();
                return namespace + localPart;
            }
        }
        return value;
    }

    private Operator mapOperator(String operatorStr) {
        // Extract the operator name from the full URI or shorthand notation
        String operatorName = operatorStr.contains("/")
                ? operatorStr.substring(operatorStr.lastIndexOf("/") + 1)
                : operatorStr.contains(":")
                ? operatorStr.substring(operatorStr.lastIndexOf(":") + 1)
                : operatorStr;

        return switch (operatorName.toLowerCase()) {
            case "eq" -> Operator.EQ;
            case "neq" -> Operator.NEQ;
            case "gt" -> Operator.GT;
            case "gteq" -> Operator.GEQ;
            case "lt" -> Operator.LT;
            case "lteq" -> Operator.LEQ;
            case "ispartof" -> Operator.IN;
            case "haspart" -> Operator.HAS_PART;
            case "isa" -> Operator.IS_A;
            case "isallof" -> Operator.IS_ALL_OF;
            case "isanyof" -> Operator.IS_ANY_OF;
            case "isnoneof" -> Operator.IS_NONE_OF;
            default -> {
                monitor.warning(String.format("Unknown operator '%s', defaulting to EQ", operatorStr));
                yield Operator.EQ;
            }
        };
    }
}