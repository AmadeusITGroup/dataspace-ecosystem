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

package org.eclipse.dse.watchdog;

import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredentialContainer;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.CredentialRequestManager;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.CredentialStatusCheckService;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VcStatus;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VerifiableCredentialResource;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.store.CredentialStore;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialFormat.VC1_0_JWT;
import static org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VcStatus.ERROR;
import static org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VcStatus.ISSUED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Exercises the interim (deprecated-for-removal) fix classes; suppress [removal] warnings.
@SuppressWarnings("removal")
class RecoveringCredentialWatchdogTest {

    private static final long GRACE_PERIOD = 20;

    private final CredentialStore credentialStore = mock();
    private final CredentialStatusCheckService credentialStatusCheckService = mock();
    private final CredentialRequestManager credentialRequestManager = mock();
    private final Monitor monitor = mock();
    private final RecoveringCredentialWatchdog watchdog = new RecoveringCredentialWatchdog(
            credentialStore, credentialStatusCheckService, monitor,
            new NoopTransactionContext(), Duration.ofSeconds(GRACE_PERIOD), credentialRequestManager);

    @BeforeEach
    void setUp() {
        when(credentialStatusCheckService.checkStatus(any())).thenReturn(Result.success(ISSUED));
        when(credentialRequestManager.initiateRequest(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(ServiceResult.success());
        when(credentialStore.update(any())).thenReturn(StoreResult.success());
    }

    @Test
    void allowedStates_includesError_forSelfHealing() {
        // The one-line delta vs upstream: ERROR must be scannable so -100 credentials can recover.
        assertThat(RecoveringCredentialWatchdog.ALLOWED_STATES).contains(ERROR.code());
    }

    @Test
    void run_queriesWithErrorIncludedFilter() {
        when(credentialStore.query(any())).thenReturn(StoreResult.success(Collections.emptyList()));

        watchdog.run();

        verify(credentialStore).query(argThat(q ->
                q.getFilterExpression().size() == 2 &&
                        q.getFilterExpression().get(0).toString().equals("state in " + RecoveringCredentialWatchdog.ALLOWED_STATES)));
    }

    @Test
    void run_whenNoCredentials_doesNothing() {
        when(credentialStore.query(any())).thenReturn(StoreResult.success(Collections.emptyList()));

        watchdog.run();

        verifyNoInteractions(credentialStatusCheckService);
        verify(credentialStore, never()).update(any());
    }

    @Test
    void run_whenErrorCredentialBecomesHealthy_healsBackToIssued() {
        // A credential stuck in ERROR(-100): once the status check succeeds, it must transition back to ISSUED.
        var stuck = createCredentialBuilder().state(ERROR).build();
        when(credentialStore.query(any())).thenReturn(StoreResult.success(List.of(stuck)));
        when(credentialStatusCheckService.checkStatus(any())).thenReturn(Result.success(ISSUED));

        watchdog.run();

        verify(credentialStore).update(argThat(vcr -> vcr.getStateAsEnum() == ISSUED));
    }

    @Test
    void run_whenCheckFails_transitionsToError() {
        var cred = createCredentialBuilder().build();
        when(credentialStore.query(any())).thenReturn(StoreResult.success(List.of(cred)));
        when(credentialStatusCheckService.checkStatus(any())).thenReturn(Result.failure("statuslist unreachable"));

        watchdog.run();

        verify(credentialStore).update(argThat(vcr -> vcr.getStateAsEnum() == ERROR));
    }

    @Test
    void run_whenStatusUnchanged_doesNotUpdate() {
        var cred = createCredentialBuilder().state(ISSUED).build();
        when(credentialStore.query(any())).thenReturn(StoreResult.success(List.of(cred)));
        when(credentialStatusCheckService.checkStatus(any())).thenReturn(Result.success(ISSUED));

        watchdog.run();

        verify(credentialStore, never()).update(any());
    }

    @Test
    void run_whenCredentialExpiring_initiatesReissuance() {
        var cred = createCredentialBuilder()
                .metadata("credentialObjectId", "cred-object-id")
                .credential(new VerifiableCredentialContainer("raw", VC1_0_JWT, createVerifiableCredential()
                        .expirationDate(Instant.now().plusSeconds(GRACE_PERIOD / 2)).build()))
                .build();
        when(credentialStore.query(any())).thenReturn(StoreResult.success(List.of(cred)));

        watchdog.run();

        verify(credentialRequestManager).initiateRequest(anyString(), anyString(), anyString(),
                argThat(list -> list.size() == 1 && list.get(0).format().equals(VC1_0_JWT.name())));
        verify(credentialStore).update(argThat(vcr -> vcr.getStateAsEnum() == VcStatus.REQUESTED));
    }

    @Test
    void run_whenExpiringButNoObjectId_abortsReissuance() {
        var cred = createCredentialBuilder()
                .credential(new VerifiableCredentialContainer("raw", VC1_0_JWT, createVerifiableCredential()
                        .expirationDate(Instant.now().plusSeconds(GRACE_PERIOD / 2)).build()))
                .build();
        when(credentialStore.query(any())).thenReturn(StoreResult.success(List.of(cred)));

        watchdog.run();

        verify(credentialRequestManager, never()).initiateRequest(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void run_whenCredentialHasNoExpirationDate_doesNotThrowAndStillChecksStatus() {
        // A non-expiring credential (null expirationDate) must not NPE the expiry pass — that previously
        // escaped run() and permanently cancelled the scheduled watchdog.
        var noExpiry = createCredentialBuilder()
                .credential(new VerifiableCredentialContainer("raw", VC1_0_JWT, createVerifiableCredential()
                        .expirationDate(null).build()))
                .state(VcStatus.ERROR)
                .build();
        when(credentialStore.query(any())).thenReturn(StoreResult.success(List.of(noExpiry)));
        when(credentialStatusCheckService.checkStatus(any())).thenReturn(Result.success(ISSUED));

        assertThatNoException().isThrownBy(watchdog::run);
        verify(credentialStore).update(argThat(vcr -> vcr.getStateAsEnum() == ISSUED));
        verify(credentialRequestManager, never()).initiateRequest(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void run_whenStatusCheckThrows_isolatesCredentialAndSurvivesCycle() {
        // One credential whose status check throws must not abort the whole cycle nor escape run().
        var bad = createCredentialBuilder().build();
        var good = createCredentialBuilder().state(VcStatus.ERROR).build();
        when(credentialStore.query(any())).thenReturn(StoreResult.success(List.of(bad, good)));
        when(credentialStatusCheckService.checkStatus(any()))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(Result.success(ISSUED));

        assertThatNoException().isThrownBy(watchdog::run);
        // the second (good) credential was still processed and healed despite the first throwing
        verify(credentialStore).update(argThat(vcr -> vcr.getId().equals(good.getId()) && vcr.getStateAsEnum() == ISSUED));
    }

    @Test
    void run_whenQueryThrows_doesNotEscape() {
        // A transaction/store failure must be contained so the next scheduled cycle still runs.
        when(credentialStore.query(any())).thenThrow(new RuntimeException("db down"));

        assertThatNoException().isThrownBy(watchdog::run);
    }

    private VerifiableCredentialResource.Builder createCredentialBuilder() {
        return VerifiableCredentialResource.Builder.newHolder()
                .issuerId("test-issuer")
                .holderId("test-holder")
                .state(ISSUED)
                .participantContextId("participant-id")
                .credential(new VerifiableCredentialContainer("raw", VC1_0_JWT, createVerifiableCredential().build()))
                .id(UUID.randomUUID().toString());
    }

    private VerifiableCredential.Builder createVerifiableCredential() {
        return VerifiableCredential.Builder.newInstance()
                .credentialSubject(CredentialSubject.Builder.newInstance().id("test-subject").claim("k", "v").build())
                .issuanceDate(Instant.now().minus(10, ChronoUnit.DAYS))
                .expirationDate(Instant.now().plus(365, ChronoUnit.DAYS))
                .type("VerifiableCredential")
                .type("DemoCredential")
                .issuer(new Issuer("test-issuer", Map.of()))
                .id("did:web:test-credential");
    }
}
