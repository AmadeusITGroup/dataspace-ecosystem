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

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.dse.controlplane.category.whitelist.CategoryWhitelist;
import org.eclipse.edc.dse.controlplane.category.whitelist.WhitelistedCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset.EDC_ASSET_PROPERTIES;
import static org.eclipse.edc.dse.controlplane.category.validator.CategoryThemeValidator.DCAT_THEME;
import static org.eclipse.edc.dse.controlplane.category.validator.CategoryThemeValidator.DCAT_VERSION;
import static org.eclipse.edc.dse.controlplane.category.validator.CategoryThemeValidator.DCT_IS_VERSION_OF;
import static org.eclipse.edc.dse.controlplane.category.validator.CategoryThemeValidator.INVALID_ENTRY_MESSAGE;
import static org.eclipse.edc.dse.controlplane.category.validator.CategoryThemeValidator.MANDATORY_FIELD_MESSAGE;

class CategoryThemeValidatorTest {

    private static final String CATEGORY_ORDER =
            "urn:amadeus:platform:content-management:taxonomy:category:Order";
    private static final String SUBCATEGORY_OMS =
            "urn:amadeus:platform:content-management:taxonomy:category:Order:subcategory:Order_Management_System";
    private static final String CATEGORY_OFFER =
            "urn:amadeus:platform:content-management:taxonomy:category:Offer";
    private static final String UNKNOWN_CATEGORY =
            "urn:amadeus:platform:content-management:taxonomy:category:Nope";
    private static final String PARENT_ID = "urn:example:asset:parent";
    private static final String DCAT_HAS_VERSION = "http://www.w3.org/ns/dcat#hasVersion";

    private CategoryThemeValidator validator;

    @BeforeEach
    void setUp() {
        var whitelist = new CategoryWhitelist(List.of(
                new WhitelistedCategory("Order", "Order", "desc", CATEGORY_ORDER, null),
                new WhitelistedCategory("Order Management System", "Order Management System", "desc",
                        SUBCATEGORY_OMS, "Order"),
                new WhitelistedCategory("Offer", "Offer", "desc", CATEGORY_OFFER, null)));
        validator = new CategoryThemeValidator(whitelist);
    }

    @Test
    void succeeds_whenSingleWhitelistedCategory() {
        var asset = asset(themes(CATEGORY_ORDER));

        assertThat(validator.validate(asset).succeeded()).isTrue();
    }

    @Test
    void succeeds_whenWhitelistedSubcategory() {
        var asset = asset(themes(SUBCATEGORY_OMS));

        assertThat(validator.validate(asset).succeeded()).isTrue();
    }

    @Test
    void succeeds_whenSeveralThemesAllWhitelisted() {
        var asset = asset(themes(CATEGORY_ORDER, SUBCATEGORY_OMS, CATEGORY_OFFER));

        assertThat(validator.validate(asset).succeeded()).isTrue();
    }

    @Test
    void fails_whenOneInvalidAmongSeveral() {
        var asset = asset(themes(CATEGORY_ORDER, UNKNOWN_CATEGORY, CATEGORY_OFFER));

        var result = validator.validate(asset);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains(UNKNOWN_CATEGORY);
        // the valid ids are not named as offending
        assertThat(result.getFailureDetail()).doesNotContain(CATEGORY_OFFER);
    }

    @Test
    void fails_whenCategoryNotWhitelisted() {
        var asset = asset(themes(UNKNOWN_CATEGORY));

        var result = validator.validate(asset);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains(UNKNOWN_CATEGORY);
        assertThat(result.getFailureDetail()).doesNotContain(MANDATORY_FIELD_MESSAGE);
    }

    @Test
    void fails_withMandatoryMessage_whenNoThemeProperty() {
        var properties = Json.createObjectBuilder().add("dct:title", "no theme here");
        var asset = asset(properties.build());

        var result = validator.validate(asset);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains(MANDATORY_FIELD_MESSAGE);
    }

    @Test
    void fails_withMandatoryMessage_whenEmptyThemeArray() {
        var properties = Json.createObjectBuilder()
                .add(DCAT_THEME, Json.createArrayBuilder());
        var asset = asset(properties.build());

        var result = validator.validate(asset);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains(MANDATORY_FIELD_MESSAGE);
    }

