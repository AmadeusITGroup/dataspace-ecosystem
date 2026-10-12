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

import org.eclipse.edc.identityhub.spi.credential.request.model.RequestedCredential;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.CredentialRequestManager;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.CredentialStatusCheckService;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.model.CredentialUsage;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VcStatus;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VerifiableCredentialResource;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.store.CredentialStore;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static java.util.Optional.ofNullable;
import static org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VcStatus.ERROR;
import static org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VcStatus.EXPIRED;
import static org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VcStatus.ISSUED;
import static org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VcStatus.NOT_YET_VALID;
import static org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VcStatus.SUSPENDED;

/**
 * This is a runnable task that is intended to be executed periodically to fetch all non-expired, non-revoked credentials from storage, check for their status,
 * and update their status. Every execution (fetch-all - check-each - update-each) will run in a transaction.
 *
 * <p>
 * Note that this will materialize **all** credentials into memory at once, as the general assumption is that typically, wallets don't
 * store an enormous amount of credentials. To mitigate this, the watchdog only considers credentials in states {@link VcStatus#EXPIRED}, {@link VcStatus#ISSUED},
 * {@link VcStatus#SUSPENDED}, {@link VcStatus#NOT_YET_VALID}, and {@link VcStatus#ERROR}, c.f. {@link RecoveringCredentialWatchdog#ALLOWED_STATES}.
 * </p>
 *
 * <p>
 * Note also, that a credentials status will only be updated if it did in fact change, to avoid unnecessary database interactions.
 * </p>
 *
 * <p><b>INTERIM FIX — REMOVE WHEN DSE MIGRATES TO EDC v1.0.0.</b><br>
 * Fork of the upstream {@code CredentialWatchdog} with two changes: (1) {@code ERROR} added to
 * {@link #ALLOWED_STATES} so credentials that transiently entered the error state (-100) are re-checked
 * and self-heal (upstream fix
 * <a href="https://github.com/eclipse-edc/IdentityHub/pull/1102">eclipse-edc/IdentityHub#1102</a>, ships
 * in <b>EDC 1.0.0</b>); and (2) a top-level exception boundary plus per-credential isolation and a
 * null-expiration guard in {@code run()}, because this task is scheduled with {@code scheduleAtFixedRate}
 * — any exception escaping {@code run()} permanently cancels all future executions, which would silently
 * kill the self-healing. Verified upstream {@code ALLOWED_STATES} still excludes ERROR through 0.18.x.
 * When bumping EDC, re-sync against the upstream {@code CredentialWatchdog}; when adopting EDC 1.0.0,
 * delete this fork + the {@code credential-watchdog} BOM exclude so the native watchdog runs.</p>
 *
 * @deprecated interim fix; remove when DSE migrates to EDC v1.0.0 (upstream eclipse-edc/IdentityHub#1102).
 */
@Deprecated(forRemoval = true) // remove when DSE adopts EDC v1.0.0 — upstream fix: eclipse-edc/IdentityHub#1102
public class RecoveringCredentialWatchdog implements Runnable {
    // Include ERROR to allow self-healing of credentials that entered error state due to transient failures
    // (THE one-line delta vs upstream CredentialWatchdog.ALLOWED_STATES — see eclipse-edc/IdentityHub#1102).
    public static final List<Integer> ALLOWED_STATES = List.of(ISSUED.code(), NOT_YET_VALID.code(), SUSPENDED.code(), EXPIRED.code(), ERROR.code());
    
    private final CredentialStore credentialStore;
    private final CredentialStatusCheckService credentialStatusCheckService;
    private final Monitor monitor;
    private final TransactionContext transactionContext;
    private final Duration expiryGracePeriod;
    private final CredentialRequestManager credentialRequestManager;

    public RecoveringCredentialWatchdog(CredentialStore credentialStore, CredentialStatusCheckService credentialStatusCheckService, Monitor monitor,
                                        TransactionContext transactionContext, Duration expiryGracePeriod, CredentialRequestManager credentialRequestManager) {
        this.credentialStore = credentialStore;
        this.credentialStatusCheckService = credentialStatusCheckService;
        this.monitor = monitor;
        this.transactionContext = transactionContext;
        this.expiryGracePeriod = expiryGracePeriod;
        this.credentialRequestManager = credentialRequestManager;
    }

