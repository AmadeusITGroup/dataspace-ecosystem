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

import io.restassured.specification.RequestSpecification;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.eclipse.edc.junit.extensions.RuntimePerClassExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.hamcrest.Matchers.containsString;

/**
 * Boots a real control plane and drives the management asset API, so the JSON-LD expansion and the
 * runtime registration of the validator are exercised end to end (not hand-built expanded JSON).
 */
class CategoryValidationApiTest {

    private static final String CATEGORY = "urn:example:category:TestCategory";
    private static final String SUBCATEGORY = "urn:example:category:TestCategory:subcategory:TestSubcategory";
    private static final String UNLISTED = "urn:example:category:Unlisted";

    private static Map<String, String> config(int managementPort) {
        var protocolPort = getFreePort();
        var config = new HashMap<String, String>();
        config.put("web.http.port", String.valueOf(getFreePort()));
        config.put("web.http.path", "/api");
        config.put("web.http.management.port", String.valueOf(managementPort));
        config.put("web.http.management.path", "/management");
        config.put("web.http.protocol.port", String.valueOf(protocolPort));
        config.put("web.http.protocol.path", "/protocol");
        config.put("web.http.control.port", String.valueOf(getFreePort()));
        config.put("web.http.control.path", "/control");
        config.put("web.http.version.port", String.valueOf(getFreePort()));
        config.put("web.http.version.path", "/version");
        config.put("edc.participant.id", "test-participant");
        config.put("edc.dsp.callback.address", "http://localhost:" + protocolPort + "/protocol");
        config.put("dse.category.whitelist.path", writeWhitelist());
        return config;
    }

