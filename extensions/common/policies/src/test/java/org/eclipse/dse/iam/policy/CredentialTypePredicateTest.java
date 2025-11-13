package org.eclipse.dse.iam.policy;

import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialTypePredicateTest {

    private static final String CREDENTIAL_TYPE = "MyCredential";

    @Test
    void typeMatch() {
        var credential = createCredential(CREDENTIAL_TYPE, "anotherType");

        var predicate = new CredentialTypePredicate(CREDENTIAL_TYPE);

        assertThat(predicate.test(credential)).isTrue();
    }

    @Test
    void noTypeMatch() {
        var credential = createCredential("aType", "anotherType");

        var predicate = new CredentialTypePredicate(CREDENTIAL_TYPE);

        assertThat(predicate.test(credential)).isFalse();
    }

    private static VerifiableCredential createCredential(String... types) {
        return VerifiableCredential.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .issuer(new Issuer("did:web:issuer"))
                .issuanceDate(Instant.now())
                .credentialSubject(CredentialSubject.Builder.newInstance()
                        .id("did:web:subject")
                        .claim("foo", "bar")
                        .build())
                .types(List.of(types))
                .build();
    }

}