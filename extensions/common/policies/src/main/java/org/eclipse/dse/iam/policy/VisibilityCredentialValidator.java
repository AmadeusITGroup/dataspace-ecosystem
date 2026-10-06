package org.eclipse.dse.iam.policy;

import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;

import java.util.Collection;
import java.util.Map;

import static org.eclipse.dse.iam.policy.PolicyConstants.VISIBILITY_CREDENTIAL_TYPE;

final class VisibilityCredentialValidator {

    private static final String VISIBILITY_SCOPE_CLAIM = "visibility_scope";
    private static final String ALL = "ALL";

    boolean hasAllVisibility(Collection<VerifiableCredential> credentials) {
        return credentials.stream().anyMatch(this::hasAllVisibility);
    }

    private boolean hasAllVisibility(VerifiableCredential credential) {
        return hasVisibilityCredentialType(credential) && credential.getCredentialSubject().stream()
                .map(subject -> subject.getClaims())
                .anyMatch(this::hasAllVisibilityClaim);
    }

    private boolean hasVisibilityCredentialType(VerifiableCredential credential) {
        return credential.getType().stream().anyMatch(type -> type.equals(VISIBILITY_CREDENTIAL_TYPE) ||
                type.endsWith("/" + VISIBILITY_CREDENTIAL_TYPE) ||
                type.endsWith("#" + VISIBILITY_CREDENTIAL_TYPE));
    }

    private boolean hasAllVisibilityClaim(Map<String, Object> claims) {
        return claims.entrySet().stream()
                .anyMatch(entry -> isVisibilityScopeClaim(entry.getKey()) && ALL.equals(entry.getValue()));
    }

    private static boolean isVisibilityScopeClaim(String claimName) {
        return claimName.equals(VISIBILITY_SCOPE_CLAIM) ||
                claimName.endsWith("/" + VISIBILITY_SCOPE_CLAIM) ||
                claimName.endsWith("#" + VISIBILITY_SCOPE_CLAIM);
    }
}