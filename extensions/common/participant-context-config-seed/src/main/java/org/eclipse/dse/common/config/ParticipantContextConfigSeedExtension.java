/*
 *  Copyright (c) 2025 Amadeus SA
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus SA - Initial Implementation
 *
 */

package org.eclipse.dse.common.config;

import org.eclipse.edc.participantcontext.spi.config.model.ParticipantContextConfiguration;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

/**
 * Seeds a default {@link ParticipantContextConfiguration} on startup using the participant ID
 * read from the {@code edc.participant.id} configuration property.
 *
 * <p>Required for connectors that do not use IdentityHub or IssuerService (which manage their own
 * participant-context store). Wire this extension into the launcher via {@code runtimeOnly}.
 */
public class ParticipantContextConfigSeedExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "Participant Context Config Seed";

    @Inject
    private Monitor monitor;

    @Inject
    private ParticipantContextConfigService participantContextConfigService;

    private String participantId;
    private ServiceExtensionContext extensionContext;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        participantId = context.getSetting("edc.participant.id", "default-participant");
        extensionContext = context;
    }

    @Override
    public void start() {
        var existingConfig = participantContextConfigService.get(participantId);
        if (existingConfig.succeeded()) {
            monitor.debug("ParticipantContextConfig already exists for '%s', will not re-create".formatted(participantId));
            return;
        }
        if (existingConfig.reason() != org.eclipse.edc.spi.result.ServiceFailure.Reason.NOT_FOUND) {
            monitor.warning("Failed to retrieve ParticipantContextConfiguration for '%s': %s — aborting seed to avoid masking the error"
                    .formatted(participantId, existingConfig.getFailureDetail()));
            return;
        }

        var entries = extensionContext.getConfig().getEntries().entrySet().stream()
                .filter(e -> isParticipantScopedConfig(e.getKey()))
                .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue));

        var config = ParticipantContextConfiguration.Builder.newInstance()
                .participantContextId(participantId)
                .entries(entries)
                .build();

        var result = participantContextConfigService.save(config);
        if (result.failed()) {
            monitor.warning("Failed to seed ParticipantContextConfiguration for participant '%s': %s"
                    .formatted(participantId, result.getFailureDetail()));
        } else {
            monitor.info("Seeded ParticipantContextConfiguration for participant '%s' with %d entries"
                    .formatted(participantId, entries.size()));
        }
    }

    private static boolean isParticipantScopedConfig(String key) {
        return key.startsWith("edc.participant.")
                || key.startsWith("edc.iam.")
                || key.startsWith("edc.ih.iam.");
    }
}
