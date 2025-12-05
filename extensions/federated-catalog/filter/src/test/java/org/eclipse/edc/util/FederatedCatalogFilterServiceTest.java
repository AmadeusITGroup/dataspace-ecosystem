package org.eclipse.edc.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.dse.iam.core.DefaultScopeExtractor;
import org.eclipse.dse.iam.core.RequestCatalogDiscoveryContext;
import org.eclipse.dse.iam.policy.CatalogDiscoveryConstraintFunction;
import org.eclipse.dse.iam.policy.CatalogDiscoveryPolicyContext;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.policy.engine.PolicyEngineImpl;
import org.eclipse.edc.policy.engine.RuleBindingRegistryImpl;
import org.eclipse.edc.policy.engine.ScopeFilter;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.policy.engine.validation.RuleValidator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.eclipse.dse.iam.policy.CatalogDiscoveryPolicyContext.CATALOG_DISCOVERY_SCOPE;
import static org.eclipse.dse.iam.policy.PolicyConstants.DOMAIN_CREDENTIAL_TYPE;
import static org.eclipse.dse.iam.policy.PolicyConstants.DSE_RESTRICTED_CATALOG_DISCOVERY_CONSTRAINT;
import static org.eclipse.dse.iam.policy.PolicyConstants.MEMBERSHIP_CREDENTIAL_TYPE;
import static org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext.NEGOTIATION_SCOPE;
import static org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext.TRANSFER_SCOPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_USE_ACTION_ATTRIBUTE;
import static org.eclipse.edc.util.IdentityServiceValidator.MEMBERSHIP_SCOPE;
import static org.eclipse.edc.util.IdentityServiceValidator.READ_DOMAIN_CREDENTIAL_SCOPE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class FederatedCatalogFilterServiceTest {

    private final AuthorityCatalogFilterDidResolver didResolverRegistry = mock();
    private static PolicyEngine policyEngine = null;
    private static RuleBindingRegistry registry = new RuleBindingRegistryImpl();
    private static final String ISSUER = "did:web:issuer";
    private static final String SUBJECT = "did:web:subject";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Monitor monitor = mock();
    private static final String PARTICIPANT_DID = "did:web:participant";
    private static final String CATALOG_REPLY = "/catalogReply.txt";

    @BeforeAll
    static void init() {
        policyEngine = new PolicyEngineImpl(new ScopeFilter(registry), new RuleValidator(registry));
        policyEngine.registerPostValidator(RequestCatalogDiscoveryContext.class, new DefaultScopeExtractor<>(Set.of(MEMBERSHIP_SCOPE, READ_DOMAIN_CREDENTIAL_SCOPE)));
        registry.bind(ODRL_USE_ACTION_ATTRIBUTE,  CATALOG_DISCOVERY_SCOPE);
        policyEngine.registerFunction(CatalogDiscoveryPolicyContext.class, Permission.class, new CatalogDiscoveryConstraintFunction<>());
        registry.dynamicBind(s -> s.startsWith(DSE_RESTRICTED_CATALOG_DISCOVERY_CONSTRAINT) ? Set.of(CATALOG_DISCOVERY_SCOPE, NEGOTIATION_SCOPE, TRANSFER_SCOPE) : Set.of());
    }

    @Test
    void filterCatalogSimplePolicies() throws IOException {
        var membershipVc = createVc(MEMBERSHIP_CREDENTIAL_TYPE, Map.of("hello", "world"));
        var domainVc = createVc(DOMAIN_CREDENTIAL_TYPE, Map.of("domain", "route"));
        var credentials = List.of(membershipVc, domainVc);
        ClaimToken tokens = ClaimToken.Builder.newInstance().claim("vc", credentials).build();
        JsonNode catalogNode = loadCatalogFromFile(CATALOG_REPLY);
        FederatedCatalogService service = new FederatedCatalogService(policyEngine, objectMapper, monitor, didResolverRegistry);
        List<JsonNode> result = service.filterCatalog(catalogNode, service.createContext(tokens), PARTICIPANT_DID);

        assertTrue(result.toString().contains("restricted-route-asset"));
        assertFalse(result.toString().contains("restricted-travel-asset"));
        assertTrue(result.toString().contains("visible-or-asset"));
        assertTrue(result.toString().contains("visible-restricted-asset"));
        assertFalse(result.toString().contains("restricted-and-asset"));
        assertTrue(result.toString().contains("visible-list-asset"));
        assertFalse(result.toString().contains("restricted-list-asset"));
    }

    private static VerifiableCredential createVc(String type, Map<String, Object> claims) {
        return VerifiableCredential.Builder.newInstance()
                .type(type)
                .issuer(new Issuer(ISSUER))
                .issuanceDate(Instant.now())
                .credentialSubject(CredentialSubject.Builder.newInstance().claims(claims).id(SUBJECT).build())
                .build();
    }

    private JsonNode loadCatalogFromFile(String resourcePath) throws IOException {
        var inputStream = getClass().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IllegalArgumentException("File not found: " + resourcePath);
        }

        return objectMapper.readTree(inputStream);
    }
}
