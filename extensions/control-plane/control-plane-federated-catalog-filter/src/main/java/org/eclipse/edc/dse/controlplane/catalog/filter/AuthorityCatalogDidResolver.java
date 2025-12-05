package org.eclipse.edc.dse.controlplane.catalog.filter;

import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.spi.result.Result;


public class AuthorityCatalogDidResolver {


    private final DidResolverRegistry didResolverRegistry;
    private final String authorityDid;

    private static final String CATALOG_FILTER_URL = "FederatedCatalogFilterService";

    public AuthorityCatalogDidResolver(DidResolverRegistry didResolverRegistry, String authorityDid) {
        this.didResolverRegistry = didResolverRegistry;
        this.authorityDid = authorityDid;
    }

    public Result<String> fetchCatalogFilterUrl() {
        return didResolverRegistry.resolve(authorityDid)
                .compose(this::cataloglUrl);
    }

    private Result<String> cataloglUrl(DidDocument document) {
        return document.getService().stream()
                .filter(s -> s.getType().equals(CATALOG_FILTER_URL))
                .findFirst()
                .map(value -> Result.success(value.getServiceEndpoint()))
                .orElseGet(() -> Result.failure("Could not find service with type '%s' in DID document".formatted(CATALOG_FILTER_URL)));
    }

}

