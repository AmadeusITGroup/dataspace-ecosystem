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

package org.eclipse.edc.dse.controlplane.query.asset;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;

/**
 * Registers {@link DseAssetPropertyLookup} into the shared {@link CriterionOperatorRegistry} so
 * that asset property paths that traverse nested {@link java.util.Map}/{@link java.util.List}
 * structures (e.g. JSON-LD property graphs) are resolved correctly for contract definitions,
 * contract negotiations, and transfer processes that filter/query on asset properties.
 *
 * <p>{@link CriterionOperatorRegistry#registerPropertyLookup(org.eclipse.edc.spi.query.PropertyLookup)}
 * inserts new lookups at the head of the list, so this extension's lookup is tried before the
 * upstream EDC {@code AssetPropertyLookup} default (registered by EDC core's
 * {@code ControlPlaneDefaultServicesExtension}), falling through to it when it returns
 * {@code null}.
 */
@Extension(value = AssetPropertyLookupExtension.EXTENSION_NAME)
public class AssetPropertyLookupExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "DSE Asset Property Lookup";

    @Inject
    private CriterionOperatorRegistry criterionOperatorRegistry;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void prepare() {
        criterionOperatorRegistry.registerPropertyLookup(new DseAssetPropertyLookup());
    }
}
