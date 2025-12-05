package org.eclipse.edc.util;

import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.spi.result.Result;

public class AuthorityCatalogFilterDidResolver {

    private final DidResolverRegistry didResolverRegistry;
    private final String authorityDid;

    protected static final String FEDERATED_CATALOG_URL = "FederatedCatalogService";

    public AuthorityCatalogFilterDidResolver(DidResolverRegistry didResolverRegistry, String authorityDid) {
        this.didResolverRegistry = didResolverRegistry;
        this.authorityDid = authorityDid;
    }

    public Result<String> fetchCatalogFilterUrl() {
        return didResolverRegistry.resolve(authorityDid)
                .compose(this::cataloglUrl);
    }

    private Result<String> cataloglUrl(DidDocument document) {
        return document.getService().stream()
                .filter(s -> s.getType().equals(FEDERATED_CATALOG_URL))
                .findFirst()
                .map(value -> Result.success(value.getServiceEndpoint()))
                .orElseGet(() -> Result.failure("Could not find service with type '%s' in DID document".formatted(FEDERATED_CATALOG_URL)));
    }

}

