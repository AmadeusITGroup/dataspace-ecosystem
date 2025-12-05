package org.eclipse.dse.iam.core;

import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyScope;
import org.eclipse.edc.spi.iam.RequestContext;
import org.eclipse.edc.spi.iam.RequestScope;

public class RequestCatalogDiscoveryContext extends RequestPolicyContext {

    @PolicyScope
    public static final String CATALOGING_DISCOVERY_REQUEST_SCOPE = "request.catalog.discovery";

    public RequestCatalogDiscoveryContext(RequestContext requestContext, RequestScope.Builder requestScopeBuilder) {
        super(requestContext, requestScopeBuilder);
    }

    @Override
    public String scope() {
        return CATALOGING_DISCOVERY_REQUEST_SCOPE;
    }
}