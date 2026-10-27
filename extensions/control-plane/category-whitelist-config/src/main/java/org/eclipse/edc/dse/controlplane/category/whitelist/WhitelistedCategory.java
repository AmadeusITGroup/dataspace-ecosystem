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

/**
 * One entry of the shared category whitelist, as carried verbatim in the ConfigMap-backed
 * {@code whitelist.json} file. {@code parentId} is {@code null} for a top-level category.
 *
 * @param id         display identifier of the category
 * @param name       human-readable name
 * @param description free-text description
 * @param internalId stable platform URN carried explicitly in the file (never derived)
 * @param parentId   id of the parent category, or {@code null} for a top-level category
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhitelistedCategory(String id, String name, String description, String internalId, String parentId) {
}
