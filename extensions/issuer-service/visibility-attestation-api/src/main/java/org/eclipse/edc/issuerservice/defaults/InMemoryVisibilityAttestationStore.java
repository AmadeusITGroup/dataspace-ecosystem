package org.eclipse.edc.issuerservice.defaults;

import org.eclipse.dse.spi.issuerservice.VisibilityAttestation;
import org.eclipse.dse.spi.issuerservice.VisibilityAttestationStore;
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

public class InMemoryVisibilityAttestationStore implements VisibilityAttestationStore {

    private final Map<String, VisibilityAttestation> attestations = new ConcurrentHashMap<>();
    private final QueryResolver<VisibilityAttestation> queryResolver;

    public InMemoryVisibilityAttestationStore(CriterionOperatorRegistry criterionOperatorRegistry) {
        queryResolver = new ReflectionBasedQueryResolver<>(VisibilityAttestation.class, criterionOperatorRegistry);
    }

    @Override
    public Stream<VisibilityAttestation> query(QuerySpec querySpec) {
        return queryResolver.query(attestations.values().stream(), querySpec);
    }

    @Override
    public VisibilityAttestation findById(String id) {
        return attestations.get(id);
    }

    @Override
    public StoreResult<Void> save(VisibilityAttestation attestation) {
        var id = attestation.id() == null ? UUID.randomUUID().toString() : attestation.id();
        var stored = new VisibilityAttestation(id, attestation.holderId(), attestation.visibilityScope());
        return attestations.putIfAbsent(id, stored) == null
                ? StoreResult.success()
                : StoreResult.alreadyExists(VISIBILITY_ATTESTATION_ALREADY_EXISTS.formatted(id));
    }

    @Override
    public StoreResult<Void> update(VisibilityAttestation attestation) {
        if (attestation.id() == null || !attestations.containsKey(attestation.id())) {
            return StoreResult.notFound(VISIBILITY_ATTESTATION_NOT_FOUND.formatted(attestation.id()));
        }
        attestations.put(attestation.id(), attestation);
        return StoreResult.success();
    }

    @Override
    public StoreResult<VisibilityAttestation> deleteById(String id) {
        return Optional.ofNullable(attestations.remove(id))
                .map(StoreResult::success)
                .orElse(StoreResult.notFound(VISIBILITY_ATTESTATION_NOT_FOUND.formatted(id)));
    }
}