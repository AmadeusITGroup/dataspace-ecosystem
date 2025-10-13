package org.eclipse.edc.issuerservice;

import org.eclipse.edc.identityhub.spi.verifiablecredentials.store.CredentialStore;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

@Extension(value = FilteredCredentialStoreExtension.NAME)
public class FilteredCredentialStoreExtension implements ServiceExtension {


    @Inject
    private CredentialStore delegate;

    public static final String NAME = "FilteredCredentialStore";

    @Override
    public void initialize(ServiceExtensionContext context) {

    }

    @Provider(isDefault = false)
    public CredentialStore filteredCredentialStore(ServiceExtensionContext context) {

        return new FilteringCredentialStore(delegate, context.getMonitor());
    }

}