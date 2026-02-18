package org.eclipse.dse.identityhub;

import org.eclipse.edc.identityhub.spi.transformation.ScopeToCriterionTransformer;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.web.spi.WebService;

@Extension(value = IdentityHubIatpExtension.NAME)
public class IdentityHubIatpExtension implements ServiceExtension {

    public static final String NAME = "DSE IATP";

    @Inject
    private WebService webService;

    @Override
    public String name() {
        return NAME;
    }

    /**
     * Creates a {@link ScopeToCriterionTransformer} that transforms a scope string into a {@link org.eclipse.edc.spi.query.Criterion}.
     * The transformer is used to convert scopes from the Identity Hub API into criteria for querying
     * verifiable credentials.
     *
     * @return the {@link ScopeToCriterionTransformer}
     */
    @Provider
    public ScopeToCriterionTransformer scopeToCriterionTransformer() {
        return new DseScopeToCriterionTransformer();
    }

}