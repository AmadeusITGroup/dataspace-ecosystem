package org.eclipse.edc.issuerservice.defaults;

import org.eclipse.dse.spi.issuerservice.DomainAttestationStore;
import org.eclipse.dse.spi.issuerservice.DomainAttestationStoreTestBase;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;

@ComponentTest
public class InMemoryDomainAttestationStoreTest extends DomainAttestationStoreTestBase {

    private final InMemoryDomainAttestationStore store = new InMemoryDomainAttestationStore(CriterionOperatorRegistryImpl.ofDefaults());

    @Override
    protected DomainAttestationStore getStore() {
        return store;
    }
}
