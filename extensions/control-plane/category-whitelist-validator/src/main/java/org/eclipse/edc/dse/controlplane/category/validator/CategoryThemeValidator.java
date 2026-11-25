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

package org.eclipse.edc.dse.controlplane.category.validator;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.eclipse.edc.dse.controlplane.category.whitelist.CategoryWhitelist;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.validator.spi.Validator;

import java.util.ArrayList;
import java.util.List;

import static org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset.EDC_ASSET_PROPERTIES;
import static org.eclipse.edc.validator.spi.Violation.violation;

/**
 * Validates that every {@code dcat:theme} category carried by an asset is present on the shared
 * {@link CategoryWhitelist}. Registered against {@code EDC_ASSET_TYPE} alongside EDC's built-in
 * asset validator, so it runs on the expanded JSON-LD asset BEFORE the asset is stored: a failure
 * is turned into an HTTP 400 by the management API, with no fork of EDC core.
 *
 * <p>Rules (see PSMP-9102). The whitelist applies to PARENT assets only:
 * <ul>
 *   <li>An asset is treated as a version (child) when it carries {@code dcat:version}
 *       ({@value #DCAT_VERSION}) or {@code dct:isVersionOf} ({@value #DCT_IS_VERSION_OF}). A child is
 *       exempt from the category check. The version value and the parent link are validated
 *       separately, not here. Concretely: {@code isVersionOf} with at least one non-blank string
 *       {@code @id}, or {@code dcat:version} with at least one non-blank value. Other asset validity
 *       (a child carries no category, no asset has both {@code hasVersion} and {@code isVersionOf})
 *       is likewise the job of a separate validator.</li>
 *   <li>Every other asset is a PARENT. An empty, {@code @value} literal or blank-{@code @id}
 *       {@code isVersionOf}, or an empty / blank {@code dcat:version}, does not make a child;
 *       {@code dcat:hasVersion} plays no role.</li>
 *   <li>Parent with no {@code dcat:theme} property, or an empty one: rejected with a distinct
 *       "mandatory field missing" message.</li>
 *   <li>Any theme entry that is not an object with a non-blank string {@code @id} (for example a
 *       {@code @value} literal or an object without {@code @id}): rejected with a distinct
 *       "invalid entry" message, so malformed category data never passes alongside a valid one.</li>
 *   <li>Any theme {@code @id} not on the whitelist: rejected with a message naming the offending
 *       id(s). Every id is checked independently - a listed parent does not approve a
 *       non-listed subcategory, because each entry is matched against the whitelist on its own.</li>
 * </ul>
 */
public class CategoryThemeValidator implements Validator<JsonObject> {

    /**
     * Expanded IRI of the {@code dcat:theme} property (DCAT namespace {@code http://www.w3.org/ns/dcat#}).
     */
    public static final String DCAT_THEME = "http://www.w3.org/ns/dcat#theme";

    /**
     * Expanded IRI of {@code dct:isVersionOf} (Dublin Core terms namespace), the link a child
     * (version) asset carries to its parent.
     */
    public static final String DCT_IS_VERSION_OF = "http://purl.org/dc/terms/isVersionOf";

    /**
     * Expanded IRI of {@code dcat:version} (DCAT namespace {@code http://www.w3.org/ns/dcat#}), the
     * version label a child (version) asset carries, e.g. {@code "v1.0.0"}.
     */
    public static final String DCAT_VERSION = "http://www.w3.org/ns/dcat#version";

    static final String MANDATORY_FIELD_MESSAGE = "Mandatory field missing: dcat:theme (asset category)";

    static final String INVALID_ENTRY_MESSAGE =
            "Invalid dcat:theme entry: every category must be an object with a non-blank @id";

    private final CategoryWhitelist whitelist;

    public CategoryThemeValidator(CategoryWhitelist whitelist) {
        this.whitelist = whitelist;
    }

