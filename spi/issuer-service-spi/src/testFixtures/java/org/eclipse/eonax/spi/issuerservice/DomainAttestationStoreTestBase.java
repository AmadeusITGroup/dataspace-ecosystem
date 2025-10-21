package org.eclipse.eonax.spi.issuerservice;

import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreFailure;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.spi.result.StoreFailure.Reason.NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class DomainAttestationStoreTestBase {

    protected abstract DomainAttestationStore getStore();

    protected DomainAttestation getAttestation() {
        return getAttestation(UUID.randomUUID().toString());
    }

    protected DomainAttestation getAttestation(String id) {
        return new DomainAttestation(id, UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    @Nested
    class FindById {
        @Test
        void shouldFindEntityById() {
            var attestation = getAttestation();
            getStore().save(attestation);

            var storedAttestationStream = getDomainAttestationStream(attestation);

            List<DomainAttestation> storedAttestationList = storedAttestationStream.toList();

            DomainAttestation storedDomainAttestation = storedAttestationList.get(0);

            var actual = getStore().findById(storedDomainAttestation.id());
            assertThat(storedDomainAttestation)
                    .usingRecursiveComparison()
                    .isEqualTo(actual);
        }

        @Test
        @DisplayName("Verify that null is returned when entity not found")
        void findById_notExist() {
            assertThat(getStore().findById("not-exist")).isNull();
        }
    }

    @Nested
    class Save {

        @Test
        void save_success() {
            var attestation = getAttestation();
            getStore().save(attestation);
            var storedAttestationStream = getDomainAttestationStream(attestation);

            List<DomainAttestation> storedAttestationList = storedAttestationStream.toList();

            DomainAttestation storedDomainAttestation = storedAttestationList.get(0);
            assertThat(getStore().findById(storedDomainAttestation.id()))
                    .usingRecursiveComparison()
                    .isEqualTo(storedDomainAttestation);
        }
    }

    @Nested
    class Update {

        @Test
        @DisplayName("Verify that an existing entity is updated instead")
        void update_exists_shouldUpdate() {
            var attestation = getAttestation();
            getStore().save(attestation);

            var storedAttestationStream = getDomainAttestationStream(attestation);

            List<DomainAttestation> storedAttestationList = storedAttestationStream.toList();

            var newAttestation = storedAttestationList.get(0);

            getStore().update(newAttestation);

            var actual = getStore().findById(newAttestation.id());
            assertThat(actual).isNotNull();
            assertThat(newAttestation.domain()).isEqualTo(actual.domain());
        }

        @Test
        @DisplayName("Verify not found is returned is trying to update attestation that does not exist")
        void update_doesNotExist_shouldUpdate() {
            var attestation = getAttestation();

            var result = getStore().update(attestation);

            assertThat(result.failed()).isTrue();
            assertThat(result.reason()).isEqualTo(StoreFailure.Reason.NOT_FOUND);
        }
    }

    @Nested
    class Query {
        @Test
        void query() {
            var attestation1 = getAttestation();
            var attestation2 = getAttestation();

            getStore().save(attestation1);
            getStore().save(attestation2);
            var spec = QuerySpec.Builder.newInstance()
                    .filter(new Criterion("holderId", "=", attestation1.holderId()))
                    .build();

            var attestationRetrieved = getStore().query(spec);

            assertThat(attestationRetrieved)
                    .isNotNull()
                    .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id")
                    .containsExactly(attestation1);
        }
    }

    @Nested
    class DeleteById {

        @Test
        @DisplayName("Delete a record that doesn't exist")
        void doesNotExist() {
            var recordDeleted = getStore().deleteById("id1");

            assertThat(recordDeleted).isNotNull().extracting(StoreResult::reason).isEqualTo(NOT_FOUND);
        }

        @Test
        @DisplayName("Delete a record that exists")
        void exists() {
            var attestation = getAttestation();
            getStore().save(attestation);

            var storedAttestationStream = getDomainAttestationStream(attestation);

            List<DomainAttestation> storedAttestationList = storedAttestationStream.toList();

            assertEquals(1, storedAttestationList.size());

            DomainAttestation domainAttestationToBeDeleted = storedAttestationList.get(0);

            var attestationDeleted = getStore().deleteById(domainAttestationToBeDeleted.id());

            assertThat(attestationDeleted).isNotNull().extracting(StoreResult::succeeded).isEqualTo(true);
            assertThat(attestationDeleted.getContent())
                    .usingRecursiveComparison()
                    .isEqualTo(domainAttestationToBeDeleted);
            assertThat(getStore().findById(domainAttestationToBeDeleted.id())).isNull();
        }
    }

    private Stream<DomainAttestation> getDomainAttestationStream(DomainAttestation attestation) {
        var spec = QuerySpec.Builder.newInstance()
                .filter(
                        List.of(
                                new Criterion("holderId", "=", attestation.holderId()),
                                new Criterion("domain", "=", attestation.domain())
                        ))
                .build();
        return getStore().query(spec);
    }
}
