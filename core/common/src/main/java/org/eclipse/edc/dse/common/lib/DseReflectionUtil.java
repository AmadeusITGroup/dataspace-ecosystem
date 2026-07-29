/*
 *  Copyright (c) 2020 - 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus IT Group - JSON-LD support, list traversal
 *
 */

package org.eclipse.edc.dse.common.lib;

import org.eclipse.edc.util.reflection.PathItem;
import org.eclipse.edc.util.reflection.ReflectionException;
import org.eclipse.edc.util.reflection.ReflectionUtil;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class DseReflectionUtil {

    private static final Pattern ARRAY_INDEXER_PATTERN = Pattern.compile(".*\\[([0-9])+\\]");
    private static final String OPENING_BRACKET = "[";
    private static final String CLOSING_BRACKET = "]";

    private DseReflectionUtil() {
    }

    /**
     * Utility function to get value of a field from an object. For field names currently the dot notation and array
     * indexers are supported:
     * <pre>
     *     someObject.someValue
     *     someObject[2].someValue  // someObject must implement the List interface
     *     mapField.nestedKey       // navigates into Map entries by key
     * </pre>
     *
     * <p>Behavior varies by object type at each path segment:
     * <ul>
     *   <li><b>Map:</b> returns {@code null} if the key does not exist.</li>
     *   <li><b>List:</b> iterates all elements and collects results; returns an empty list if no element has the property.</li>
     *   <li><b>Object:</b> uses reflective field access; throws {@link ReflectionException} if the field does not exist.</li>
     * </ul>
     *
     * @param propertyName The name of the field or path expression
     * @param object       The object to extract the value from
     * @return The field's value, {@code null} for missing Map keys, or an empty list when no list elements match.
     * @throws ReflectionException if a field does not exist on a Java object (not Map/List) or cannot be accessed
     */
    public static <T> T getFieldValue(String propertyName, Object object) {
        Objects.requireNonNull(propertyName, "propertyName");
        Objects.requireNonNull(object, "object");

        var path = PathItem.parse(propertyName);
        return getFieldValue(path, object);
    }
    /**
     * Resolves a property value from an element within a list context.
     * If the property exists as a field in the element's class, it is retrieved reflectively.
     * If the element is a {@link Map}, the property name is used as a key lookup.
     * If the element is a {@link List}, the property is resolved recursively for each item.
     *
     * @param propertyName the name of the property to resolve
     * @param element      the element from which the value is extracted
     * @return the resolved value, or a list of resolved values if the element is a list
     */
    private static Object resolveElement(String propertyName, Object element) {
        if (element instanceof Map<?, ?> map) {
            return map.get(propertyName);
        } else if (element instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(innerIt -> getFieldValue(propertyName, innerIt))
                    .filter(Objects::nonNull)
                    .toList();
        } else {
            return getRecursiveValue(propertyName, element);
        }
    }

    /**
     * Flattens a result into a stream. If the result is a {@link Collection}, its elements are
     * streamed and unwrapped. Otherwise, a single-element stream is returned.
     *
     * @param result the object to flatten
     * @return a stream of unwrapped elements
     */
    private static Stream<?> flatten(Object result) {
        if (result instanceof Collection<?> c) {
            return c.stream().filter(Objects::nonNull).map(DseReflectionUtil::unwrapValueObject);
        }
        return Stream.of(result);
    }

    /**
     * Unwraps a JSON-LD value object. If the object is a single-entry {@link Map} containing
     * only an {@code @value} key, the associated value is returned. Otherwise, the object
     * is returned as-is.
     *
     * @param obj the object to unwrap
     * @return the unwrapped value if it is a JSON-LD value object, otherwise the original object
     */
    private static Object unwrapValueObject(Object obj) {
        if (obj instanceof Map<?, ?> map && map.size() == 1 && map.containsKey("@value")) {
            return map.get("@value");
        }
        return obj;
    }

    /**
     * Recursively resolves a field value by traversing the given path of property segments.
     * Supports dot notation for nested access, array indexers (e.g. {@code field[0]}),
     * {@link Map} key lookups, and {@link List} iteration.
     *
     * @param path   the parsed path segments to traverse
     * @param object the root object to start resolution from
     * @param <T>    the expected return type
     * @return the resolved value, or {@code null} if a nested segment is null
     * @throws IndexOutOfBoundsException if an array index is out of range
     */
    private static <T> T getFieldValue(List<PathItem> path, Object object) {
        var first = path.get(0);

        if (path.size() > 1) {
            return resolveNestedPath(first, path, object);
        } else if (ARRAY_INDEXER_PATTERN.matcher(first.toString()).matches()) {
            return resolveArrayIndexer(first.toString(), object);
        } else {
            return resolveSingleSegment(first.toString(), object);
        }
    }

    private static <T> T resolveNestedPath(PathItem first, List<PathItem> path, Object object) {
        var nested = getFieldValue(List.of(first), object);
        if (nested == null) {
            return null;
        }
        return getFieldValue(path.subList(1, path.size()), nested);
    }

    private static <T> T resolveArrayIndexer(String segment, Object object) {
        var openingBracketIx = segment.indexOf(OPENING_BRACKET);
        var closingBracketIx = segment.indexOf(CLOSING_BRACKET);
        var propName = segment.substring(0, openingBracketIx);
        var arrayIndex = Integer.parseInt(segment.substring(openingBracketIx + 1, closingBracketIx));
        var iterableObject = (List) getFieldValue("'%s'".formatted(propName), object);
        return (T) iterableObject.get(arrayIndex);
    }

    /**
     * Resolves a single path segment against the given object.
     * <ul>
     *   <li><b>Map:</b> returns the value for the key, or {@code null} if the key does not exist.</li>
     *   <li><b>List:</b> iterates all elements and collects non-null results. Elements where the property
     *       is missing or resolves to {@code null} are excluded from the result list.</li>
     *   <li><b>Object:</b> uses reflective field access.</li>
     * </ul>
     */
    private static <T> T resolveSingleSegment(String propertyName, Object object) {
        if (object instanceof Map<?, ?> map) {
            return (T) unwrapMapValue(map.get(propertyName));
        } else if (object instanceof List<?> list) {
            return (T) list.stream()
                    .filter(Objects::nonNull)
                    .flatMap(it -> Stream.ofNullable(resolveElement(propertyName, it)))
                    .flatMap(result -> flatten(result))
                    .toList();
        } else {
            return getRecursiveValue(propertyName, object);
        }
    }

    /**
     * Unwraps a value fetched from a direct {@link Map} key lookup. If the value is itself a
     * {@link List} (e.g. a JSON-LD array of {@code {"@value": ...}} maps), it is routed through
     * {@link #flatten(Object)} — the same list-aware unwrapping used when an intermediate path
     * segment resolves to a List — so that {@code @value} maps nested inside it are unwrapped too.
     * Otherwise, the value is unwrapped directly via {@link #unwrapValueObject(Object)}.
     *
     * @param rawValue the value fetched from the map, before unwrapping
     * @return the unwrapped value
     */
    private static Object unwrapMapValue(Object rawValue) {
        if (rawValue instanceof List<?>) {
            return flatten(rawValue).toList();
        }
        return unwrapValueObject(rawValue);
    }


    /**
     * Retrieves the value of a field by name from an object, searching through the class hierarchy.
     * The field is made accessible before reading its value.
     *
     * @param propertyName the name of the field to read
     * @param object       the object instance to read the field from
     * @param <T>          the expected return type
     * @return the field value
     * @throws ReflectionException if the field does not exist or cannot be accessed
     */
    private static <T> T getRecursiveValue(String propertyName, Object object) {
        var field = ReflectionUtil.getFieldRecursive(object.getClass(), propertyName);
        if (field == null) {
            throw new ReflectionException(propertyName);
        }
        if (!field.trySetAccessible()) {
            throw new ReflectionException("Unable to access field: " + propertyName);
        }
        try {
            return (T) field.get(object);
        } catch (IllegalAccessException e) {
            throw new ReflectionException(e);
        }
    }
}
