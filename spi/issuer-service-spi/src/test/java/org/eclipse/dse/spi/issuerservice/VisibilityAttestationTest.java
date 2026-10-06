package org.eclipse.dse.spi.issuerservice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisibilityAttestationTest {

    @Test
    void shouldPreserveAttestationValues() {
        var attestation = new VisibilityAttestation("attestation-id", "holder-id", VisibilityScope.SUBSET);

        assertThat(attestation.id()).isEqualTo("attestation-id");
        assertThat(attestation.holderId()).isEqualTo("holder-id");
        assertThat(attestation.visibilityScope()).isEqualTo(VisibilityScope.SUBSET);
    }

    @Test
    void shouldExposeSupportedScopes() {
        assertThat(VisibilityScope.values())
                .containsExactly(VisibilityScope.ALL, VisibilityScope.SUBSET, VisibilityScope.NONE);
    }
}