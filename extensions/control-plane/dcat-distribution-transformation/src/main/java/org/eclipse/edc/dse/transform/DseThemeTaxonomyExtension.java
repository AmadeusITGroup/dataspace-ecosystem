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
 */

package org.eclipse.edc.dse.transform;

import jakarta.json.Json;
import org.eclipse.edc.dse.controlplane.category.whitelist.CategoryWhitelist;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.constants.CoreConstants;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;

import java.util.Map;

import static org.eclipse.edc.protocol.dsp.spi.type.DspConstants.DSP_CONTEXT_SEPARATOR;
import static org.eclipse.edc.protocol.dsp.spi.type.DspConstants.DSP_TRANSFORMER_CONTEXT;

/**
 * Registers {@link DSECatalogTransformer} so catalog responses include dcat:themeTaxonomy.
 * <p>
 * Separate from {@link DcatDistributionTransformationExtension} - different feature, different
 * lifecycle. Datasets aren't touched here; a dataset's dcat:theme should already point at this
 * taxonomy by the time the asset is created. The skos prefix used in responses is registered by
 * DseJsonLdNamespacesExtension (extensions:common:jsonld-namespaces).
 */
@Extension(value = DseThemeTaxonomyExtension.EXTENSION_NAME)
public class DseThemeTaxonomyExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "DSE Theme Taxonomy Transform Extension";

    public static final String DSP_TRANSFORMER_CONTEXT_V_2025_1 = DSP_TRANSFORMER_CONTEXT + DSP_CONTEXT_SEPARATOR + "2025-1";

    @Inject
    private TypeTransformerRegistry registry;

    @Inject
    private TypeManager typeManager;

    @Inject
    private ParticipantIdMapper participantIdMapper;

    @Inject
    private CategoryWhitelist categoryWhitelist;

    private ServiceExtensionContext extensionContext;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        this.extensionContext = context;
    }

    @Override
    public void prepare() {
        var jsonFactory = Json.createBuilderFactory(Map.of());
        // Replaces upstream's Catalog -> JsonObject transformer for DSP 2025-1. EDC's registry keeps
        // one transformer per input/output type and the last one registered wins (it logs
        // "Overriding transformer registered for ..."). Upstream registers in initialize(), we
        // register in prepare(), which runs after every initialize(), so ours is the one used.
        // Recheck on EDC upgrades: the system test ThemeTaxonomyTest fails if this stops working.
        registry.forContext(DSP_TRANSFORMER_CONTEXT_V_2025_1)
                .register(new DSECatalogTransformer(jsonFactory, typeManager, CoreConstants.JSON_LD, participantIdMapper, categoryWhitelist));

        extensionContext.getMonitor().info(EXTENSION_NAME + ": registered dcat:themeTaxonomy Catalog transformer (overriding upstream)");
    }
}
