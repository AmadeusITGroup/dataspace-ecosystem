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
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;

import static org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset.EDC_ASSET_TYPE;

/**
 * Registers a {@link CategoryThemeValidator} against {@code EDC_ASSET_TYPE}. The EDC management API
 * composes it with the built-in asset validator and runs both on asset CREATE and asset UPDATE
 * (both go through {@code BaseAssetApiController.validate(EDC_ASSET_TYPE, ...)}), so a
 * non-whitelisted or missing category is rejected with HTTP 400 before the asset is stored - no
 * fork of EDC core.
 *
 * <p>The {@link CategoryWhitelist} is provided by the {@code category-whitelist-config} extension and
 * is a required dependency: category validation cannot be switched off, and the control plane does
 * not boot without the whitelist file.
 */
@Extension(value = CategoryWhitelistValidatorExtension.EXTENSION_NAME)
public class CategoryWhitelistValidatorExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "DSE Category Whitelist Validator";

    @Inject
    private JsonObjectValidatorRegistry validatorRegistry;

    @Inject
    private CategoryWhitelist whitelist;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        validatorRegistry.register(EDC_ASSET_TYPE, new CategoryThemeValidator(whitelist));
        monitor.info("Registered category whitelist validator on asset create/update (" +
                whitelist.size() + " whitelisted categories)");
    }
}
