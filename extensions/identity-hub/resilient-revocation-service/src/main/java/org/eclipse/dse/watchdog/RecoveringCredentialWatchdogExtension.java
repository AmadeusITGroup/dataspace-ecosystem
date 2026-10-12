/*
 * Copyright (c) 2024 Metaform Systems, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contributors:
 *      Metaform Systems, Inc. - initial API and implementation
 *      Dataspace Ecosystem - fork with ERROR state recovery
 */

package org.eclipse.dse.watchdog;

import org.eclipse.edc.identityhub.spi.verifiablecredentials.CredentialRequestManager;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.CredentialStatusCheckService;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.store.CredentialStore;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.Optional.ofNullable;

/**
 * Registers the {@link RecoveringCredentialWatchdog}.
 *
 * <p><b>INTERIM FIX — REMOVE WHEN DSE MIGRATES TO EDC v1.0.0.</b><br>
 * Replaces the stock {@code credential-watchdog} (excluded from the identity-hub launcher BOM) so that
 * ERROR(-100) credentials are re-checked and self-heal. Mirrors upstream
 * <a href="https://github.com/eclipse-edc/IdentityHub/pull/1102">eclipse-edc/IdentityHub#1102</a>
 * (ships in EDC 1.0.0). On EDC 1.0.0: delete this module, remove the BOM exclude, and let the native
 * watchdog run.</p>
 *
 * @deprecated interim fix; remove when DSE migrates to EDC v1.0.0 (upstream eclipse-edc/IdentityHub#1102).
 */
@Deprecated(forRemoval = true) // remove when DSE adopts EDC v1.0.0 — upstream fix: eclipse-edc/IdentityHub#1102
@Extension(value = RecoveringCredentialWatchdogExtension.NAME)
public class RecoveringCredentialWatchdogExtension implements ServiceExtension {
    public static final String NAME = "VerifiableCredential Recovering Watchdog Extension";
    public static final int DEFAULT_WATCHDOG_PERIOD = 60;
    public static final int DEFAULT_WATCHDOG_INITIAL_DELAY = 5;
    public static final int DEFAULT_GRACE_PERIOD_SECONDS = 7 * 24 * 3600; // 1 week
    public static final String CREDENTIAL_WATCHDOG = "RecoveringCredentialWatchdog";
    private final SecureRandom random = new SecureRandom();

    @Setting(description = "Period (in seconds) at which the Watchdog thread checks all stored credentials for their status. A number <=0 disables the Watchdog.",
            min = 0, defaultValue = DEFAULT_WATCHDOG_PERIOD + "", key = "edc.iam.credential.status.check.period")
    private int watchdogPeriod;

    @Setting(description = "Initial delay (in seconds) before the Watchdog thread begins its work.", min = 0, key = "edc.iam.credential.status.check.delay", required = false)
    private Integer initialDelay;

    @Setting(description = "Grace period (in seconds) before credential expiry at which automatic renewal is triggered", key = "edc.iam.credential.renewal.graceperiod", min = 0, defaultValue = DEFAULT_GRACE_PERIOD_SECONDS + "")
    private long gracePeriodSeconds;

    @Inject
    private ExecutorInstrumentation executorInstrumentation;

    @Inject
    private CredentialStatusCheckService credentialStatusCheckService;

    @Inject
    private CredentialStore credentialStore;

    @Inject
    private TransactionContext transactionContext;

    @Inject
    private CredentialRequestManager credentialRequestManager;

    private ScheduledExecutorService scheduledExecutorService;
    private Monitor monitor;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor = context.getMonitor().withPrefix(CREDENTIAL_WATCHDOG);

        if (watchdogPeriod > 0) {
            initialDelay = ofNullable(initialDelay).orElseGet((this::randomDelay));
            monitor.debug(() -> "Credential watchdog will run with a delay of %d seconds, at an interval of %d seconds".formatted(initialDelay, watchdogPeriod));
            scheduledExecutorService = executorInstrumentation.instrument(Executors.newSingleThreadScheduledExecutor(), CREDENTIAL_WATCHDOG);
        } else {
            monitor.debug(() -> "The Credential Watchdog is disabled.");
        }
    }

    @Override
    public void start() {
        if (scheduledExecutorService != null && !scheduledExecutorService.isShutdown()) {
            monitor.debug(() -> "Starting credential watchdog in %d seconds, every %d seconds".formatted(initialDelay, watchdogPeriod));
            var watchdog = new RecoveringCredentialWatchdog(credentialStore, credentialStatusCheckService, monitor, transactionContext, Duration.ofSeconds(gracePeriodSeconds), credentialRequestManager);
            scheduledExecutorService.scheduleAtFixedRate(watchdog, initialDelay, watchdogPeriod, TimeUnit.SECONDS);
        }
    }

    @Override
    public void shutdown() {
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdownNow();
        }
    }

    private Integer randomDelay() {
        return random.nextInt(1, DEFAULT_WATCHDOG_INITIAL_DELAY);
    }
}
