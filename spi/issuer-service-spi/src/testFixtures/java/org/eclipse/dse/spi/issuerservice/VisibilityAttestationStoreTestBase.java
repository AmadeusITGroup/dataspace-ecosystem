package org.eclipse.dse.spi.issuerservice;

import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreFailure;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class VisibilityAttestationStoreTestBase {

    protected abstract VisibilityAttestationStore getStore();

    protected VisibilityAttestation getAttestation() {
        return getAttestation(UUID.randomUUID().toString());
    }

    protected VisibilityAttestation getAttestation(String id) {
        return new VisibilityAttestation(id, UUID.randomUUID().toString(), VisibilityScope.ALL);
    }

    @Nested
    class FindById {
        @Test
        void shouldFindEntityById() {
            var attestation = getAttestation();
            getStore().save(attestation);

            assertThat(getStore().findById(attestation.id())).isEqualTo(attestation);
        }

        @Test
        void shouldReturnNullWhenEntityDoesNotExist() {
            assertThat(getStore().findById("not-exist")).isNull();
        }
    }

    @Nested
    class Save {
        @Test
        void shouldSaveEachVisibilityScope() {
            for (var scope : VisibilityScope.values()) {
                var attestation = new VisibilityAttestation(UUID.randomUUID().toString(), UUID.randomUUID().toString(), scope);

                assertThat(getStore().save(attestation).succeeded()).isTrue();
                assertThat(getStore().findById(attestation.id())).isEqualTo(attestation);
            }
        }
    }

    @Nested
    class Update {
        @Test
        void shouldUpdateExistingEntity() {
            var attestation = getAttestation();
            getStore().save(attestation);
            var updated = new VisibilityAttestation(attestation.id(), UUID.randomUUID().toString(), VisibilityScope.SUBSET);

            assertThat(getStore().update(updated).succeeded()).isTrue();
            assertThat(getStore().findById(attestation.id())).isEqualTo(updated);
        }

        @Test
        void shouldReturnNotFoundWhenEntityDoesNotExist() {
            var result = getStore().update(getAttestation());

            assertThat(result.failed()).isTrue();
            assertThat(result.reason()).isEqualTo(StoreFailure.Reason.NOT_FOUND);
        }
    }

    @Nested
    class Query {
        @Test
        void shouldQueryByHolderId() {
            var attestation = getAttestation();
            getStore().save(attestation);
            getStore().save(getAttestation());
            var spec = QuerySpec.Builder.newInstance()
                    .filter(new Criterion("holderId", "=", attestation.holderId()))
                    .build();

            assertThat(getStore().query(spec)).containsExactly(attestation);
        }
    }

    @Nested
    class DeleteById {
        @Test
        @DisplayName("Delete a record that does not exist")
        void shouldReturnNotFoundWhenEntityDoesNotExist() {
            var result = getStore().deleteById("not-exist");

            assertThat(result).extracting(StoreResult::reason).isEqualTo(StoreFailure.Reason.NOT_FOUND);
        }

        @Test
        void shouldDeleteExistingEntity() {
            var attestation = getAttestation();
            getStore().save(attestation);

            var result = getStore().deleteById(attestation.id());

            assertThat(result.succeeded()).isTrue();
            assertThat(result.getContent()).isEqualTo(attestation);
            assertThat(getStore().findById(attestation.id())).isNull();
        }
    }
}