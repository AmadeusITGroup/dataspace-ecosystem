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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.spi.EdcException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads and parses the shared {@code whitelist.json} file into a {@link CategoryWhitelist}.
 *
 * <p>Fails fast: any missing, unreadable, empty, or malformed file raises an {@link EdcException},
 * so the control-plane runtime aborts boot rather than starting with an empty whitelist.
 */
public class CategoryWhitelistLoader {

    private final ObjectMapper objectMapper;

    public CategoryWhitelistLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Load the whitelist from the given file path.
     *
     * @param path absolute path to the ConfigMap-backed {@code whitelist.json} file
     * @return the parsed, in-memory whitelist
     * @throws EdcException if the file is missing, unreadable, or cannot be parsed into a non-empty whitelist
     */
    public CategoryWhitelist load(String path) {
        if (path == null || path.isBlank()) {
            throw new EdcException("Category whitelist path is not configured");
        }

        var file = Path.of(path);
        if (!Files.exists(file) || !Files.isReadable(file)) {
            throw new EdcException("Category whitelist file is missing or unreadable: " + path);
        }

        final WhitelistFile parsed;
        try {
            // Reject trailing content (a second document, junk after the object) without touching the
            // shared mapper's configuration.
            parsed = objectMapper.readerFor(WhitelistFile.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new EdcException("Failed to parse category whitelist file: " + path, e);
        }

        try {
            return new CategoryWhitelist(parsed == null ? null : parsed.categories());
        } catch (IllegalArgumentException e) {
            throw new EdcException("Category whitelist file " + e.getMessage() + ": " + path, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WhitelistFile(List<WhitelistedCategory> categories) {
    }
}
