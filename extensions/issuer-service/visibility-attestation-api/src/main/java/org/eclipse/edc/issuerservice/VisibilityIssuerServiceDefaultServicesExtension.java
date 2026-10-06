package org.eclipse.edc.issuerservice;

import org.eclipse.dse.spi.issuerservice.VisibilityAttestationStore;
import org.eclipse.edc.issuerservice.defaults.InMemoryVisibilityAttestationStore;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;

@Extension(value = VisibilityIssuerServiceDefaultServicesExtension.NAME)
public class VisibilityIssuerServiceDefaultServicesExtension implements ServiceExtension {

    public static final String NAME = "Visibility Attestation Default Services";

    @Inject
    private CriterionOperatorRegistry criterionOperatorRegistry;

    @Provider(isDefault = true)
    public VisibilityAttestationStore inMemoryVisibilityAttestationStore() {
        return new InMemoryVisibilityAttestationStore(criterionOperatorRegistry);
    }
}