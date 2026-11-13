/*
 *  Copyright (c) 2026 Amadeus IT Group
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
import org.eclipse.edc.util.reflection.PathItem;
import org.eclipse.edc.util.reflection.ReflectionException;
import org.eclipse.edc.util.reflection.ReflectionUtil;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Dataset-level field paths of a federated catalog query. The one place for the rules that the query resolution
 * ({@link DatasetAwareQueryResolver}, {@link DatasetComparator}) and the management v4 request validation
 * ({@link DatasetSortFieldValidator}) share, so a sort field is rejected by the same rule in both.
 *
 * <p>A path is resolved with {@link DseReflectionUtil#getFieldValue(String, Object)}: a {@link ReflectionException}
 * means that a field of the path does not exist. A missing {@code Map} key is not an error (the value is
 * {@code null}), e.g. {@code properties.'http://purl.org/dc/terms/title'} or {@code offers.<offer id>}.
 */
public final class DatasetFieldPaths {

    static final String DATASETS_PREFIX = "datasets.";
    // the indexer form of DseReflectionUtil: <field>[<index>]
    private static final Pattern ARRAY_INDEXER = Pattern.compile(".*\\[([0-9])+\\]");
    // sentinel returned by resolveSegmentType when a segment lands on an open map/object: any path below it is valid.
    private static final Type DYNAMIC = Object.class;

    private DatasetFieldPaths() {
    }

    /**
     * The dataset-level path of a query path: the query applies to datasets, so a leading {@code datasets.} is removed.
     *
     * @param queryPath a sort field or the left operand of a criterion
     * @return the path relative to a {@link Dataset}
     */
    public static String toDatasetPath(String queryPath) {
        return queryPath.startsWith(DATASETS_PREFIX)
                ? queryPath.substring(DATASETS_PREFIX.length())
                : queryPath;
    }

    /**
     * The value to sort the dataset by.
     *
     * @param datasetPath the dataset-level path (see {@link #toDatasetPath(String)})
     * @param dataset     the dataset
     * @return the value, {@code null} for a missing {@code Map} key
     * @throws IllegalArgumentException if a field of the path does not exist
     */
    public static Object sortValue(String datasetPath, Dataset dataset) {
        try {
            return DseReflectionUtil.getFieldValue(datasetPath, dataset);
        } catch (ReflectionException e) {
            throw new IllegalArgumentException(unknownSortFieldMessage(datasetPath), e);
        }
    }

    /**
     * Whether a dataset can have the path. The path is checked against the declared Java types, segment by segment,
     * with the rules of {@link DseReflectionUtil#getFieldValue(String, Object)}:
     * <ul>
     *   <li>a field is looked up with {@link ReflectionUtil#getFieldRecursive(Class, String)} and must be accessible;</li>
     *   <li>on a {@code List} the segment applies to every element, so it is checked on the element type
     *       (e.g. {@code distributions.format} is checked on {@code Distribution});</li>
     *   <li>an indexer ({@code field[0]}) needs a {@code List} field (the sort casts the value to {@code List});</li>
     *   <li>below a {@code Map} (keys) or an untyped value ({@code Object}) the path depends on the values and is
     *       allowed, e.g. {@code properties.'http://purl.org/dc/terms/title'}.</li>
     * </ul>
     * A list index that a dataset does not have (e.g. {@code distributions[3]}) also depends on the values.
     *
     * @param datasetPath the dataset-level path (see {@link #toDatasetPath(String)})
     * @return {@code false} if no dataset has the path
     */
    public static boolean existsInDataset(String datasetPath) {
        List<PathItem> path;
        try {
            path = PathItem.parse(datasetPath);
        } catch (RuntimeException e) {
            return false;
        }
        if (path.isEmpty()) {
            return false;
        }
        Type type = Dataset.class;
        for (var item : path) {
            type = resolveSegmentType(type, item.toString());
            if (type == DYNAMIC) {
                return true;
            }
            if (type == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resolves the declared type reached by following one path segment from {@code type}.
     *
     * @return the resolved type to continue from; {@link #DYNAMIC} when the segment lands on an open map/object
     *         (anything below is valid), or {@code null} when the segment does not exist / is not navigable.
     */
    private static Type resolveSegmentType(Type type, String segment) {
        var isIndexer = ARRAY_INDEXER.matcher(segment).matches();
        var name = isIndexer ? segment.substring(0, segment.indexOf('[')) : segment;
        if (name.isEmpty()) {
            return null;
        }
        var raw = rawClass(elementType(type));
        if (raw == Object.class || Map.class.isAssignableFrom(raw)) {
            return DYNAMIC;
        }
        var field = ReflectionUtil.getFieldRecursive(raw, name);
        if (field == null || !field.trySetAccessible()) {
            return null;
        }
        var fieldType = field.getGenericType();
        if (!isIndexer) {
            return fieldType;
        }
        var fieldClass = rawClass(fieldType);
        if (fieldClass != Object.class && !List.class.isAssignableFrom(fieldClass)) {
            return null;
        }
        return elementType(fieldType);
    }

    /**
     * The element type of a collection or array type (repeatedly, for nested collections), the type itself otherwise.
     */
    private static Type elementType(Type type) {
        var current = type;
        while (true) {
            var raw = rawClass(current);
            if (raw.isArray()) {
                current = raw.getComponentType();
            } else if (Collection.class.isAssignableFrom(raw)) {
                current = current instanceof ParameterizedType parameterized
                        ? parameterized.getActualTypeArguments()[0]
                        : Object.class;
            } else {
                return current;
            }
        }
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof WildcardType wildcard && wildcard.getUpperBounds().length == 1) {
            return rawClass(wildcard.getUpperBounds()[0]);
        }
        // type variables and anything else: untyped
        return Object.class;
    }

    /**
     * The message for a sort field that does not exist in a dataset.
     *
     * @param datasetPath the dataset-level path (see {@link #toDatasetPath(String)})
     * @return the message
     */
    public static String unknownSortFieldMessage(String datasetPath) {
        return "Cannot sort by '%s': field does not exist in Dataset".formatted(datasetPath);
    }
}
