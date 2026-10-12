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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.failsafe.RetryPolicy;
import okhttp3.OkHttpClient;
import org.eclipse.dse.watchdog.RecoveringCredentialWatchdog;
import org.eclipse.edc.http.client.EdcHttpClientImpl;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialStatus;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VcStatus;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.token.spi.TokenValidationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Intentionally exercises the interim (deprecated-for-removal) fix classes; suppress [removal] warnings.
@SuppressWarnings("removal")
class RetryingBitstringStatusListRevocationServiceTest {

    private final Monitor monitor = mock();
    private final RetryingBitstringStatusListRevocationService service =
            new RetryingBitstringStatusListRevocationService(
                    new ObjectMapper(),
                    5 * 60 * 1000,
                    singleton("application/json"),
                    new EdcHttpClientImpl(new OkHttpClient(), RetryPolicy.ofDefaults(), mock()),
                    mock(TokenValidationService.class),
                    mock(DidPublicKeyResolver.class),
                    monitor,
                    3,     // maxRetries
                    1);    // backoffMillis (keep tests fast)

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private CredentialStatus statusAt(String path) {
        return new CredentialStatus("test-id", "BitstringStatusListEntry", Map.of(
                "statusPurpose", "revocation",
                "statusListIndex", 1,
                "statusListCredential",
                "http://127.0.0.1:%d%s".formatted(server.getAddress().getPort(), path)));
    }

