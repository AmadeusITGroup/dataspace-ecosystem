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

import org.eclipse.edc.dse.controlplane.category.whitelist.CategoryWhitelist;
import org.eclipse.edc.dse.controlplane.category.whitelist.WhitelistedCategory;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset.EDC_ASSET_TYPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(DependencyInjectionExtension.class)
class CategoryWhitelistValidatorExtensionTest {

    private final JsonObjectValidatorRegistry validatorRegistry = mock(JsonObjectValidatorRegistry.class);
    private final CategoryWhitelist whitelist = new CategoryWhitelist(List.of(
            new WhitelistedCategory("Order", "Order", "desc",
                    "urn:amadeus:platform:content-management:taxonomy:category:Order", null)));

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(JsonObjectValidatorRegistry.class, validatorRegistry);
        context.registerService(CategoryWhitelist.class, whitelist);
    }

    @Test
    void name_isCorrect(CategoryWhitelistValidatorExtension ext) {
        assertThat(ext.name()).isEqualTo(CategoryWhitelistValidatorExtension.EXTENSION_NAME);
    }

    @Test
    void initialize_registersValidatorForAssetType(CategoryWhitelistValidatorExtension ext,
                                                    ServiceExtensionContext context) {
        ext.initialize(context);

        verify(validatorRegistry).register(eq(EDC_ASSET_TYPE), any(CategoryThemeValidator.class));
    }
}
