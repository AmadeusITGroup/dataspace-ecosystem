/*
 *  Copyright (c) 2026 Amadeus IT Group
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.edc.dse.transform;

import jakarta.json.Json;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;

import java.util.Map;

import static org.eclipse.edc.protocol.dsp.spi.type.DspConstants.DSP_CONTEXT_SEPARATOR;
import static org.eclipse.edc.protocol.dsp.spi.type.DspConstants.DSP_TRANSFORMER_CONTEXT;

/**
 * Extension that registers custom transformers for Dataset and Distribution
 * with the same input/output type pair as the upstream DSP catalog transformers.
 * <p>
 * This extension verifies that the TypeTransformerRegistry correctly allows
 * overriding transformers by registering a new one with the same type signature.
 * <p>
 * Registration is done in {@link #prepare()} to ensure it executes after all
 * upstream extensions have completed their {@link #initialize(ServiceExtensionContext)} phase,
 * guaranteeing our custom transformers override the upstream ones.
 * <p>
 * <strong>WARNING:</strong> This extension is disabled by default. It must be explicitly
 * enabled via the {@code dse.dcat.distribution.transformation.enabled} configuration property.
 * The current transformers produce minimal JSON-LD output and are intended for
 * test/validation purposes only. Do NOT enable in production without a complete implementation.
 */
@Extension(value = DcatDistributionTransformationExtension.NAME)
public class DcatDistributionTransformationExtension implements ServiceExtension {

    public static final String NAME = "DCAT Distribution Transformation Extension";
    public static final String ENABLED_PROPERTY = "dse.dcat.distribution.transformation.enabled";

    static final String DSP_TRANSFORMER_CONTEXT_V_2025_1 = DSP_TRANSFORMER_CONTEXT + DSP_CONTEXT_SEPARATOR + "2025-1";

    @Setting(description = "Enables the custom DCAT distribution/dataset transformer override. " +
            "Set to 'true' to activate. Defaults to 'false' (disabled).",
            key = ENABLED_PROPERTY,
            defaultValue = "false",
            required = false)
    private String enabled;

    @Inject
    private TypeTransformerRegistry registry;

    private ServiceExtensionContext extensionContext;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        this.extensionContext = context;
    }

    @Override
    public void prepare() {
        if (!Boolean.parseBoolean(enabled)) {
            extensionContext.getMonitor().info(NAME + ": disabled (set " + ENABLED_PROPERTY + "=true to activate)");
            return;
        }

        var jsonFactory = Json.createBuilderFactory(Map.of());

        var dspRegistry = registry.forContext(DSP_TRANSFORMER_CONTEXT_V_2025_1);

        // Register custom transformers that override the upstream ones
        dspRegistry.register(new CustomDatasetTransformer(jsonFactory));
        dspRegistry.register(new CustomDistributionTransformer(jsonFactory));

        extensionContext.getMonitor().info(NAME + ": registered custom Dataset and Distribution transformers (overriding upstream)");
    }
}
