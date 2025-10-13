package org.eclipse.edc.issuerservice.defaults;

import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.query.QueryResolver;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.store.ReflectionBasedQueryResolver;
import org.eclipse.eonax.spi.issuerservice.MembershipAttestation;
import org.eclipse.eonax.spi.issuerservice.MembershipAttestationStore;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * An in-memory, threadsafe telemetry record store. This implementation is intended for testing purposes only.
 */
public class InMemoryMembershipAttestationStore implements MembershipAttestationStore {
    private final Map<String, MembershipAttestation> attestations = new ConcurrentHashMap<>();

    private final QueryResolver<MembershipAttestation> queryResolver;

    public InMemoryMembershipAttestationStore(CriterionOperatorRegistry criterionOperatorRegistry) {
        queryResolver = new ReflectionBasedQueryResolver<>(MembershipAttestation.class, criterionOperatorRegistry);
    }


    @Override
    public Stream<MembershipAttestation> query(QuerySpec querySpec) {
        return queryResolver.query(attestations.values().stream(), querySpec);
    }

    @Override
    public MembershipAttestation findById(String id) {
        return attestations.get(id);
    }

    @Override
    public StoreResult<Void> save(MembershipAttestation attestation) {
        if (attestations.get(attestation.id()) != null) {
            return StoreResult.alreadyExists("Attestation with id '%s' already exist".formatted(attestation.id()));
        }
        attestations.put(attestation.id(), attestation);
        return StoreResult.success();
    }

    @Override
    public StoreResult<Void> update(MembershipAttestation attestation) {
        if (attestations.get(attestation.id()) == null) {
            return StoreResult.notFound("Attestation with id '%s' does not exist".formatted(attestation.id()));
        }
        attestations.put(attestation.id(), attestation);
        return StoreResult.success();
    }

    @Override
    public StoreResult<MembershipAttestation> deleteById(String id) {
        return Optional.ofNullable(attestations.remove(id))
                .map(StoreResult::success)
                .orElse(StoreResult.notFound("Attestation with id '%s' does not exist".formatted(id)));
    }

}
