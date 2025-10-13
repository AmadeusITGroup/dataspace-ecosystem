package org.eclipse.edc.issuerservice;

import org.eclipse.edc.issuerservice.defaults.InMemoryMembershipAttestationStore;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.eonax.spi.issuerservice.MembershipAttestationStore;

@Extension(value = IssuerServiceDefaultServicesExtension.NAME)
public class IssuerServiceDefaultServicesExtension implements ServiceExtension {

    public static final String NAME = "Issuer Service Default Services";

    @Inject
    private CriterionOperatorRegistry criterionOperatorRegistry;

    @Override
    public String name() {
        return NAME;
    }

    @Provider(isDefault = true)
    public MembershipAttestationStore inMemoryMembershipAttestationStore() {
        return new InMemoryMembershipAttestationStore(criterionOperatorRegistry);
    }
}