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
 * <p>The whitelist is always loaded: there is no switch to turn category validation off. It is
 * fail-fast: if the file is missing, unreadable, or unparseable the loader throws an
 * {@code EdcException} from {@link #initialize(ServiceExtensionContext)}, aborting EDC boot. This is
 * deliberate - running with an empty or absent whitelist would either hide every dataset downstream
 * or let assets be published without category validation.
 *
 * <p>Because the service is always registered, consumers inject {@link CategoryWhitelist} as a
 * required dependency.
 */
@Provides(CategoryWhitelist.class)
@Extension(value = CategoryWhitelistConfigExtension.EXTENSION_NAME)
public class CategoryWhitelistConfigExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "DSE Category Whitelist Config";

    public static final String DEFAULT_WHITELIST_PATH = "/etc/dse/category-whitelist/whitelist.json";

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

        var path = context.getSetting(PATH_KEY, DEFAULT_WHITELIST_PATH);

        monitor.info("Loading category whitelist from " + path);
        var whitelist = new CategoryWhitelistLoader(typeManager.getMapper()).load(path);
        monitor.info("Loaded category whitelist: " + whitelist.size() + " categories");

        context.registerService(CategoryWhitelist.class, whitelist);
    }
}
