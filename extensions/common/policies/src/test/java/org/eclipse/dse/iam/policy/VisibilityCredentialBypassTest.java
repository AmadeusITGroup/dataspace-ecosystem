package org.eclipse.dse.iam.policy;

import org.eclipse.edc.dse.common.DseNamespaceConfig;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyContextImpl;
import org.eclipse.edc.policy.model.Operator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.dse.iam.policy.AbstractDynamicCredentialConstraintFunction.VC_CLAIM;
import static org.eclipse.dse.iam.policy.PolicyConstants.VISIBILITY_CREDENTIAL_TYPE;
import static org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext.CATALOG_SCOPE;
import static org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext.NEGOTIATION_SCOPE;
import static org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext.TRANSFER_SCOPE;

class VisibilityCredentialBypassTest {

    private static final PolicyConstants POLICY_CONSTANTS = new PolicyConstants(new DseNamespaceConfig(
            "https://w3id.org/dse/v0.0.1/ns/",
            "dse-policy",
            "https://w3id.org/dse/policy/"
    ));

    @ParameterizedTest
    @ValueSource(strings = { CATALOG_SCOPE, CatalogDiscoveryPolicyContext.CATALOG_DISCOVERY_SCOPE })
    void shouldBypassExistingCredentialConstraintsForCatalogScopes(String scope) {
        var context = createPolicyContext(createVisibilityCredential("ALL"), scope);

        var membershipResult = new MembershipConstraintFunction<>(POLICY_CONSTANTS)
                .evaluate(POLICY_CONSTANTS.getDseMembershipConstraint(), Operator.EQ, "inactive", null, context);
        var genericClaimResult = new JsonPathCredentialConstraintFunction<>(POLICY_CONSTANTS)
                .evaluate(POLICY_CONSTANTS.getDseGenericClaimConstraint() + ".$.MissingCredential.claim", Operator.GT, null, null, context);
        var catalogDiscoveryResult = new CatalogDiscoveryConstraintFunction<>(POLICY_CONSTANTS)
                .evaluate(POLICY_CONSTANTS.getDseRestrictedCatalogDiscoveryConstraint() + ".$.MissingCredential.claim", Operator.GT, null, null, context);

        assertThat(membershipResult).isTrue();
        assertThat(genericClaimResult).isTrue();
        assertThat(catalogDiscoveryResult).isTrue();
        assertThat(context.getProblems()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { NEGOTIATION_SCOPE, TRANSFER_SCOPE })
    void shouldNotBypassCredentialConstraintsOutsideCatalogScopes(String scope) {
        var membershipContext = createPolicyContext(createVisibilityCredential("ALL"), scope);
        var genericClaimContext = createPolicyContext(createVisibilityCredential("ALL"), scope);
        var catalogDiscoveryContext = createPolicyContext(createVisibilityCredential("ALL"), scope);

        var membershipResult = new MembershipConstraintFunction<>(POLICY_CONSTANTS)
                .evaluate(POLICY_CONSTANTS.getDseMembershipConstraint(), Operator.EQ, "active", null, membershipContext);
        var genericClaimResult = new JsonPathCredentialConstraintFunction<>(POLICY_CONSTANTS)
                .evaluate(POLICY_CONSTANTS.getDseGenericClaimConstraint() + ".$.MissingCredential.claim", Operator.EQ, "expected", null, genericClaimContext);
        var catalogDiscoveryResult = new CatalogDiscoveryConstraintFunction<>(POLICY_CONSTANTS)
                .evaluate(POLICY_CONSTANTS.getDseRestrictedCatalogDiscoveryConstraint() + ".$.MissingCredential.claim", Operator.EQ, "expected", null, catalogDiscoveryContext);

        assertThat(membershipResult).isFalse();
        assertThat(genericClaimResult).isFalse();
        assertThat(catalogDiscoveryResult).isFalse();
    }

    @Test
    void shouldNotBypassCatalogConstraintsWithoutCredentials() {
        var context = new TestPolicyContext(emptyMap(), CATALOG_SCOPE);

        var result = new MembershipConstraintFunction<>(POLICY_CONSTANTS)
                .evaluate(POLICY_CONSTANTS.getDseMembershipConstraint(), Operator.EQ, "active", null, context);

        assertThat(result).isFalse();
    }

    @Test
    void shouldNotAcceptFutureVisibilityScopes() {
        var validator = new VisibilityCredentialValidator();

        assertThat(validator.hasAllVisibility(List.of(createVisibilityCredential("SUBSET")))).isFalse();
        assertThat(validator.hasAllVisibility(List.of(createVisibilityCredential("NONE")))).isFalse();
    }

    @Test
    void shouldNotAcceptLegacyGodCredential() {
        var credential = createCredential("GodCredential", Map.of("god", true));

        assertThat(new VisibilityCredentialValidator().hasAllVisibility(List.of(credential))).isFalse();
    }

    @Test
    void shouldNotAcceptVisibilityClaimOnAnotherCredentialType() {
        var credential = createCredential("MembershipCredential", Map.of("visibility_scope", "ALL"));

        assertThat(new VisibilityCredentialValidator().hasAllVisibility(List.of(credential))).isFalse();
    }

    @Test
    void shouldAcceptUriVisibilityCredentialAndClaim() {
        var credential = createCredential("https://w3id.org/dse/VisibilityCredential",
                Map.of("https://w3id.org/dse/visibility_scope", "ALL"));

        assertThat(new VisibilityCredentialValidator().hasAllVisibility(List.of(credential))).isTrue();
    }

    @Test
    void shouldAcceptFragmentVisibilityCredentialAndClaim() {
        var credential = createCredential("https://w3id.org/dse#VisibilityCredential",
                Map.of("https://w3id.org/dse#visibility_scope", "ALL"));

        assertThat(new VisibilityCredentialValidator().hasAllVisibility(List.of(credential))).isTrue();
    }

    @Test
    void shouldRejectUnrelatedClaimOnVisibilityCredential() {
        var credential = createCredential(VISIBILITY_CREDENTIAL_TYPE, Map.of("scope", "ALL"));

        assertThat(new VisibilityCredentialValidator().hasAllVisibility(List.of(credential))).isFalse();
    }

    private static TestPolicyContext createPolicyContext(VerifiableCredential credential, String scope) {
        return new TestPolicyContext(Map.of(VC_CLAIM, List.of(credential)), scope);
    }

    private static VerifiableCredential createVisibilityCredential(String visibilityScope) {
        return createCredential(VISIBILITY_CREDENTIAL_TYPE, Map.of("visibility_scope", visibilityScope));
    }

    private static VerifiableCredential createCredential(String type, Map<String, Object> claims) {
        return VerifiableCredential.Builder.newInstance()
                .type(type)
                .issuer(new Issuer("did:web:authority"))
                .issuanceDate(Instant.now())
                .credentialSubject(CredentialSubject.Builder.newInstance()
                        .claims(claims)
                        .id("did:web:subject")
                        .build())
                .build();
    }

    private static class TestPolicyContext extends PolicyContextImpl implements ParticipantAgentPolicyContext {

        private final ParticipantAgent participantAgent;
        private final String scope;

        TestPolicyContext(Map<String, Object> claims, String scope) {
            participantAgent = new ParticipantAgent("did:web:test-participant", claims, emptyMap());
            this.scope = scope;
        }

        @Override
        public ParticipantAgent participantAgent() {
            return participantAgent;
        }

        @Override
        public String scope() {
            return scope;
        }
    }
}