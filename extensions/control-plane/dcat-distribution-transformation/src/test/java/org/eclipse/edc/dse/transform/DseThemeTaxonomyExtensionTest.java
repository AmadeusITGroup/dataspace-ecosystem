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

import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.dse.controlplane.category.whitelist.CategoryWhitelist;
import org.eclipse.edc.dse.controlplane.category.whitelist.WhitelistedCategory;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformer;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.dse.transform.DseThemeTaxonomyExtension.DSP_TRANSFORMER_CONTEXT_V_2025_1;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code @Inject} fields are resolved when {@link ObjectFactory#constructInstance} runs, so any
 * service the extension should see (e.g. {@link CategoryWhitelist}) must be registered on the
 * context before constructing it.
 */
@ExtendWith(DependencyInjectionExtension.class)
class DseThemeTaxonomyExtensionTest {

    private final TypeTransformerRegistry registry = mock();
    private final TypeTransformerRegistry dspRegistry = mock();
    private final TypeManager typeManager = new JacksonTypeManager();
    private final ParticipantIdMapper participantIdMapper = mock();
    private final Monitor monitor = mock();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        when(registry.forContext(DSP_TRANSFORMER_CONTEXT_V_2025_1)).thenReturn(dspRegistry);
        context.registerService(TypeTransformerRegistry.class, registry);
        context.registerService(TypeManager.class, typeManager);
        context.registerService(ParticipantIdMapper.class, participantIdMapper);
        when(context.getMonitor()).thenReturn(monitor);
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of()));
    }

    @Test
    void prepare_withWhitelist_registersOneCatalogTransformer(ObjectFactory factory, ServiceExtensionContext context) {
        context.registerService(CategoryWhitelist.class, new CategoryWhitelist(List.of(
                new WhitelistedCategory("50", "Airline Order", null, "urn:category:50", null))));
        var extension = factory.constructInstance(DseThemeTaxonomyExtension.class);

        extension.initialize(context);
        extension.prepare();

        verify(dspRegistry).register(any(DSECatalogTransformer.class));
        verify(dspRegistry, times(1)).register(any(TypeTransformer.class));
    }

    @Test
    void name_returnsExtensionName(ObjectFactory factory) {
        var extension = factory.constructInstance(DseThemeTaxonomyExtension.class);

        assertThat(extension.name()).isEqualTo(DseThemeTaxonomyExtension.EXTENSION_NAME);
    }
}
