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

import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.dse.common.lib.DseReflectionUtil;
import org.eclipse.edc.spi.query.SortOrder;
import org.eclipse.edc.util.reflection.ReflectionException;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;

/**
 * A {@link Comparator} for {@link Dataset} objects that resolves sort values via
 * {@link DseReflectionUtil}, supporting dot-notation paths, {@code Map} key lookups,
 * and JSON-LD {@code @value} unwrapping.
 *
 * <p>Null values are sorted last on ASC and first on DESC.
 */
public class DatasetComparator implements Comparator<Dataset>, Serializable {

    private static final long serialVersionUID = -3207223507781712017L;

    private final String path;
    private final SortOrder sortOrder;

    public DatasetComparator(String path, SortOrder sortOrder) {
        this.path = path;
        this.sortOrder = sortOrder;
    }

    @Override
    public int compare(Dataset d1, Dataset d2) {
        int result;
        try {
            Object v1 = DseReflectionUtil.getFieldValue(path, d1);
            Object v2 = DseReflectionUtil.getFieldValue(path, d2);
            result = compareValues(v1, v2);

        } catch (ReflectionException e) {
            throw new IllegalArgumentException(
                    "Cannot sort by '%s': field does not exist in Dataset".formatted(path), e);
        }

        if (sortOrder == SortOrder.DESC) {
            result = -result;
        }

        return result;
    }

    private int compareValues(Object v1, Object v2) {
        int result;
        if (v1 == null && v2 == null) {
            result = 0;
        } else if (v1 == null) {
            result = 1;
        } else if (v2 == null) {
            result = -1;
        } else {
            result = compareNonNull(v1, v2);
        }
        return result;
    }

    private int compareNonNull(Object v1, Object v2) {
        if (!(v1 instanceof Comparable<?> comparable)) {
            throw new IllegalArgumentException("Sort field '" + path + "' is not comparable");
        }

        try {
            return compareComparable(comparable, v2);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(
                    "Sort field '%s' has incompatible value types: %s vs %s"
                            .formatted(path, v1.getClass().getName(), v2.getClass().getName()), e);
        }
    }

    private int compareComparable(Comparable<?> left, Object right) {
        try {
            var compareTo = left.getClass().getMethod("compareTo", Object.class);
            return (int) compareTo.invoke(left, right);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalArgumentException("Sort field '" + path + "' is not comparable", e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof ClassCastException castException) {
                throw castException;
            }
            throw new IllegalArgumentException("Failed to compare values for sort field '" + path + "'", e);
        }
    }
}
