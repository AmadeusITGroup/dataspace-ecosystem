package org.eclipse.edc.issuerservice.defaults;

import org.eclipse.dse.spi.issuerservice.MembershipAttestationStore;
import org.eclipse.dse.spi.issuerservice.MembershipAttestationStoreTestBase;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;

class InMemoryMembershipAttestationStoreTest extends MembershipAttestationStoreTestBase {

    private final InMemoryMembershipAttestationStore store = new InMemoryMembershipAttestationStore(CriterionOperatorRegistryImpl.ofDefaults());

    @Override
    protected MembershipAttestationStore getStore() {
        return store;
    }
}