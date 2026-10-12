/*
 * Copyright (c) 2024 Dataspace Ecosystem
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contributors:
 *      Dataspace Ecosystem - initial API and implementation
 */

package org.eclipse.dse.revocation;

import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.RevocationServiceRegistry;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.token.spi.TokenValidationService;

import java.util.List;

/**
 * Registers a retrying BitstringStatusList revocation service.
 *
 * <p><b>INTERIM FIX — REMOVE WHEN DSE MIGRATES TO EDC v1.0.0.</b><br>
 * Prevents transient statuslist failures from flipping VCs to {@code VcStatus.ERROR} (-100). Mirrors
 * upstream <a href="https://github.com/eclipse-edc/Connector/pull/6049">eclipse-edc/Connector#6049</a>
 * (ships in EDC 1.0.0). Injecting {@link RevocationServiceRegistry} guarantees this runs after the EDC
 * provider; {@code addService} is last-writer-wins, so our service replaces the stock one. Delete this
 * module and its launcher wiring once DSE is on EDC 1.0.0.</p>
 *
 * @deprecated interim fix; remove when DSE migrates to EDC v1.0.0 (upstream eclipse-edc/Connector#6049).
 */
@Deprecated(forRemoval = true) // remove when DSE adopts EDC v1.0.0 — upstream fix: eclipse-edc/Connector#6049
@Extension(value = ResilientRevocationServiceExtension.NAME)
public class ResilientRevocationServiceExtension implements ServiceExtension {
    public static final String NAME = "Resilient Revocation Service Extension";

    @Setting(description = "Revocation cache validity period in milliseconds", key = "edc.iam.credential.revocation.cache.validity", defaultValue = "900000")
    private long cacheValidity;

    @Setting(description = "Accepted content types for revocation list downloads", key = "edc.iam.credential.revocation.mimetype", defaultValue = "*/*")
    private String contentTypes;

    @Setting(description = "Maximum number of retry attempts for transient failures", key = "edc.iam.credential.revocation.retry.max", defaultValue = "3", min = 0)
    private int maxRetries;

    @Setting(description = "Backoff time in milliseconds between retry attempts", key = "edc.iam.credential.revocation.retry.backoff.ms", defaultValue = "500", min = 0)
    private long backoffMillis;

    @Inject
    private RevocationServiceRegistry registry;

    @Inject
    private TypeManager typeManager;

    @Inject
    private EdcHttpClient httpClient;

    @Inject
    private TokenValidationService tokenValidationService;

    @Inject
    private DidPublicKeyResolver didPublicKeyResolver;

    @Inject
    private Monitor monitor;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var mapper = typeManager.getMapper();
        var acceptedTypes = List.of(contentTypes.split(","));
        
        var service = new RetryingBitstringStatusListRevocationService(
                mapper,
                cacheValidity,
                acceptedTypes,
                httpClient,
                tokenValidationService,
                didPublicKeyResolver,
                monitor,
                maxRetries,
                backoffMillis
        );

        registry.addService(BitstringStatusListStatus.TYPE, service);
        monitor.info("Registered resilient bitstring status list revocation service with max %d retries".formatted(maxRetries));
    }
}