    private static String writeWhitelist() {
        try {
            var file = Files.createTempFile("category-whitelist", ".json");
            file.toFile().deleteOnExit();
            Files.writeString(file, """
                    { "categories": [
                      { "id": "Test Category", "name": "Test Category", "internalId": "%s", "parentId": null },
                      { "id": "Test Subcategory", "name": "Test Subcategory", "internalId": "%s", "parentId": "Test Category" }
                    ] }
                    """.formatted(CATEGORY, SUBCATEGORY), StandardCharsets.UTF_8);
            return file.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonObject asset(String id, String... themeIds) {
        return asset(id, Json.createObjectBuilder(), themeIds);
    }

    /**
     * Child (version) asset: {@code dct:isVersionOf} points at the given parent id.
     */
    private static JsonObject child(String id, String parentId) {
        return asset(id, Json.createObjectBuilder()
                .add("dct:isVersionOf", Json.createObjectBuilder().add("@id", parentId)));
    }

    /**
     * Parent asset listing the given version ids in {@code dcat:hasVersion}.
     */
    private static JsonObject parent(String id, String versionId, String... themeIds) {
        return asset(id, Json.createObjectBuilder()
                .add("dcat:hasVersion", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add("@id", versionId))), themeIds);
    }

    private static JsonObject asset(String id, JsonObjectBuilder properties, String... themeIds) {
        properties.add("name", "asset " + id);
        if (themeIds.length > 0) {
            JsonArrayBuilder themes = Json.createArrayBuilder();
            for (var themeId : themeIds) {
                themes.add(Json.createObjectBuilder().add("@id", themeId));
            }
            properties.add("dcat:theme", themes);
        }
        return Json.createObjectBuilder()
                .add("@context", Json.createObjectBuilder()
                        .add("@vocab", "https://w3id.org/edc/v0.0.1/ns/")
                        .add("dcat", "http://www.w3.org/ns/dcat#")
                        .add("dct", "http://purl.org/dc/terms/"))
                .add("@id", id)
                .add("@type", "Asset")
                .add("properties", properties)
                .add("dataAddress", Json.createObjectBuilder().add("type", "HttpData").add("baseUrl", "http://example.com"))
                .build();
    }

    private static RequestSpecification management(int port) {
        return given().baseUri("http://localhost:" + port + "/management").contentType(JSON);
    }

    @Nested
    class WhitelistEnabled {

        private static final int MANAGEMENT_PORT = getFreePort();

        @RegisterExtension
        static RuntimePerClassExtension runtime = new RuntimePerClassExtension() {
            {
                setConfiguration(config(MANAGEMENT_PORT));
            }
        };

        @Test
        void create_whitelistedCategory_isStored() {
            var id = UUID.randomUUID().toString();

            management(MANAGEMENT_PORT).body(asset(id, CATEGORY, SUBCATEGORY).toString())
                    .post("/v3/assets").then().statusCode(200);

            management(MANAGEMENT_PORT).get("/v3/assets/" + id).then().statusCode(200);
        }

        @Test
        void create_unlistedCategory_isRejectedAndNotStored() {
            var id = UUID.randomUUID().toString();

            management(MANAGEMENT_PORT).body(asset(id, CATEGORY, UNLISTED).toString())
                    .post("/v3/assets").then().statusCode(400)
                    .body(containsString("Category not on the whitelist: " + UNLISTED));

            management(MANAGEMENT_PORT).get("/v3/assets/" + id).then().statusCode(404);
        }

        @Test
        void create_missingCategory_isRejectedAndNotStored() {
            var id = UUID.randomUUID().toString();

            management(MANAGEMENT_PORT).body(asset(id).toString())
                    .post("/v3/assets").then().statusCode(400)
                    .body(containsString(CategoryThemeValidator.MANDATORY_FIELD_MESSAGE));

            management(MANAGEMENT_PORT).get("/v3/assets/" + id).then().statusCode(404);
        }

        @Test
        void update_toUnlistedCategory_isRejectedAndAssetUnchanged() {
            var id = UUID.randomUUID().toString();
            management(MANAGEMENT_PORT).body(asset(id, CATEGORY).toString())
                    .post("/v3/assets").then().statusCode(200);
            var before = management(MANAGEMENT_PORT).get("/v3/assets/" + id).then().statusCode(200)
                    .extract().asString();

            management(MANAGEMENT_PORT).body(asset(id, UNLISTED).toString())
                    .put("/v3/assets").then().statusCode(400)
                    .body(containsString("Category not on the whitelist: " + UNLISTED));

            var after = management(MANAGEMENT_PORT).get("/v3/assets/" + id).then().statusCode(200)
                    .extract().asString();
            assertThat(after).isEqualTo(before).contains(CATEGORY).doesNotContain(UNLISTED);
        }

        @Test
        void create_childWithoutCategory_isStored() {
            var id = UUID.randomUUID().toString();

            management(MANAGEMENT_PORT).body(child(id, "urn:example:asset:parent").toString())
                    .post("/v3/assets").then().statusCode(200);

            management(MANAGEMENT_PORT).get("/v3/assets/" + id).then().statusCode(200);
        }

        @Test
        void update_childRemovingIsVersionOf_withoutCategory_isRejected() {
            var id = UUID.randomUUID().toString();
            management(MANAGEMENT_PORT).body(child(id, "urn:example:asset:parent").toString())
                    .post("/v3/assets").then().statusCode(200);

            management(MANAGEMENT_PORT).body(asset(id).toString())
                    .put("/v3/assets").then().statusCode(400)
                    .body(containsString(CategoryThemeValidator.MANDATORY_FIELD_MESSAGE));
        }

        @Test
        void create_parentWithHasVersionAndNoCategory_isRejectedAndNotStored() {
            var id = UUID.randomUUID().toString();

            management(MANAGEMENT_PORT).body(parent(id, "urn:example:asset:version").toString())
                    .post("/v3/assets").then().statusCode(400)
                    .body(containsString(CategoryThemeValidator.MANDATORY_FIELD_MESSAGE));

            management(MANAGEMENT_PORT).get("/v3/assets/" + id).then().statusCode(404);
        }
    }
}