    @Override
    public ValidationResult validate(JsonObject asset) {
        var properties = extractProperties(asset);
        if (properties == null) {
            // No properties object at all - EDC's own AssetValidator reports the mandatory
            // 'properties' violation; nothing for us to add here.
            return ValidationResult.success();
        }

        if (isChild(properties)) {
            // The whitelist applies to parent assets only. A child (dcat:version or isVersionOf) is
            // exempt; its version value and parent link are validated separately.
            return ValidationResult.success();
        }

        var themes = properties.get(DCAT_THEME);
        var entries = entriesOf(themes);

        if (entries.isEmpty()) {
            return ValidationResult.failure(violation(MANDATORY_FIELD_MESSAGE, DCAT_THEME));
        }

        var themeIds = new ArrayList<String>();
        for (var entry : entries) {
            var id = idOf(entry);
            if (id == null) {
                return ValidationResult.failure(violation(INVALID_ENTRY_MESSAGE, DCAT_THEME));
            }
            themeIds.add(id);
        }

        var notWhitelisted = new ArrayList<String>();
        for (var id : themeIds) {
            if (!whitelist.isWhitelisted(id)) {
                notWhitelisted.add(id);
            }
        }

        if (!notWhitelisted.isEmpty()) {
            var message = "Category not on the whitelist: " + String.join(", ", notWhitelisted);
            return ValidationResult.failure(violation(message, DCAT_THEME));
        }

        return ValidationResult.success();
    }

    /**
     * Pulls the single properties object out of the expanded asset. In expanded JSON-LD the
     * {@code properties} key holds a one-element array whose element is the properties object
     * (mirrors EDC's own {@code AssetValidator}).
     */
    private JsonObject extractProperties(JsonObject asset) {
        var value = asset.get(EDC_ASSET_PROPERTIES);
        if (value == null) {
            return null;
        }
        if (value.getValueType() == JsonValue.ValueType.ARRAY) {
            var array = value.asJsonArray();
            if (array.isEmpty() || array.get(0).getValueType() != JsonValue.ValueType.OBJECT) {
                return null;
            }
            return array.getJsonObject(0);
        }
        if (value.getValueType() == JsonValue.ValueType.OBJECT) {
            return value.asJsonObject();
        }
        return null;
    }

    /**
     * True when the asset is a child (version): it carries {@code dcat:version} or
     * {@code dct:isVersionOf}. Concretely, {@code dct:isVersionOf} holds at least one entry with a
     * non-blank string {@code @id}, or {@code dcat:version} holds at least one non-blank value. The
     * version value and the parent link are validated separately, not here.
     */
    private boolean isChild(JsonObject properties) {
        return entriesOf(properties.get(DCT_IS_VERSION_OF)).stream().anyMatch(entry -> idOf(entry) != null) ||
                entriesOf(properties.get(DCAT_VERSION)).stream().anyMatch(this::hasValue);
    }

    /**
     * True when an expanded entry carries a usable value: a non-blank {@code @value} (a non-string
     * {@code @value} such as a number counts as present), a non-blank {@code @id}, or a non-blank plain
     * string.
     */
    private boolean hasValue(JsonValue entry) {
        if (entry.getValueType() == JsonValue.ValueType.STRING) {
            return !((JsonString) entry).getString().isBlank();
        }
        if (entry.getValueType() != JsonValue.ValueType.OBJECT) {
            return false;
        }
        var value = entry.asJsonObject().get("@value");
        if (value == null) {
            return idOf(entry) != null;
        }
        if (value.getValueType() == JsonValue.ValueType.STRING) {
            return !((JsonString) value).getString().isBlank();
        }
        return value.getValueType() != JsonValue.ValueType.NULL;
    }

    /**
     * Returns the entries of an expanded property value. In expanded JSON-LD the value is an array; a
     * single object is accepted too. Any other shape yields no entries (treated as missing).
     */
    private List<JsonValue> entriesOf(JsonValue value) {
        if (value == null) {
            return List.of();
        }
        return switch (value.getValueType()) {
            case ARRAY -> List.copyOf(value.asJsonArray());
            case OBJECT -> List.of(value);
            default -> List.of();
        };
    }

    /**
     * Returns the non-blank string {@code @id} of an entry, or {@code null} if the entry is not
     * an object or has no usable {@code @id}.
     */
    private String idOf(JsonValue entry) {
        if (entry.getValueType() != JsonValue.ValueType.OBJECT) {
            return null;
        }
        var idValue = entry.asJsonObject().get("@id");
        if (idValue == null || idValue.getValueType() != JsonValue.ValueType.STRING) {
            return null;
        }
        var id = ((JsonString) idValue).getString();
        return id.isBlank() ? null : id;
    }
}
