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
 */

package org.eclipse.edc.dse.transform;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.dse.controlplane.category.whitelist.CategoryWhitelist;
import org.eclipse.edc.dse.controlplane.category.whitelist.WhitelistedCategory;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.protocol.dsp.catalog.transform.from.JsonObjectFromCatalogV2025Transformer;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VALUE;
import static org.eclipse.edc.protocol.dsp.spi.type.Dsp2025Constants.DSP_NAMESPACE_V_2025_1;

/**
 * Adds dcat:themeTaxonomy to the catalog JSON, on top of what the upstream transformer builds.
 * <p>
 * Upstream has no extension point, so we wrap a real instance of the DSP 2025-1 catalog
 * transformer ({@link JsonObjectFromCatalogV2025Transformer}, the one upstream registers for
 * the 2025-1 context): delegate for the normal output, then add the taxonomy on top.
 * <p>
 * The taxonomy only lists the whitelist categories that the catalog's assets use in
 * dcat:theme, plus their parents so the tree stays connected. A theme matches a category by its
 * {@code @id} (the category internalId) or by plain value (the category id); other themes are
 * ignored. If no asset uses a whitelisted category, the scheme is still added with no top
 * concepts. Assets are left as-is.
 */
public class DSECatalogTransformer extends AbstractJsonLdTransformer<Catalog, JsonObject> {

    private final JsonObjectFromCatalogV2025Transformer delegate;
    private final CategoryWhitelist categoryWhitelist;
    private final ThemeTaxonomyBuilder taxonomyBuilder = new ThemeTaxonomyBuilder();
    private final ThemeTaxonomyJsonLdWriter taxonomyWriter;

    public DSECatalogTransformer(JsonBuilderFactory jsonFactory, TypeManager typeManager, String typeContext,
                                  ParticipantIdMapper participantIdMapper, CategoryWhitelist categoryWhitelist) {
        super(Catalog.class, JsonObject.class);
        this.delegate = new JsonObjectFromCatalogV2025Transformer(jsonFactory, typeManager, typeContext, participantIdMapper,
                DSP_NAMESPACE_V_2025_1);
        this.categoryWhitelist = categoryWhitelist;
        this.taxonomyWriter = new ThemeTaxonomyJsonLdWriter(jsonFactory);
    }

    @Override
    public @Nullable JsonObject transform(@NotNull Catalog catalog, @NotNull TransformerContext context) {
        var jsonObject = delegate.transform(catalog, context);
        if (jsonObject == null) {
            return null;
        }

        var usedCategories = usedCategoriesWithParents(catalog);
        var scheme = taxonomyBuilder.build(usedCategories);

        return Json.createObjectBuilder(jsonObject)
                .add(ThemeTaxonomyConstants.DCAT_THEME_TAXONOMY_ATTRIBUTE, taxonomyWriter.toJsonLd(scheme))
                .build();
    }

    /**
     * Picks the categories that go into this catalog's dcat:themeTaxonomy: the ones its assets
     * use, plus the parents of those categories up to the top. Categories that no asset uses
     * are left out, even when their parent is in the tree. The result is in whitelist order,
     * so the tree comes out the same whatever order the assets are in.
     */
    private List<WhitelistedCategory> usedCategoriesWithParents(Catalog catalog) {
        // 1. Category ids the assets use (dcat:theme)
        var usedCategoryIds = catalog.getDatasets().stream()
                .flatMap(asset -> themeReferences(asset.getProperties().get(ThemeTaxonomyConstants.DCAT_THEME_ATTRIBUTE)).stream())
                .collect(Collectors.toSet());

        // 2. The matching whitelist categories, each with its parents up to the top
        var selectedIds = usedCategoryIds.stream()
                .map(this::findCategory)
                .flatMap(Optional::stream)
                .flatMap(this::selfAndParents)
                .map(WhitelistedCategory::id)
                .collect(Collectors.toSet());

        // 3. In whitelist order
        return categoryWhitelist.getAll().stream()
                .filter(category -> selectedIds.contains(category.id()))
                .toList();
    }

    /**
     * Finds the whitelist category for a category id used by an asset: either the category's
     * internalId or its plain id. Category ids that are not in the whitelist are ignored.
     */
    private Optional<WhitelistedCategory> findCategory(String categoryId) {
        return categoryWhitelist.findByInternalId(categoryId).or(() -> categoryWhitelist.findById(categoryId));
    }

    /**
     * The category itself, then its parent, its parent's parent, and so on up to the top.
     * Each category has at most one parent, so this is a single line up the tree.
     */
    private Stream<WhitelistedCategory> selfAndParents(WhitelistedCategory category) {
        return Stream.iterate(category, Objects::nonNull, this::parentOf);
    }

    private WhitelistedCategory parentOf(WhitelistedCategory category) {
        return category.parentId() == null ? null : categoryWhitelist.findById(category.parentId()).orElse(null);
    }

    /**
     * Reads one asset's category ids (dcat:theme). An asset can have one category id or
     * several. A category id is either a reference to a category or the category's plain id.
     * An asset with no category id, or with a value that refers to no category, returns nothing.
     */
    private static Set<String> themeReferences(Object theme) {
        var refs = new LinkedHashSet<String>();
        if (theme == null) {
            return refs;
        }
        if (theme instanceof Collection<?> values) {
            values.forEach(value -> refs.addAll(themeReferences(value)));
        } else if (theme instanceof Map<?, ?> map) {
            var ref = map.containsKey(ID) ? map.get(ID) : map.get(VALUE);
            if (ref != null) {
                refs.add(ref.toString());
            }
        } else {
            refs.add(theme.toString());
        }
        return refs;
    }
}
