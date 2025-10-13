package org.eclipse.edc.issuerservice.defaults;

import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.eonax.spi.issuerservice.MembershipAttestationStore;
import org.eclipse.eonax.spi.issuerservice.MembershipAttestationStoreTestBase;

class InMemoryMembershipAttestationStoreTest extends MembershipAttestationStoreTestBase {

    private final InMemoryMembershipAttestationStore store = new InMemoryMembershipAttestationStore(CriterionOperatorRegistryImpl.ofDefaults());

    @Override
    protected MembershipAttestationStore getStore() {
        return store;
    }
}