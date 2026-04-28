package org.eclipse.dse.spi.issuerservice;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MembershipAttestationTest {

    @Test
    void shouldNormalizeNullPropertiesToEmptyMap() {
        var attestation = new MembershipAttestation("id-1", "holder-1", "Test Corp", "FullMember", Instant.now(), null);

        assertThat(attestation.properties()).isNotNull().isEmpty();
    }

    @Test
    void shouldPreservePropertiesWhenProvided() {
        var props = Map.<String, Object>of("companySegment", "Airlines", "channel", "Direct");
        var attestation = new MembershipAttestation("id-1", "holder-1", "Test Corp", "FullMember", Instant.now(), props);

        assertThat(attestation.properties()).isEqualTo(props);
    }

    @Test
    void shouldDefensivelyCopyProperties() {
        var props = new HashMap<String, Object>();
        props.put("companySegment", "Airlines");

        var attestation = new MembershipAttestation("id-1", "holder-1", "Test Corp", "FullMember", Instant.now(), props);
        props.put("channel", "Direct");

        assertThat(attestation.properties())
                .containsEntry("companySegment", "Airlines")
                .doesNotContainKey("channel");
        assertThatThrownBy(() -> attestation.properties().put("newKey", "newValue"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
