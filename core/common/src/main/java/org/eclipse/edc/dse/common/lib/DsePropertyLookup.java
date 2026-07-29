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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generic {@link PropertyLookup} backed by {@link DseReflectionUtil}.
 *
 * <p>It resolves nested paths across arbitrary objects, maps, lists, and JSON-LD
 * {@code @value} wrappers. If a path cannot be resolved, {@code null} is returned so
 * the next registered {@link PropertyLookup} can be tried.
 */
public class DsePropertyLookup implements PropertyLookup {

    private static final Logger LOGGER = Logger.getLogger(DsePropertyLookup.class.getName());

    @Override
    public Object getProperty(String key, Object object) {
        try {
            return DseReflectionUtil.getFieldValue(key, object);
        } catch (ReflectionException e) {
            LOGGER.log(Level.FINE, "Could not resolve property ''{0}'' on object type {1}",
                    new Object[] { key, object.getClass().getName() });
            LOGGER.log(Level.FINER, "Property resolution failure details", e);
            return null;
        }
    }
}
