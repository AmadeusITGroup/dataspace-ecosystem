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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DseInOperatorPredicateTest {

    @Test
    @DisplayName("returns true when scalar property is contained in right operand")
    void test_scalarProperty_containedInOperand() {
        var predicate = DseInOperatorPredicate.in();

        var result = predicate.test("flights", List.of("cars", "flights", "trains"));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("returns false when scalar property is not contained in right operand")
    void test_scalarProperty_notContainedInOperand() {
        var predicate = DseInOperatorPredicate.in();

        var result = predicate.test("boats", List.of("cars", "flights", "trains"));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("returns true when list property has at least one matching element")
    void test_listProperty_oneElementMatches() {
        var predicate = DseInOperatorPredicate.in();

        var result = predicate.test(List.of("aviation", "rail"), List.of("space", "aviation"));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("returns false when list property has no matching element")
    void test_listProperty_noElementMatches() {
        var predicate = DseInOperatorPredicate.in();

        var result = predicate.test(List.of("aviation", "rail"), List.of("space", "marine"));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("supports null comparisons using Objects.equals semantics")
    void test_nullProperty_supported() {
        var predicate = DseInOperatorPredicate.in();

        var result = predicate.test(null, java.util.Arrays.asList("aviation", null));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("throws when right operand is not iterable")
    void test_nonIterableOperand_throws() {
        var predicate = DseInOperatorPredicate.in();

        assertThatThrownBy(() -> predicate.test("flights", "not-iterable"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("right-hand operand")
                .hasMessageContaining("java.lang.Iterable");
    }

    @Test
    @DisplayName("throws when right operand is null")
    void test_nullOperand_throws() {
        var predicate = DseInOperatorPredicate.in();

        assertThatThrownBy(() -> predicate.test("flights", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("but was null");
    }
}
