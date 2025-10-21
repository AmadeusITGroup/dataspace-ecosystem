package org.eclipse.edc.issuerservice;


import org.eclipse.edc.issuerservice.defaults.InMemoryDomainAttestationStore;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.eonax.spi.issuerservice.DomainAttestationStore;


@Extension(value = DomainIssuerServiceDefaultServicesExtension.NAME)
public class DomainIssuerServiceDefaultServicesExtension implements ServiceExtension {
    public static final String NAME = "Domain Issuer Service Default Services";
    @Inject
    private CriterionOperatorRegistry criterionOperatorRegistry;

    @Override
    public String name() {
        return NAME;
    }

    @Provider(isDefault = true)
    public DomainAttestationStore attestationStore() {
        return new InMemoryDomainAttestationStore(criterionOperatorRegistry);
    }

}
