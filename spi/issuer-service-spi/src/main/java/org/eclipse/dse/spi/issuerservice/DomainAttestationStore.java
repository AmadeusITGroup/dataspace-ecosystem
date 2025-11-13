package org.eclipse.dse.spi.issuerservice;

import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;

import java.util.stream.Stream;

public interface DomainAttestationStore {
    String ATTESTATION_NOT_FOUND = "Domain Attestation with ID %s could not be found";
    String ATTESTATION_ALREADY_EXISTS = "Domain Attestation already exists";

    Stream<DomainAttestation> query(QuerySpec querySpec);

    DomainAttestation findById(String id);

    StoreResult<Void> save(DomainAttestation attestation);

    StoreResult<Void> update(DomainAttestation attestation);

    StoreResult<DomainAttestation> deleteById(String id);
}
