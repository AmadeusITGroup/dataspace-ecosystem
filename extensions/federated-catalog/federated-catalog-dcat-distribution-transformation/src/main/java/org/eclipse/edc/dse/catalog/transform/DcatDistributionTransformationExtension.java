/*
 *  Copyright (c) 2026 Amadeus IT Group
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.edc.dse.catalog.transform;

import jakarta.json.Json;
import org.eclipse.edc.dse.transform.JsonObjectFromEnrichedDistributionTransformer;
import org.eclipse.edc.dse.transform.JsonObjectToEnrichedDistributionTransformer;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;

import java.util.Map;

import static org.eclipse.edc.protocol.dsp.spi.type.DspConstants.DSP_CONTEXT_SEPARATOR;
import static org.eclipse.edc.protocol.dsp.spi.type.DspConstants.DSP_TRANSFORMER_CONTEXT;

/**
 * Registers the enriched distribution transformers used by the federated catalog to read and
 * write distributions without losing custom DCAT properties.
 * <p>
 * Registration happens in {@link #prepare()} so these override the upstream distribution transformers.
 */
@Extension(value = DcatDistributionTransformationExtension.NAME)
public class DcatDistributionTransformationExtension implements ServiceExtension {

    public static final String NAME = "Federated Catalog DCAT Distribution Transformation Extension";

    private static final String MANAGEMENT_API_TRANSFORMER_CONTEXT = "management-api";
    static final String DSP_TRANSFORMER_CONTEXT_V_2025_1 = DSP_TRANSFORMER_CONTEXT + DSP_CONTEXT_SEPARATOR + "2025-1";

    @Inject
    private TypeTransformerRegistry registry;

    @Inject
    private Monitor monitor;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void prepare() {
        var jsonFactory = Json.createBuilderFactory(Map.of());
        var dspRegistry = registry.forContext(DSP_TRANSFORMER_CONTEXT_V_2025_1);

        dspRegistry.register(new JsonObjectFromEnrichedDistributionTransformer(jsonFactory, monitor));
        dspRegistry.register(new JsonObjectToEnrichedDistributionTransformer());

        registry.register(new JsonObjectFromEnrichedDistributionTransformer(jsonFactory, monitor));
        registry.register(new JsonObjectToEnrichedDistributionTransformer());

        // EDC 0.17+ serializes federated catalog query results with the "management-api" transformer context
        // (FederatedCatalogApiExtension, also used by DSE's /v1alpha/catalog/query): override it there as well,
        // otherwise the upstream distribution transformer fails on DSE's enriched distributions.
        registry.forContext(MANAGEMENT_API_TRANSFORMER_CONTEXT)
                .register(new JsonObjectFromEnrichedDistributionTransformer(jsonFactory, monitor));
    }
}
