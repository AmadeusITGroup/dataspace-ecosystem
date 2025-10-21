package org.eclipse.edc.issuerservice.defaults;

import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.eonax.spi.issuerservice.DomainAttestationStore;
import org.eclipse.eonax.spi.issuerservice.DomainAttestationStoreTestBase;

@ComponentTest
public class InMemoryDomainAttestationStoreTest extends DomainAttestationStoreTestBase {

    private final InMemoryDomainAttestationStore store = new InMemoryDomainAttestationStore(CriterionOperatorRegistryImpl.ofDefaults());

    @Override
    protected DomainAttestationStore getStore() {
        return store;
    }
}
