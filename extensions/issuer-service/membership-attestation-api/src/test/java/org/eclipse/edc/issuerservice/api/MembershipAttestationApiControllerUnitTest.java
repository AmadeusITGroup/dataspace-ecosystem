package org.eclipse.edc.issuerservice.api;

import org.eclipse.dse.spi.issuerservice.MembershipAttestation;
import org.eclipse.dse.spi.issuerservice.MembershipAttestationStore;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MembershipAttestationApiControllerUnitTest {

    private final MembershipAttestationStore store = mock();
    private MembershipAttestationApiController controller;

    @BeforeEach
    void setUp() {
        controller = new MembershipAttestationApiController(store);
    }

    @Test
    void queryMembershipAttestations_withNullSpec() {
        var attestation = new MembershipAttestation("id-1", "holder-1", "Test", "FullMember", Instant.now(), Map.of("key", "val"));
        when(store.query(any(QuerySpec.class))).thenReturn(Stream.of(attestation));

        var result = controller.queryMembershipAttestations("ctx-1", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).properties()).containsEntry("key", "val");
    }

    @Test
    void queryMembershipAttestations_withSpec() {
        var attestation = new MembershipAttestation("id-1", "holder-1", "Test", "FullMember", Instant.now(), Map.of());
        var spec = QuerySpec.Builder.newInstance().build();
        when(store.query(spec)).thenReturn(Stream.of(attestation));

        var result = controller.queryMembershipAttestations("ctx-1", spec);

        assertThat(result).hasSize(1);
    }

    @Test
    void createMembershipAttestation_withProperties() {
        var dto = new MembershipAttestationDto("id-1", "Test", "holder-1", "FullMember", Map.of("segment", "Airlines"));
        when(store.save(any(MembershipAttestation.class))).thenReturn(StoreResult.success());

        controller.createMembershipAttestation("ctx-1", dto);

        verify(store).save(assertArg(a -> {
            assertThat(a.id()).isEqualTo("id-1");
            assertThat(a.properties()).containsEntry("segment", "Airlines");
        }));
    }

    @Test
    void createMembershipAttestation_withNullProperties() {
        var dto = new MembershipAttestationDto("id-2", "Test", "holder-1", "FullMember", null);
        when(store.save(any(MembershipAttestation.class))).thenReturn(StoreResult.success());

        controller.createMembershipAttestation("ctx-1", dto);

        verify(store).save(assertArg(a -> {
            assertThat(a.properties()).isNotNull().isEmpty();
        }));
    }

    @Test
    void updateMembershipAttestation_existingPreservesStartDate() {
        var existing = new MembershipAttestation("id-3", "holder-1", "Test", "FullMember", Instant.parse("2025-01-01T00:00:00Z"), Map.of());
        var dto = new MembershipAttestationDto("id-3", "Updated", "holder-1", "FullMember", Map.of("key", "val"));
        when(store.findById("id-3")).thenReturn(existing);
        when(store.update(any(MembershipAttestation.class))).thenReturn(StoreResult.success());

        controller.updateMembershipAttestation("ctx-1", dto);

        verify(store).update(assertArg(a -> {
            assertThat(a.membershipStartDate()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
            assertThat(a.properties()).containsEntry("key", "val");
        }));
    }

    @Test
    void updateMembershipAttestation_notFoundSetsNewStartDate() {
        var dto = new MembershipAttestationDto("id-4", "Test", "holder-1", "FullMember", Map.of());
        when(store.findById("id-4")).thenReturn(null);
        when(store.update(any(MembershipAttestation.class))).thenReturn(StoreResult.success());

        controller.updateMembershipAttestation("ctx-1", dto);

        verify(store).update(assertArg(a -> {
            assertThat(a.membershipStartDate()).isNotNull();
        }));
    }

    @Test
    void deleteMembershipAttestation() {
        when(store.deleteById("id-5")).thenReturn(StoreResult.success());

        controller.deleteMembershipAttestation("ctx-1", "id-5");

        verify(store).deleteById("id-5");
    }
}
