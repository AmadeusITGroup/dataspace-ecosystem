package org.eclipse.dse.spi.issuerservice;

import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;

import java.util.stream.Stream;

public interface VisibilityAttestationStore {

    String VISIBILITY_ATTESTATION_NOT_FOUND = "Visibility Attestation with ID %s could not be found";
    String VISIBILITY_ATTESTATION_ALREADY_EXISTS = "Visibility Attestation with ID %s already exists";

    Stream<VisibilityAttestation> query(QuerySpec querySpec);

    VisibilityAttestation findById(String id);

    StoreResult<Void> save(VisibilityAttestation attestation);

    StoreResult<Void> update(VisibilityAttestation attestation);

    StoreResult<VisibilityAttestation> deleteById(String id);
}