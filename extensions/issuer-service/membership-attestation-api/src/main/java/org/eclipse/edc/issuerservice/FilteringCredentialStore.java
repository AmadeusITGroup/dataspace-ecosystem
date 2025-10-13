package org.eclipse.edc.issuerservice;

import org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VerifiableCredentialResource;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.store.CredentialStore;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;

import java.util.Collection;

public class FilteringCredentialStore implements CredentialStore {

    private final CredentialStore delegate;

    private final Monitor monitor;

    public FilteringCredentialStore(CredentialStore delegate, Monitor monitor) {
        this.delegate = delegate;
        this.monitor = monitor;
    }

    @Override
    public StoreResult<Void> create(VerifiableCredentialResource credentialResource) {
        if (credentialResource.getVerifiableCredential().rawVc() == null || credentialResource.getVerifiableCredential().rawVc().isEmpty()) {
            return StoreResult.success();
        }
        return delegate.create(credentialResource);
    }

    @Override
    public StoreResult<Collection<VerifiableCredentialResource>> query(QuerySpec querySpec) {
        return delegate.query(querySpec);
    }

    @Override
    public StoreResult<Void> update(VerifiableCredentialResource credentialResource) {
        return delegate.update(credentialResource);
    }

    @Override
    public StoreResult<Void> deleteById(String id) {
        return delegate.deleteById(id);
    }

    @Override
    public StoreResult<VerifiableCredentialResource> findById(String credentialId) {
        return delegate.findById(credentialId);
    }
}