    @Override
    public void run() {
        // Top-level boundary: this task is submitted with scheduleAtFixedRate, where ANY exception that
        // escapes run() permanently cancels all future executions. We must never let that happen, or the
        // self-healing watchdog would silently die on a single malformed credential or transient DB error.
        try {
            transactionContext.execute(() -> {
                var allCredentials = credentialStore.query(allExcludingExpiredAndRevoked())
                        .onFailure(f -> monitor.warning("Failed to fetch credentials from database: %s".formatted(f.getFailureDetail())))
                        .orElse(f -> Collections.emptyList());

                monitor.debug("checking %d credentials".formatted(allCredentials.size()));

                // check status — isolate each credential so one failure doesn't abort the whole cycle
                allCredentials.forEach(this::checkAndUpdateStatus);

                // check credentials that are nearing expiry — likewise isolated
                allCredentials.forEach(this::maybeStartReissuance);
            });
        } catch (RuntimeException e) {
            monitor.severe("Credential watchdog cycle failed; it will run again next period", e);
        }
    }

    private void checkAndUpdateStatus(VerifiableCredentialResource credential) {
        try {
            var newStatus = credentialStatusCheckService.checkStatus(credential)
                    .orElse(f -> {
                        monitor.warning("Error determining status for credential '%s': %s. Will move to the ERROR state.".formatted(credential.getId(), f.getFailureDetail()));
                        return VcStatus.ERROR;
                    });

            var changed = credential.getState() != newStatus.code();
            if (changed) {
                monitor.debug("Credential '%s' is now in status '%s'".formatted(credential.getId(), newStatus));
                credential.setCredentialStatus(newStatus);
                credentialStore.update(credential);
            }
        } catch (RuntimeException e) {
            monitor.warning("Failed to check status for credential '%s'; skipping it this cycle".formatted(credential.getId()), e);
        }
    }

    private void maybeStartReissuance(VerifiableCredentialResource credential) {
        try {
            var vc = credential.getVerifiableCredential().credential();
            var expiration = vc.getExpirationDate();
            if (expiration == null) {
                // Non-expiring credential: nothing to renew. (A null here previously NPE'd and killed the watchdog.)
                return;
            }
            if (Instant.now().isAfter(expiration.minusSeconds(expiryGracePeriod.toSeconds()))) {
                startReissuance(credential);
            }
        } catch (RuntimeException e) {
            monitor.warning("Failed to evaluate/renew credential '%s'; skipping it this cycle".formatted(credential.getId()), e);
        }
    }

    private void startReissuance(VerifiableCredentialResource expiringCredential) {
        var formatString = expiringCredential.getVerifiableCredential().format().toString();
        var type = expiringCredential.getVerifiableCredential().credential().getType()
                .stream()
                .filter(s -> !"VerifiableCredential".equalsIgnoreCase(s))
                .findAny()
                .orElse(null);

        var credentialObjectId = ofNullable(expiringCredential.getMetadata().get("credentialObjectId")).map(Object::toString);

        if (credentialObjectId.isEmpty()) {
            monitor.warning("Attempting to start re-issuance for credential '%s' failed: No CredentialObjectId found (metadata property 'credentialObjectId'). Will abort re-issuance.".formatted(expiringCredential.getId()));
            return;
        }

        var requestedCredential = new RequestedCredential(credentialObjectId.get(), type, formatString);
        expiringCredential.setCredentialStatus(VcStatus.REQUESTED);

        credentialRequestManager.initiateRequest(expiringCredential.getParticipantContextId(), expiringCredential.getIssuerId(), UUID.randomUUID().toString(), List.of(requestedCredential))
                .compose(holderRequestId -> ServiceResult.from(credentialStore.update(expiringCredential)))
                .onFailure(f -> monitor.warning("Error sending re-issuance request: %s".formatted(f.getFailureDetail())));
    }

    private QuerySpec allExcludingExpiredAndRevoked() {
        return QuerySpec.Builder.newInstance()
                .filter(new Criterion("state", "in", ALLOWED_STATES))
                .filter(new Criterion("usage", "=", CredentialUsage.Holder.toString()))
                .build();
    }
}