    @Test
    void mandatoryAndNotWhitelisted_useDistinctMessages() {
        var missing = validator.validate(
                asset(Json.createObjectBuilder().add("dct:title", "x").build())).getFailureDetail();
        var notWhitelisted = validator.validate(asset(themes(UNKNOWN_CATEGORY))).getFailureDetail();

        assertThat(missing).isNotEqualTo(notWhitelisted);
        assertThat(missing).contains(MANDATORY_FIELD_MESSAGE);
        assertThat(notWhitelisted).doesNotContain(MANDATORY_FIELD_MESSAGE);
    }

    @Test
    void succeeds_whenNoPropertiesKey() {
        // EDC's own AssetValidator reports the mandatory 'properties' violation; this validator
        // must not throw, it just passes through.
        assertThat(validator.validate(Json.createObjectBuilder().build()).succeeded()).isTrue();
    }

    @Test
    void succeeds_whenPropertiesIsEmptyArray() {
        var asset = Json.createObjectBuilder()
                .add(EDC_ASSET_PROPERTIES, Json.createArrayBuilder())
                .build();
        assertThat(validator.validate(asset).succeeded()).isTrue();
    }

    @Test
    void handlesProperties_asPlainObject_notArray() {
        // Defensive: if properties arrives as a plain object rather than the expanded one-element
        // array, the validator still reads the theme from it.
        var asset = Json.createObjectBuilder()
                .add(EDC_ASSET_PROPERTIES, Json.createObjectBuilder()
                        .add(DCAT_THEME, Json.createArrayBuilder()
                                .add(Json.createObjectBuilder().add("@id", CATEGORY_ORDER))))
                .build();
        assertThat(validator.validate(asset).succeeded()).isTrue();
    }

    @Test
    void handlesTheme_asSingleObject_notArray() {
        var properties = Json.createObjectBuilder()
                .add(DCAT_THEME, Json.createObjectBuilder().add("@id", CATEGORY_ORDER));
        assertThat(validator.validate(asset(properties.build())).succeeded()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedEntries")
    void fails_withInvalidEntryMessage_whenAnEntryHasNoUsableId(String label, JsonValue malformed) {
        // A malformed entry is rejected even when a valid, whitelisted entry is present too.
        var properties = Json.createObjectBuilder()
                .add(DCAT_THEME, Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add("@id", CATEGORY_ORDER))
                        .add(malformed));
        var result = validator.validate(asset(properties.build()));

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains(INVALID_ENTRY_MESSAGE).doesNotContain(MANDATORY_FIELD_MESSAGE);
    }

    @Test
    void fails_withInvalidEntryMessage_whenOnlyMalformedEntries() {
        var properties = Json.createObjectBuilder()
                .add(DCAT_THEME, Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add("@value", "urn:example:category:Unknown")));
        var result = validator.validate(asset(properties.build()));

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains(INVALID_ENTRY_MESSAGE);
    }

    private static Stream<Arguments> malformedEntries() {
        return Stream.of(
                Arguments.of("@value literal", Json.createObjectBuilder().add("@value", "urn:example:category:Unknown").build()),
                Arguments.of("object without @id", Json.createObjectBuilder().add("dct:title", "no id here").build()),
                Arguments.of("blank @id", Json.createObjectBuilder().add("@id", "  ").build()),
                Arguments.of("non-string @id", Json.createObjectBuilder().add("@id", 42).build()),
                Arguments.of("string entry", Json.createValue("not-an-object")));
    }

    // --- parent / child (dct:isVersionOf) ---

    @Test
    void succeeds_whenChildHasNoTheme() {
        var properties = Json.createObjectBuilder()
                .add(DCT_IS_VERSION_OF, idArray(PARENT_ID));

        assertThat(validator.validate(asset(properties.build())).succeeded()).isTrue();
    }

    @Test
    void succeeds_whenChildHasNonWhitelistedTheme() {
        // Child validity (a child carries no category) is another validator's job, not this one.
        var properties = Json.createObjectBuilder()
                .add(DCT_IS_VERSION_OF, idArray(PARENT_ID))
                .add(DCAT_THEME, idArray(UNKNOWN_CATEGORY));

        assertThat(validator.validate(asset(properties.build())).succeeded()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonChildIsVersionOf")
    void fails_withMandatoryMessage_whenIsVersionOfHasNoUsableId(String label, JsonValue isVersionOf) {
        var properties = Json.createObjectBuilder().add(DCT_IS_VERSION_OF, isVersionOf);

        var result = validator.validate(asset(properties.build()));

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains(MANDATORY_FIELD_MESSAGE);
    }

    private static Stream<Arguments> nonChildIsVersionOf() {
        return Stream.of(
                Arguments.of("empty array", Json.createArrayBuilder().build()),
                Arguments.of("@value literal", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add("@value", PARENT_ID)).build()),
                Arguments.of("blank @id", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add("@id", "  ")).build()));
    }

