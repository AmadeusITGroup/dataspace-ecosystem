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
import org.eclipse.edc.catalog.spi.FederatedCatalogCache;
import org.eclipse.edc.dse.common.lib.DsePropertyLookup;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.util.concurrency.LockManager;

import java.util.concurrent.locks.ReentrantReadWriteLock;

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
 */
@Extension(value = DseFederatedCatalogCacheExtension.EXTENSION_NAME)
public class DseFederatedCatalogCacheExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "DSE Federated Catalog Cache";
    public static final String IN = "in";

    @Override
    public String name() {
        return EXTENSION_NAME;
    }
    @Provider
    public FederatedCatalogCache federatedCatalogCache() {
        var criterionOperatorRegistry = CriterionOperatorRegistryImpl.ofDefaults();
        criterionOperatorRegistry.registerPropertyLookup(new DsePropertyLookup());
        criterionOperatorRegistry.registerOperatorPredicate(IN, DseInOperatorPredicate.in());
        var lockManager = new LockManager(new ReentrantReadWriteLock());
        var queryResolver = new DatasetAwareQueryResolver(criterionOperatorRegistry);
        return new CustomFederatedCatalogCache(lockManager, queryResolver);
    }
}
