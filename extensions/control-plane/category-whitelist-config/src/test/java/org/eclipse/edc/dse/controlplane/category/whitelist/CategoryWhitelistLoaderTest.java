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

package org.eclipse.edc.dse.controlplane.category.whitelist;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.spi.EdcException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class CategoryWhitelistLoaderTest {

    private final CategoryWhitelistLoader loader = new CategoryWhitelistLoader(new ObjectMapper());

    @Test
    void load_parsesFixture_elevenEntriesAndTree() throws URISyntaxException {
        var path = Path.of(getClass().getClassLoader().getResource("whitelist.json").toURI()).toString();

        var whitelist = loader.load(path);

        // 7 top-level + 4 subcategories = 11 entries, order preserved.
        assertThat(whitelist.size()).isEqualTo(11);
        assertThat(whitelist.getAll())
                .extracting(WhitelistedCategory::id)
                .containsExactly(
                        "Test Category One", "Test Category Two", "Test Category Three",
                        "Test Category Four", "Test Category Five", "Test Parent", "Test Other",
                        "Test Subcategory", "Test Grandchild", "Test Other Child", "Test Extra Child");

        // Top-level categories carry a null parentId.
        assertThat(whitelist.findById("Test Parent")).get()
                .satisfies(c -> assertThat(c.parentId()).isNull());

        // Parent/child tree: the 3-level Test Parent -> Test Subcategory -> Test Grandchild chain.
        assertThat(whitelist.findById("Test Subcategory")).get()
                .satisfies(c -> assertThat(c.parentId()).isEqualTo("Test Parent"));
        assertThat(whitelist.findById("Test Grandchild")).get()
                .satisfies(c -> assertThat(c.parentId()).isEqualTo("Test Subcategory"));
        assertThat(whitelist.findById("Test Other Child")).get()
                .satisfies(c -> assertThat(c.parentId()).isEqualTo("Test Other"));

        // internalId carried explicitly (root-based scheme) and indexed.
        assertThat(whitelist.findByInternalId(
                "urn:example:category:TestParent:subcategory:TestGrandchild"))
                .get().satisfies(c -> assertThat(c.id()).isEqualTo("Test Grandchild"));
        assertThat(whitelist.isWhitelisted(
                "urn:example:category:TestCategoryOne")).isTrue();
        assertThat(whitelist.isWhitelisted("urn:not:in:the:list")).isFalse();
    }

    @Test
    void load_missingFile_throwsFailFast(@TempDir Path dir) {
        var missing = dir.resolve("does-not-exist.json").toString();

        assertThatExceptionOfType(EdcException.class)
                .isThrownBy(() -> loader.load(missing))
                .withMessageContaining("missing or unreadable");
    }

    @Test
    void load_blankPath_throwsFailFast() {
        assertThatExceptionOfType(EdcException.class)
                .isThrownBy(() -> loader.load("  "))
                .withMessageContaining("not configured");
    }

    @Test
    void load_malformedJson_throwsFailFast(@TempDir Path dir) throws IOException {
        var file = dir.resolve("bad.json");
        Files.writeString(file, "{ this is not json", StandardCharsets.UTF_8);

        assertThatExceptionOfType(EdcException.class)
                .isThrownBy(() -> loader.load(file.toString()))
                .withMessageContaining("Failed to parse");
    }

    @Test
    void load_emptyCategories_throwsFailFast(@TempDir Path dir) throws IOException {
        var file = dir.resolve("empty.json");
        Files.writeString(file, "{ \"categories\": [] }", StandardCharsets.UTF_8);

        assertThatExceptionOfType(EdcException.class)
                .isThrownBy(() -> loader.load(file.toString()))
                .withMessageContaining("no categories");
    }

    @Test
    void load_entryMissingInternalId_throwsFailFast(@TempDir Path dir) throws IOException {
        var file = dir.resolve("no-internal-id.json");
        Files.writeString(file,
                "{ \"categories\": [ { \"id\": \"X\", \"name\": \"X\", \"description\": \"d\", \"parentId\": null } ] }",
                StandardCharsets.UTF_8);

        assertThatExceptionOfType(EdcException.class)
                .isThrownBy(() -> loader.load(file.toString()))
                .withMessageContaining("internalId");
    }

    @Test
    void load_duplicateId_throwsFailFast(@TempDir Path dir) throws IOException {
        var file = writeCategories(dir, entry("A", "urn:example:a", null) + "," + entry("A", "urn:example:b", null));

        assertThatExceptionOfType(EdcException.class)
                .isThrownBy(() -> loader.load(file))
                .withMessageContaining("duplicate id 'A'");
    }

    @Test
    void load_duplicateInternalId_throwsFailFast(@TempDir Path dir) throws IOException {
        var file = writeCategories(dir, entry("A", "urn:example:same", null) + "," + entry("B", "urn:example:same", null));

        assertThatExceptionOfType(EdcException.class)
                .isThrownBy(() -> loader.load(file))
                .withMessageContaining("duplicate internalId 'urn:example:same'");
    }

    @Test
    void load_unknownParentId_throwsFailFast(@TempDir Path dir) throws IOException {
        var file = writeCategories(dir, entry("A", "urn:example:a", null) + "," + entry("B", "urn:example:b", "Missing"));

        assertThatExceptionOfType(EdcException.class)
                .isThrownBy(() -> loader.load(file))
                .withMessageContaining("unknown parentId 'Missing'");
    }

    @Test
    void load_selfParent_throwsFailFast(@TempDir Path dir) throws IOException {
        var file = writeCategories(dir, entry("A", "urn:example:a", "A"));

        assertThatExceptionOfType(EdcException.class)
                .isThrownBy(() -> loader.load(file))
                .withMessageContaining("'A' is its own parent");
    }

    @Test
    void load_parentCycle_throwsFailFast(@TempDir Path dir) throws IOException {
        var file = writeCategories(dir, entry("Root", "urn:example:root", null) + "," +
                entry("A", "urn:example:a", "B") + "," + entry("B", "urn:example:b", "A"));

        assertThatExceptionOfType(EdcException.class)
                .isThrownBy(() -> loader.load(file))
                .withMessageContaining("parentId cycle");
    }

    @Test
    void load_nullEntry_throwsFailFastNotNpe(@TempDir Path dir) throws IOException {
        var file = writeCategories(dir, entry("A", "urn:example:a", null) + ", null");

        assertThatExceptionOfType(EdcException.class)
                .isThrownBy(() -> loader.load(file))
                .withMessageContaining("null entry at index 1");
    }

    @Test
    void load_blankId_throwsFailFast(@TempDir Path dir) throws IOException {
        var file = writeCategories(dir, entry("  ", "urn:example:a", null));

        assertThatExceptionOfType(EdcException.class)
                .isThrownBy(() -> loader.load(file))
                .withMessageContaining("no id at index 0");
    }

    @Test
    void load_trailingContent_throwsFailFast(@TempDir Path dir) throws IOException {
        var file = dir.resolve("whitelist.json");
        var valid = "{ \"categories\": [ " + entry("A", "urn:example:a", null) + " ] }";
        Files.writeString(file, valid + "\n" + valid, StandardCharsets.UTF_8);

        assertThatExceptionOfType(EdcException.class)
                .isThrownBy(() -> loader.load(file.toString()))
                .withMessageContaining("Failed to parse");
    }

    @Test
    void constructor_validatesInputWithoutLoader() {
        var a = new WhitelistedCategory("A", "A", null, "urn:example:a", null);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new CategoryWhitelist(List.of(a, a)))
                .withMessageContaining("duplicate id 'A'");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new CategoryWhitelist(List.of()))
                .withMessageContaining("no categories");
    }

    private static String entry(String id, String internalId, String parentId) {
        var parent = parentId == null ? "null" : "\"" + parentId + "\"";
        return "{ \"id\": \"" + id + "\", \"name\": \"" + id + "\", \"internalId\": \"" + internalId +
                "\", \"parentId\": " + parent + " }";
    }

    private static String writeCategories(Path dir, String entries) throws IOException {
        var file = dir.resolve("whitelist.json");
        Files.writeString(file, "{ \"categories\": [ " + entries + " ] }", StandardCharsets.UTF_8);
        return file.toString();
    }
}
