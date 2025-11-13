package org.eclipse.dse.iam.policy;

import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;

import java.util.function.Predicate;

public class CredentialTypePredicate implements Predicate<VerifiableCredential> {
    private final String type;

    public CredentialTypePredicate(String type) {
        this.type = type;
    }

    @Override
    public boolean test(VerifiableCredential credential) {
        return credential.getType().stream().anyMatch(t -> t.endsWith(type));
    }
    
}
