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
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.iam.verifiablecredentials.revocation.bitstring.BitstringStatusListRevocationService;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListCredential;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.token.spi.TokenValidationService;

import java.util.Collection;

/**
 * Extends the standard BitstringStatusListRevocationService with a bounded retry for transient
 * <em>HTTP 5xx</em> responses when downloading a status list credential.
 *
 * <p><b>INTERIM FIX — REMOVE WHEN DSE MIGRATES TO EDC v1.0.0.</b><br>
 * This works around the fact that {@code BaseRevocationListService.downloadStatusListCredential()}
 * treats a 5xx <em>response</em> as a hard failure (it is not a thrown exception, so the global
 * {@code EdcHttpClient} retry policy does not retry it), and a brief statuslist outage therefore flips
 * VCs to {@code VcStatus.ERROR} (-100). The upstream fix is
 * <a href="https://github.com/eclipse-edc/Connector/pull/6049">eclipse-edc/Connector#6049</a>, which
 * ships in <b>EDC 1.0.0</b>. Verified compatible through EDC 0.16.x / 0.17.x / 0.18.x. Once DSE is on
 * EDC 1.0.0, delete the whole {@code resilient-revocation-service} module and its launcher wiring.</p>
 *
 * <p><b>Why only 5xx is retried here:</b> the injected {@code EdcHttpClient.execute(request)} already
 * wraps the call in the global EDC retry policy (Failsafe), which retries thrown {@code IOException}s
 * (DNS/routing/timeouts/connection resets) up to {@code edc.core.retry.retries.max} times. Re-retrying
 * those in this outer loop would multiply attempts (e.g. 5 inner × 4 outer = 24 connection attempts per
 * credential) and hold the watchdog transaction open far longer. A 5xx, by contrast, is a successfully
 * received response that Failsafe does <em>not</em> retry, so this outer loop is the only retry for it —
 * exactly the gap this interim fix needs to close. Transport exceptions are still caught (so they cannot
 * escape and cancel the scheduled watchdog) but are normalized to a non-retryable failed {@code Result}.</p>
 *
 * @deprecated interim fix; remove when DSE migrates to EDC v1.0.0 (upstream eclipse-edc/Connector#6049).
 */
@Deprecated(forRemoval = true) // remove when DSE adopts EDC v1.0.0 — upstream fix: eclipse-edc/Connector#6049
public class RetryingBitstringStatusListRevocationService extends BitstringStatusListRevocationService {
    // Defensive upper bound on configured retries (prevents loop overflow / runaway retries).
    private static final int MAX_ALLOWED_RETRIES = 100;

    // Shape of the superclass download-failure message: "<PREFIX><url><SEPARATOR><statusCode> <reason>".
    private static final String DOWNLOAD_FAILURE_PREFIX = "Failed to download status list credential from ";
    private static final String STATUS_SEPARATOR = ": ";
    private static final int HTTP_STATUS_DIGITS = 3;

    private final Monitor monitor;
    private final int maxRetries;
    private final long backoffMillis;

    public RetryingBitstringStatusListRevocationService(ObjectMapper mapper,
                                                        long cacheValidity,
                                                        Collection<String> acceptedContentTypes,
                                                        EdcHttpClient httpClient,
                                                        TokenValidationService tokenValidationService,
                                                        DidPublicKeyResolver didPublicKeyResolver,
                                                        Monitor monitor,
                                                        int maxRetries,
                                                        long backoffMillis) {
        super(mapper, cacheValidity, acceptedContentTypes, httpClient, tokenValidationService, didPublicKeyResolver);
        this.monitor = monitor;
        // Clamp to a sane upper bound: guards against overflow in the retry loop and runaway retries if
        // misconfigured. Negative values are already rejected by the @Setting(min=0) in the extension.
        this.maxRetries = Math.max(0, Math.min(maxRetries, MAX_ALLOWED_RETRIES));
        this.backoffMillis = backoffMillis;
    }

    /**
     * Classifies a download failure as transient (worth retrying by this outer loop).
     *
     * <p>Only an HTTP <b>5xx</b> is transient here. The superclass surfaces a non-2xx response as
     * {@code "Failed to download status list credential from <url>: <statusCode> <reason>"}; we extract
     * the real 3-digit status (the token immediately after the last {@code ": "}) and retry only 5xx —
     * this avoids false positives from digits embedded in the URL (e.g. {@code /status/500}) and covers
     * 501/507 etc. Transport/IO failures are deliberately <em>not</em> retried here because the global
     * {@code EdcHttpClient} retry policy has already retried them before they surface.</p>
     */
    static boolean isTransient(String failureDetail) {
        if (failureDetail == null) {
            return false;
        }
        var status = extractHttpStatus(failureDetail);
        return status != null && status >= 500 && status < 600;
    }

