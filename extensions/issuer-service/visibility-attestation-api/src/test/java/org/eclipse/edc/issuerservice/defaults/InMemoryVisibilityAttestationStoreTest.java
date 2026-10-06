package org.eclipse.edc.issuerservice.defaults;

import org.eclipse.dse.spi.issuerservice.VisibilityAttestation;
import org.eclipse.dse.spi.issuerservice.VisibilityAttestationStore;
import org.eclipse.dse.spi.issuerservice.VisibilityAttestationStoreTestBase;
import org.eclipse.dse.spi.issuerservice.VisibilityScope;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.spi.query.QuerySpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryVisibilityAttestationStoreTest extends VisibilityAttestationStoreTestBase {

    private final InMemoryVisibilityAttestationStore store = new InMemoryVisibilityAttestationStore(CriterionOperatorRegistryImpl.ofDefaults());

    @Override
    protected VisibilityAttestationStore getStore() {
        return store;
    }

    @Test
    void shouldGenerateIdWhenSavingWithoutOne() {
        assertThat(store.save(new VisibilityAttestation(null, "holder-id", VisibilityScope.ALL)).succeeded()).isTrue();

        try (var result = store.query(QuerySpec.Builder.newInstance().build())) {
            assertThat(result).singleElement().satisfies(attestation -> {
                assertThat(attestation.id()).isNotBlank();
                assertThat(attestation.holderId()).isEqualTo("holder-id");
            });
        }
    }

    @Test
    void shouldRejectDuplicateId() {
        var attestation = new VisibilityAttestation("attestation-id", "holder-id", VisibilityScope.ALL);
        assertThat(store.save(attestation).succeeded()).isTrue();

        assertThat(store.save(attestation).failed()).isTrue();
    }

    @Test
    void shouldRejectUpdateWithoutId() {
        var result = store.update(new VisibilityAttestation(null, "holder-id", VisibilityScope.ALL));

        assertThat(result.failed()).isTrue();
    }
}