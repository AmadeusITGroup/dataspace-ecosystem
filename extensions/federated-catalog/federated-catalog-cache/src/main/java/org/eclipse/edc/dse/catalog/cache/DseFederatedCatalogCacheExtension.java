/*
 *  Copyright (c) 2024 Amadeus IT Group
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus IT Group - initial API and implementation
 *
 */

package org.eclipse.edc.dse.catalog.cache;
import org.eclipse.edc.api.management.schema.ManagementApiJsonSchema;
import org.eclipse.edc.catalog.spi.FederatedCatalogCache;
import org.eclipse.edc.dse.common.lib.DsePropertyLookup;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.util.concurrency.LockManager;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.eclipse.edc.spi.query.QuerySpec.EDC_QUERY_SPEC_TYPE_TERM;

/**
 * EDC extension that overrides the default {@link FederatedCatalogCache} with
 * {@link CustomFederatedCatalogCache} backed by {@link DatasetAwareQueryResolver}.
 *
 * <p>Creates a dedicated {@link CriterionOperatorRegistry} instance via
 * {@link CriterionOperatorRegistryImpl#ofDefaults()}, then registers
 * {@link DsePropertyLookup} into that registry so dataset-level property paths are
 * resolved correctly during query execution.</p>
 *
 * <p>The {@link Provider} is non-default and unconditionally overrides
 * {@code InMemoryFederatedCatalogCache} from EDC core.</p>
 *
 * <p>It also registers {@link DatasetSortFieldValidator} for the management v4 {@code QuerySpec}
 * ({@code v4:QuerySpec}, {@link #QUERY_SPEC_V4_VALIDATOR_TYPE}), so a sort field that the datasets of this cache
 * cannot have is a {@code 400}. The registry composes the validators of a type, so any other validator of that type
 * still runs.</p>
 */
@Extension(value = DseFederatedCatalogCacheExtension.EXTENSION_NAME)
public class DseFederatedCatalogCacheExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "DSE Federated Catalog Cache";
    public static final String IN = "in";
    /**
     * The validator type the upstream management v4 JSON-LD interceptor validates a {@code QuerySpec} body with
     * ({@code <schema version>:<compact @type>}).
     */
    public static final String QUERY_SPEC_V4_VALIDATOR_TYPE = ManagementApiJsonSchema.V4.version() + ":" + EDC_QUERY_SPEC_TYPE_TERM;

    @Inject
    private JsonObjectValidatorRegistry validatorRegistry;

    @Inject
    private JsonLd jsonLd;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        validatorRegistry.register(QUERY_SPEC_V4_VALIDATOR_TYPE, new DatasetSortFieldValidator(jsonLd));
    }

    @Provider
    public FederatedCatalogCache federatedCatalogCache() {
        var criterionOperatorRegistry = CriterionOperatorRegistryImpl.ofDefaults();
        criterionOperatorRegistry.registerPropertyLookup(new DsePropertyLookup());
        // the right operand of IN is a list, as for the upstream IN operator (CriterionOperatorRegistryImpl.ofDefaults())
        criterionOperatorRegistry.registerOperator(IN, Iterable.class, DseInOperatorPredicate.in());
        var lockManager = new LockManager(new ReentrantReadWriteLock());
        var queryResolver = new DatasetAwareQueryResolver(criterionOperatorRegistry);
        return new CustomFederatedCatalogCache(lockManager, queryResolver);
    }
}