    @Test
    void fails_withMandatoryMessage_whenParentHasOnlyHasVersion_andNoTheme() {
        var properties = Json.createObjectBuilder()
                .add(DCAT_HAS_VERSION, idArray(PARENT_ID + ":v1"));

        var result = validator.validate(asset(properties.build()));

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains(MANDATORY_FIELD_MESSAGE);
    }

    @Test
    void succeeds_whenParentHasHasVersion_andWhitelistedTheme() {
        var properties = Json.createObjectBuilder()
                .add(DCAT_HAS_VERSION, idArray(PARENT_ID + ":v1"))
                .add(DCAT_THEME, idArray(CATEGORY_ORDER));

        assertThat(validator.validate(asset(properties.build())).succeeded()).isTrue();
    }

    // --- child detected by dcat:version ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("childByDcatVersion")
    void succeeds_whenChildDetectedByDcatVersion(String label, JsonObject properties) {
        assertThat(validator.validate(asset(properties)).succeeded()).isTrue();
    }

    private static Stream<Arguments> childByDcatVersion() {
        return Stream.of(
                Arguments.of("no theme", Json.createObjectBuilder()
                        .add(DCAT_VERSION, valueArray("v1.0.0")).build()),
                Arguments.of("non-whitelisted theme", Json.createObjectBuilder()
                        .add(DCAT_VERSION, valueArray("v1.0.0"))
                        .add(DCAT_THEME, idArray(UNKNOWN_CATEGORY)).build()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonChildDcatVersion")
    void fails_withMandatoryMessage_whenDcatVersionHasNoValue(String label, JsonValue version) {
        var properties = Json.createObjectBuilder().add(DCAT_VERSION, version);

        var result = validator.validate(asset(properties.build()));

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains(MANDATORY_FIELD_MESSAGE);
    }

    private static Stream<Arguments> nonChildDcatVersion() {
        return Stream.of(
                Arguments.of("empty array", Json.createArrayBuilder().build()),
                Arguments.of("blank @value", valueArray("  ").build()));
    }

    @Test
    void fails_whenParentWithoutVersionMarkers_hasNonWhitelistedTheme() {
        var properties = Json.createObjectBuilder()
                .add(DCAT_THEME, idArray(UNKNOWN_CATEGORY));

        var result = validator.validate(asset(properties.build()));

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains(UNKNOWN_CATEGORY);
    }

    @Test
    void succeeds_whenPropertiesIsNotAnObjectOrArray() {
        // Defensive: a malformed properties value (scalar) is handled by EDC's AssetValidator;
        // this validator passes through without throwing.
        var asset = Json.createObjectBuilder()
                .add(EDC_ASSET_PROPERTIES, "garbage")
                .build();
        assertThat(validator.validate(asset).succeeded()).isTrue();
    }

    // --- helpers: build the expanded-JSON-LD shapes the validator receives ---

    /**
     * Expanded asset: {@code properties} is a one-element array wrapping the properties object.
     */
    private JsonObject asset(JsonObject properties) {
        return Json.createObjectBuilder()
                .add(EDC_ASSET_PROPERTIES, Json.createArrayBuilder().add(properties))
                .build();
    }

    /**
     * Expanded array of {@code {"@id": "<id>"}} objects.
     */
    private static JsonArrayBuilder idArray(String... ids) {
        JsonArrayBuilder array = Json.createArrayBuilder();
        for (var id : ids) {
            array.add(Json.createObjectBuilder().add("@id", id));
        }
        return array;
    }

    /**
     * Expanded array of {@code {"@value": "<value>"}} literals.
     */
    private static JsonArrayBuilder valueArray(String... values) {
        JsonArrayBuilder array = Json.createArrayBuilder();
        for (var value : values) {
            array.add(Json.createObjectBuilder().add("@value", value));
        }
        return array;
    }

    /**
     * Builds a {@code dcat:theme} array of {@code {"@id": "<urn>"}} objects (expanded form).
     */
    private JsonObject themes(String... ids) {
        JsonArrayBuilder themeArray = Json.createArrayBuilder();
        for (var id : ids) {
            themeArray.add(Json.createObjectBuilder().add("@id", id));
        }
        return Json.createObjectBuilder().add(DCAT_THEME, themeArray).build();
    }
}
