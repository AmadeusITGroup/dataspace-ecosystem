package org.eclipse.edc.issuerservice.defaults;

import org.eclipse.dse.spi.issuerservice.DomainAttestation;
import org.eclipse.dse.spi.issuerservice.DomainAttestationStore;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.query.QueryResolver;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.store.ReflectionBasedQueryResolver;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class InMemoryDomainAttestationStore implements DomainAttestationStore {

    private final Map<String, DomainAttestation> attestations = new ConcurrentHashMap<>();

    private final QueryResolver<DomainAttestation> queryResolver;

    public InMemoryDomainAttestationStore(CriterionOperatorRegistry criterionOperatorRegistry) {
        queryResolver = new ReflectionBasedQueryResolver<>(DomainAttestation.class, criterionOperatorRegistry);
    }


    @Override
    public Stream<DomainAttestation> query(QuerySpec querySpec) {
        return queryResolver.query(attestations.values().stream(), querySpec);
    }

    @Override
    public DomainAttestation findById(String id) {
        return attestations.get(id);
    }

    @Override
    public StoreResult<Void> save(DomainAttestation attestation) {
        var newAttestation = withGeneratedId(attestation);
        attestations.put(newAttestation.id(), newAttestation);
        return StoreResult.success();
    }

    private DomainAttestation withGeneratedId(DomainAttestation attestation) {
        return new DomainAttestation(UUID.randomUUID().toString(), attestation.holderId(), attestation.domain());
    }

    @Override
    public StoreResult<Void> update(DomainAttestation attestation) {
        if (attestations.get(attestation.id()) == null) {
            return StoreResult.notFound(DomainAttestationStore.ATTESTATION_NOT_FOUND.formatted(attestation.id()));
        }
        attestations.put(attestation.id(), attestation);
        return StoreResult.success();
    }

    @Override
    public StoreResult<DomainAttestation> deleteById(String id) {
        return Optional.ofNullable(attestations.remove(id))
                .map(StoreResult::success)
                .orElse(StoreResult.notFound(DomainAttestationStore.ATTESTATION_NOT_FOUND.formatted(id)));
    }
}
