/*
 *  Copyright (c) 2026 Amadeus
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 */

package org.eclipse.edc.dse.controlplane.security.mtls;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.monitor.Monitor;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A trusted-CA client certificate only proves the cert was issued by a CA in the truststore, not
 * that it belongs to an authorized caller (e.g. any other pod or client sharing that CA would also
 * pass). This filter adds that missing authorization check by rejecting requests whose client
 * certificate has no {@code dNSName} Subject Alternative Name in {@link #allowedSans}.
 *
 * <p>SAN (not CN) is used because RFC 6125 and modern CA policies deprecate the Subject CN for
 * identity verification — many CAs now issue certificates with an empty Subject and rely entirely
 * on the SAN extension. DNS names are compared case-insensitively (RFC 4343, RFC 6125 §6.4.1).
 */
class ClientCertificateSanFilter implements ContainerRequestFilter {

    private static final String X509_CERTIFICATE_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";
    private static final int DNS_NAME_SAN_TYPE = 2;

    private final Set<String> allowedSans;
    private final Monitor monitor;

    ClientCertificateSanFilter(Set<String> allowedSans, Monitor monitor) {
        this.allowedSans = allowedSans.stream()
                .map(san -> san.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.monitor = monitor;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // EDC binds resource instances as HK2 constants, so @Context fields are never injected; Jersey exposes servlet attributes as properties
        var certificates = (X509Certificate[]) requestContext.getProperty(X509_CERTIFICATE_ATTRIBUTE);
        var sans = certificates != null && certificates.length > 0 ? extractDnsNames(certificates[0]) : Set.<String>of();

        if (sans.stream().noneMatch(allowedSans::contains)) {
            monitor.warning("Rejected client certificate with SAN(s) '%s', none in the configured allowlist".formatted(sans));
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build());
        }
    }

    private static Set<String> extractDnsNames(X509Certificate certificate) {
        try {
            var subjectAlternativeNames = certificate.getSubjectAlternativeNames();
            if (subjectAlternativeNames == null) {
                return Set.of();
            }
            return subjectAlternativeNames.stream()
                    .filter(entry -> entry.size() >= 2 && Integer.valueOf(DNS_NAME_SAN_TYPE).equals(entry.get(0)))
                    .map(entry -> ((String) entry.get(1)).toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
        } catch (CertificateParsingException e) {
            return Set.of();
        }
    }

}
