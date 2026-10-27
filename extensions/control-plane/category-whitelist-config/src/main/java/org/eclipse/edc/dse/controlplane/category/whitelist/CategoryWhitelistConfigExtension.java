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

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

/**
 * Loads the category whitelist from the ConfigMap-backed file once at boot and registers
 * the parsed {@link CategoryWhitelist} as a runtime service.
 *
 * <p>Controlled by {@code dse.category.whitelist.enabled} (default {@code false}). When disabled no
 * file is read, no {@link CategoryWhitelist} service is registered, and the control plane boots
 * without category validation - the default, for deployments that do not use a whitelist.
 *
 * <p>When enabled it is fail-fast: if the file is missing, unreadable, or unparseable the loader
 * throws an {@code EdcException} from {@link #initialize(ServiceExtensionContext)}, aborting EDC
 * boot. This is deliberate - running with an empty whitelist would silently hide every dataset
 * downstream.
 *
 * <p>Because the service is registered only when enabled, consumers must inject
 * {@link CategoryWhitelist} with {@code @Inject(required = false)} and handle {@code null}.
 */
@Provides(CategoryWhitelist.class)
@Extension(value = CategoryWhitelistConfigExtension.EXTENSION_NAME)
public class CategoryWhitelistConfigExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "DSE Category Whitelist Config";

    public static final String DEFAULT_WHITELIST_PATH = "/etc/dse/category-whitelist/whitelist.json";

    @Setting(description = "Whether to load the category whitelist at boot. When false, no whitelist is " +
            "loaded, no CategoryWhitelist service is registered, and any category is accepted.",
            defaultValue = "false", type = "boolean")
    public static final String ENABLED_KEY = "dse.category.whitelist.enabled";

    @Setting(description = "Filesystem path to the shared category whitelist JSON file (ConfigMap-backed)",
            defaultValue = DEFAULT_WHITELIST_PATH)
    public static final String PATH_KEY = "dse.category.whitelist.path";

    @Inject
    private TypeManager typeManager;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();

        if (!context.getSetting(ENABLED_KEY, false)) {
            monitor.info("Category whitelist disabled (" + ENABLED_KEY +
                    "=false): no whitelist loaded, any category accepted");
            return;
        }

        var path = context.getSetting(PATH_KEY, DEFAULT_WHITELIST_PATH);

        monitor.info("Loading category whitelist from " + path);
        var whitelist = new CategoryWhitelistLoader(typeManager.getMapper()).load(path);
        monitor.info("Loaded category whitelist: " + whitelist.size() + " categories");

        context.registerService(CategoryWhitelist.class, whitelist);
    }
}
