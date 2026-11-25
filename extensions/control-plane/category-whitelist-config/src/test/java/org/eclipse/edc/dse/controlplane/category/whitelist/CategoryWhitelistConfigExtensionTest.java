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

import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.spi.types.TypeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class CategoryWhitelistConfigExtensionTest {

    private static final String SETTING = "dse.category.whitelist.path";

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(TypeManager.class, new JacksonTypeManager());
    }

    @Test
    void name_isCorrect(CategoryWhitelistConfigExtension ext) {
        assertThat(ext.name()).isEqualTo(CategoryWhitelistConfigExtension.EXTENSION_NAME);
    }

    @Test
    void initialize_loadsFixtureAndRegistersService(CategoryWhitelistConfigExtension ext,
                                                    ServiceExtensionContext context) throws URISyntaxException {
        var path = Path.of(getClass().getClassLoader().getResource("whitelist.json").toURI()).toString();
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of(SETTING, path)));

        ext.initialize(context);

        var whitelist = context.getService(CategoryWhitelist.class);
        assertThat(whitelist).isNotNull();
        assertThat(whitelist.size()).isEqualTo(11);
    }

    @Test
    void initialize_missingFile_abortsBoot(CategoryWhitelistConfigExtension ext,
                                                   ServiceExtensionContext context) {
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of(
                SETTING, "/no/such/path/whitelist.json")));

        assertThatExceptionOfType(EdcException.class).isThrownBy(() -> ext.initialize(context));
    }

    @Test
    void initialize_invalidFile_abortsBoot(CategoryWhitelistConfigExtension ext,
                                                   ServiceExtensionContext context,
                                                   @TempDir Path dir) throws IOException {
        var file = dir.resolve("whitelist.json");
        Files.writeString(file, "{ \"categories\": [ { \"id\": \"A\", \"name\": \"A\", \"internalId\": \"urn:example:a\", " +
                "\"parentId\": \"A\" } ] }", StandardCharsets.UTF_8);
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of(SETTING, file.toString())));

        assertThatExceptionOfType(EdcException.class)
                .isThrownBy(() -> ext.initialize(context))
                .withMessageContaining("its own parent");
        assertThat(context.getService(CategoryWhitelist.class, true)).isNull();
    }
}
