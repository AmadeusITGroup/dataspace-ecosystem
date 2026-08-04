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

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.dse.common.lib.DsePropertyLookup;
import org.eclipse.edc.spi.query.PropertyLookup;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Map.entry;

/**
 * A property lookup implementation for {@link Asset} that first checks
 * the asset's properties and private properties before falling back to
 * the default property lookup.
 */
public class DseAssetPropertyLookup implements PropertyLookup {

    private static final List<Map.Entry<String, Function<Asset, Map<String, Object>>>> MAPPINGS = List.of(
            entry("%s", Asset::getProperties),
            entry("'%s'", Asset::getProperties),
            entry("%s", Asset::getPrivateProperties),
            entry("'%s'", Asset::getPrivateProperties));

    private final PropertyLookup fallbackPropertyLookup = new DsePropertyLookup();

    @Override
    public Object getProperty(String key, Object object) {
        if (object instanceof Asset asset) {
            for (var mapping : MAPPINGS) {
                var value = fallbackPropertyLookup.getProperty(mapping.getKey().formatted(key), mapping.getValue().apply(asset));
                if (value != null) {
                    return value;
                }
            }
            return fallbackPropertyLookup.getProperty(key, asset);
        }

        return null;
    }
}
