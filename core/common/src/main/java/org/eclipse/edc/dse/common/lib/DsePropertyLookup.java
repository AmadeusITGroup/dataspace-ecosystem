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

package org.eclipse.edc.dse.common.lib;

import org.eclipse.edc.spi.query.PropertyLookup;
import org.eclipse.edc.util.reflection.ReflectionException;

/**
 * Generic {@link PropertyLookup} backed by {@link DseReflectionUtil}.
 *
 * <p>It resolves nested paths across arbitrary objects, maps, lists, and JSON-LD
 * {@code @value} wrappers. If a path cannot be resolved, {@code null} is returned so
 * the next registered {@link PropertyLookup} can be tried.
 */
public class DsePropertyLookup implements PropertyLookup {

    @Override
    public Object getProperty(String key, Object object) {
        try {
            return DseReflectionUtil.getFieldValue(key, object);
        } catch (ReflectionException e) {
            // normal fallback — next registered PropertyLookup will be tried
            return null;
        }
    }
}