    private void stub(String path, int status) {
        server.createContext(path, exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
    }

    // Serves `status` for the first `failTimes` hits, then 200 with `okBody`.
    private void stubThenOk(String path, int status, int failTimes, String okBody) {
        server.createContext(path, exchange -> {
            var n = hits.incrementAndGet();
            if (n <= failTimes) {
                exchange.sendResponseHeaders(status, -1);
            } else {
                var bytes = okBody.getBytes();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
    }

    // Always serves 200 with the given body (used to exercise successfully-downloaded-but-unparseable bodies).
    private void stubOkBody(String path, String body) {
        server.createContext(path, exchange -> {
            hits.incrementAndGet();
            var bytes = body.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    @Test
    void getCredential_malformed200Body_isNotRetried() {
        // A 200 response with an unparseable body throws during parsing (JsonProcessingException, wrapped
        // in EdcException). safeGetCredential normalizes it to a non-5xx failed Result, so the outer loop
        // does NOT retry it (and it must not throw and cancel the watchdog).
        stubOkBody("/sl/garbage", "this-is-not-json{{{");

        var result = service.checkValidity(statusAt("/sl/garbage"));

        assertThat(result.failed()).isTrue();
        assertThat(hits.get()).isEqualTo(1); // downloaded exactly once, no retries
    }

    @Test
    void getCredential_transient503_exhaustsRetriesThenFails() {
        stub("/sl/transient", 503);

        var result = service.checkValidity(statusAt("/sl/transient"));

        assertThat(result.failed()).isTrue();
        // 1 initial attempt + 3 retries == 4 downloads of the dead endpoint.
        assertThat(hits.get()).isEqualTo(4);
    }

    @Test
    void getCredential_transportException_connectionRefused_notRetriedByOuterLoop() throws Exception {
        // The injected EdcHttpClient already applies the global retry policy to transport IOExceptions.
        // When one finally surfaces here it must be normalized to a failed Result and NOT re-retried by
        // the outer loop (which would multiply attempts, e.g. 5 inner x 4 outer = 24), yet must not throw
        // (that would cancel the scheduled watchdog).
        var ex = new org.eclipse.edc.spi.EdcException(new java.net.ConnectException("Connection refused"));
        var count = runWithThrowingClient(ex);

        assertThat(count.get()).isEqualTo(1); // one download attempt, no outer-loop retries
    }

    @Test
    void getCredential_transportException_unknownHost_notRetriedByOuterLoop() throws Exception {
        // DNS failures (UnknownHostException) are likewise already retried by the global client policy;
        // the outer loop must not retry them again.
        var ex = new org.eclipse.edc.spi.EdcException(new java.net.UnknownHostException("issuer.invalid"));
        var count = runWithThrowingClient(ex);

        assertThat(count.get()).isEqualTo(1);
    }

    /**
     * Builds a service whose EdcHttpClient.execute(...) always throws {@code toThrow}, drives a validity
     * check, and returns the number of download attempts made. Used to assert that transport exceptions
     * surface as a failed Result without the outer loop re-retrying them.
     */
    private AtomicInteger runWithThrowingClient(RuntimeException toThrow) throws Exception {
        var counter = new AtomicInteger();
        var throwingClient = mock(EdcHttpClient.class);
        when(throwingClient.execute(org.mockito.ArgumentMatchers.any(okhttp3.Request.class))).thenAnswer(inv -> {
            counter.incrementAndGet();
            throw toThrow;
        });
        var svc = new RetryingBitstringStatusListRevocationService(
                new ObjectMapper(), 5 * 60 * 1000, singleton("application/json"),
                throwingClient, mock(TokenValidationService.class), mock(DidPublicKeyResolver.class),
                monitor, 3, 1);

        var result = svc.checkValidity(new CredentialStatus("test-id", "BitstringStatusListEntry", Map.of(
                "statusPurpose", "revocation",
                "statusListIndex", 1,
                "statusListCredential", "http://issuer.invalid/sl")));

        assertThat(result.failed()).isTrue();
        return counter;
    }

    @Test
    void getCredential_transient503ThenSuccess_recoversWithTwoRequests() {
        // 503 once, then a valid (not-revoked) bitstring status list credential -> validity succeeds.
        stubThenOk("/sl/recovers", 503, 1, notRevokedBitstringCredential());

        var result = service.checkValidity(statusAt("/sl/recovers"));

        assertThat(result.succeeded()).isTrue();
        // 1 failed (503) + 1 successful retry == 2 requests.
        assertThat(hits.get()).isEqualTo(2);
    }

    // Minimal valid BitstringStatusList credential whose bit at index 1 is 0 (i.e. not revoked).
    private String notRevokedBitstringCredential() {
        var allZeroBitstring = org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.BitString.Writer
                .newInstance()
                .encoder(java.util.Base64.getUrlEncoder().withoutPadding())
                .writeMultibase(org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.BitString.Builder
                        .newInstance().size(16 * 1024).build())
                .getContent();
        return """
                {
                  "@context": ["https://www.w3.org/ns/credentials/v2"],
                  "type": ["VerifiableCredential", "BitstringStatusListCredential"],
                  "issuer": "did:web:issuer",
                  "credentialSubject": {
                    "type": "BitstringStatusList",
                    "statusPurpose": "revocation",
                    "encodedList": "%s"
                  }
                }
                """.formatted(allZeroBitstring);
    }

    @Test
    void getCredential_nonTransient404_notRetried() {
        stub("/sl/notfound", 404);

        var result = service.checkValidity(statusAt("/sl/notfound"));

        assertThat(result.failed()).isTrue();
        // 404 is non-transient: exactly one attempt, no retries.
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    void getCredential_transient500_retriesUpToMax() {
        stub("/sl/flaky", 500);

        var result = service.checkValidity(statusAt("/sl/flaky"));

        assertThat(result.failed()).isTrue();
        assertThat(hits.get()).isEqualTo(4);
    }

    // The superclass surfaces an HTTP download failure in this exact shape:
    private static String httpFailure(String url, int code, String reason) {
        return "Failed to download status list credential from %s: %d %s".formatted(url, code, reason);
    }

    @Test
    void isTransient_5xxStatus_isTransient() {
        var url = "http://issuer:9999/api/statuslist/abc";
        assertThat(RetryingBitstringStatusListRevocationService.isTransient(httpFailure(url, 500, "Internal Server Error"))).isTrue();
        assertThat(RetryingBitstringStatusListRevocationService.isTransient(httpFailure(url, 502, "Bad Gateway"))).isTrue();
        assertThat(RetryingBitstringStatusListRevocationService.isTransient(httpFailure(url, 503, "Service Unavailable"))).isTrue();
        assertThat(RetryingBitstringStatusListRevocationService.isTransient(httpFailure(url, 504, "Gateway Timeout"))).isTrue();
        // 501 and 507 must also be retried (previously missed by substring matching).
        assertThat(RetryingBitstringStatusListRevocationService.isTransient(httpFailure(url, 501, "Not Implemented"))).isTrue();
        assertThat(RetryingBitstringStatusListRevocationService.isTransient(httpFailure(url, 507, "Insufficient Storage"))).isTrue();
    }

    @Test
    void isTransient_4xxStatus_isNotTransient() {
        var url = "http://issuer:9999/api/statuslist/abc";
        assertThat(RetryingBitstringStatusListRevocationService.isTransient(httpFailure(url, 400, "Bad Request"))).isFalse();
        assertThat(RetryingBitstringStatusListRevocationService.isTransient(httpFailure(url, 401, "Unauthorized"))).isFalse();
        assertThat(RetryingBitstringStatusListRevocationService.isTransient(httpFailure(url, 404, "Not Found"))).isFalse();
    }

    @Test
    void isTransient_statusDigitsInUrl_doNotCauseFalsePositive() {
        // A 404 for a URL that itself contains "500" must NOT be classified transient (Copilot finding).
        var url = "http://issuer:9999/api/statuslist/500";
        assertThat(RetryingBitstringStatusListRevocationService.isTransient(httpFailure(url, 404, "Not Found"))).isFalse();
        // Conversely a genuine 503 for that same URL is transient.
        assertThat(RetryingBitstringStatusListRevocationService.isTransient(httpFailure(url, 503, "Service Unavailable"))).isTrue();
    }

    @Test
    void isTransient_4xxReasonPhraseWithNetworkWords_isNotTransient() {
        var url = "http://issuer:9999/api/statuslist/abc";
        // A 4xx whose reason phrase happens to contain "timeout"/"connect" must NOT be retried:
        // classification is driven by the parsed 3-digit status, not loose substring matching.
        assertThat(RetryingBitstringStatusListRevocationService.isTransient(httpFailure(url, 400, "connect field invalid"))).isFalse();
        assertThat(RetryingBitstringStatusListRevocationService.isTransient(httpFailure(url, 404, "timeout parameter missing"))).isFalse();
    }

    @Test
    void isTransient_malformedOrOversizedStatusToken_isNotTransient() {
        var url = "http://issuer:9999/api/statuslist/abc";
        // Non-3-digit / out-of-range tokens are not treated as HTTP statuses (and must not throw).
        assertThat(RetryingBitstringStatusListRevocationService.isTransient(httpFailure(url, 99999, "weird"))).isFalse();
        assertThat(RetryingBitstringStatusListRevocationService.isTransient("Failed to download status list credential from " + url + ": ")).isFalse();
        assertThat(RetryingBitstringStatusListRevocationService.isTransient("Failed to download status list credential from " + url + ": nope reason")).isFalse();
    }

    @Test
    void isTransient_networkFailures_areNotTransient() {
        // Transport/IO failures are NOT retried by this outer loop: the global EdcHttpClient retry policy
        // has already retried them before they surface. isTransient only recognizes HTTP 5xx.
        assertThat(RetryingBitstringStatusListRevocationService.isTransient(
                "Status list credential download failed for http://x/sl (UnknownHostException: x)")).isFalse();
        assertThat(RetryingBitstringStatusListRevocationService.isTransient(
                "Status list credential download failed for http://x/sl (ConnectException: Connection refused)")).isFalse();
        assertThat(RetryingBitstringStatusListRevocationService.isTransient("java.net.SocketTimeoutException: timeout")).isFalse();
    }

    @Test
    void isTransient_nullEmptyOrOther_isNotTransient() {
        assertThat(RetryingBitstringStatusListRevocationService.isTransient(null)).isFalse();
        assertThat(RetryingBitstringStatusListRevocationService.isTransient("")).isFalse();
        assertThat(RetryingBitstringStatusListRevocationService.isTransient("Invalid JSON format")).isFalse();
        assertThat(RetryingBitstringStatusListRevocationService.isTransient("Validation of status list credential JWT failed.")).isFalse();
    }

    @Test
    void testRecoveringWatchdog_allowedStatesIncludesError() {
        // Verify that the RecoveringCredentialWatchdog includes ERROR in ALLOWED_STATES
        assertThat(RecoveringCredentialWatchdog.ALLOWED_STATES)
                .contains(VcStatus.ERROR.code())
                .as("ALLOWED_STATES should include ERROR code for self-healing");
    }
}
