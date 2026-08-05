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

import org.eclipse.edc.spi.query.OperatorPredicate;

import java.util.List;
import java.util.Objects;

/**
 * {@link OperatorPredicate} for the {@code IN} operator.
 * <p>
 * Supports property values that are themselves lists: the predicate matches when at least one
 * element of the property value is present among the operand values. Scalar property values keep
 * the standard containment semantics.
 */
public final class DseInOperatorPredicate implements OperatorPredicate {

    private DseInOperatorPredicate() {
    }

    public static OperatorPredicate in() {
        return new DseInOperatorPredicate();
    }

    @Override
    public boolean test(Object property, Object operandRight) {
        if (!(operandRight instanceof Iterable<?> iterable)) {
            throw new IllegalArgumentException("Operators ['IN', 'NOT IN'] require the right-hand operand to be an "
                    + Iterable.class.getName() + " but was "
                    + (operandRight == null ? "null" : operandRight.getClass().getName()));
        }

        if (property instanceof List<?> list) {
            return list.stream().anyMatch(it -> contains(iterable, it));
        }

        return contains(iterable, property);
    }

    private static boolean contains(Iterable<?> iterable, Object value) {
        for (var candidate : iterable) {
            if (Objects.equals(candidate, value)) {
                return true;
            }
        }
        return false;
    }
}