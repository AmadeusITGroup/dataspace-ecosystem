package org.eclipse.dse.spi.issuerservice;

import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;

import java.util.stream.Stream;

public interface MembershipAttestationStore {

    String MEMBERSHIP_ATTESTATION_NOT_FOUND = "Membership Attestation with ID %s could not be found";
    String MEMBERSHIP_ATTESTATION_ALREADY_EXISTS = "Membership Attestation with ID %s already exists";

    Stream<MembershipAttestation> query(QuerySpec querySpec);

    MembershipAttestation findById(String id);

    StoreResult<Void> save(MembershipAttestation attestation);

    StoreResult<Void> update(MembershipAttestation attestation);

    StoreResult<MembershipAttestation> deleteById(String id);

}