    /**
     * Extracts the HTTP status code from the superclass download-failure message, which ends with
     * {@code "...: <statusCode> <reason>"}. Returns {@code null} if the message is not an HTTP failure.
     */
    private static Integer extractHttpStatus(String failureDetail) {
        if (!failureDetail.contains(DOWNLOAD_FAILURE_PREFIX)) {
            return null;
        }
        var colon = failureDetail.lastIndexOf(STATUS_SEPARATOR);
        if (colon < 0 || colon + STATUS_SEPARATOR.length() >= failureDetail.length()) {
            return null;
        }
        var tail = failureDetail.substring(colon + STATUS_SEPARATOR.length()).trim();
        var space = tail.indexOf(' ');
        var token = space < 0 ? tail : tail.substring(0, space);
        if (token.length() != HTTP_STATUS_DIGITS || !token.chars().allMatch(Character::isDigit)) {
            return null;
        }
        var code = Integer.parseInt(token);
        return code >= 100 && code < 600 ? code : null;
    }

    @Override
    protected Result<BitstringStatusListCredential> getCredential(String credentialUrl) {
        var result = safeGetCredential(credentialUrl);
        if (result.succeeded() || !isTransient(result.getFailureDetail())) {
            return result;
        }

        var retriesPerformed = 0;
        for (var attempt = 1; attempt <= maxRetries && isTransient(result.getFailureDetail()); attempt++) {
            monitor.warning("Transient 5xx downloading credential from %s (attempt %d/%d): %s"
                    .formatted(credentialUrl, attempt, maxRetries, result.getFailureDetail()));
            if (!sleepBackoff(credentialUrl)) {
                return result;
            }
            retriesPerformed = attempt;
            result = safeGetCredential(credentialUrl);
            if (result.succeeded()) {
                monitor.info("Successfully downloaded credential from %s after %d retries"
                        .formatted(credentialUrl, attempt));
                return result;
            }
        }

        // Report the retries actually performed — the loop can stop early when a failure flips from
        // transient to non-transient (e.g. 503 then 404), so this may be fewer than maxRetries.
        monitor.warning("Giving up downloading credential from %s after %d retries: %s"
                .formatted(credentialUrl, retriesPerformed, result.getFailureDetail()));
        return result;
    }

    /**
     * Calls the superclass download but never lets an exception escape: in EDC 0.16.x the base
     * {@code getCredential} only converts {@link IllegalArgumentException} to a failed {@link Result},
     * while a transport {@code IOException} surfaces as a thrown
     * {@link org.eclipse.edc.spi.EdcException}. If that escaped, it would propagate out of the watchdog's
     * scheduled task and cancel all future executions, so we normalize any throwable into a failed
     * {@link Result}.
     *
     * <p>The resulting failure is intentionally <em>not</em> marked as a retryable 5xx: the global
     * {@code EdcHttpClient} retry policy has already retried transport exceptions before they reach here,
     * and a parse failure of an already-downloaded body (Jackson {@code JsonProcessingException}, itself
     * an {@link IOException}) is deterministic. Re-retrying either in the outer loop would be wasteful.</p>
     */
    private Result<BitstringStatusListCredential> safeGetCredential(String credentialUrl) {
        try {
            return super.getCredential(credentialUrl);
        } catch (RuntimeException ex) {
            var cause = rootCause(ex);
            // Non-retryable by construction: the message carries no HTTP status token, so isTransient()
            // returns false and the outer loop will not retry it.
            return Result.failure("Status list credential download failed for %s (%s: %s)"
                    .formatted(credentialUrl, cause.getClass().getSimpleName(), cause.getMessage()));
        }
    }

    private static Throwable rootCause(Throwable t) {
        var current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * Sleeps for the configured backoff. Returns {@code false} if the thread was interrupted (caller should abort).
     */
    private boolean sleepBackoff(String credentialUrl) {
        if (backoffMillis <= 0) {
            return true;
        }
        try {
            Thread.sleep(backoffMillis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            monitor.warning("Retry interrupted for %s".formatted(credentialUrl));
            return false;
        }
    }
}
