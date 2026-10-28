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
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientCertificateSanFilterTest {

    private static final String X509_CERTIFICATE_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";

    private final Monitor monitor = mock(Monitor.class);
    private final ContainerRequestContext requestContext = mock(ContainerRequestContext.class);

    @Test
    void filter_shouldPassThrough_whenSanIsAllowed() throws Exception {
        var filter = filter(Set.of("bff-client.svc.cluster.local"));
        givenClientCertificate("bff-client.svc.cluster.local");

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void filter_shouldPassThrough_whenAnyOfMultipleSansIsAllowed() throws Exception {
        var filter = filter(Set.of("bff-client.svc.cluster.local"));
        givenClientCertificate("other.svc.cluster.local", "bff-client.svc.cluster.local");

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
    }

    @ParameterizedTest
    @CsvSource({
            "cr-bff.svc.cluster.local, CR-BFF.svc.cluster.local",
            "CR-BFF.svc.cluster.local, cr-bff.svc.cluster.local"
    })
    void filter_shouldPassThrough_whenSanMatchesIgnoringCase(String allowedSan, String certificateSan) throws Exception {
        var filter = filter(Set.of(allowedSan));
        givenClientCertificate(certificateSan);

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void filter_shouldReject_whenSanIsNotAllowed() throws Exception {
        var filter = filter(Set.of("bff-client.svc.cluster.local"));
        givenClientCertificate("someone-else.svc.cluster.local");

        filter.filter(requestContext);

        assertForbidden();
    }

    @Test
    void filter_shouldReject_whenCertificateHasNoSan() throws Exception {
        var filter = filter(Set.of("bff-client.svc.cluster.local"));
        var certificate = mock(X509Certificate.class);
        when(certificate.getSubjectAlternativeNames()).thenReturn(null);
        when(requestContext.getProperty(X509_CERTIFICATE_ATTRIBUTE)).thenReturn(new X509Certificate[] { certificate });

        filter.filter(requestContext);

        assertForbidden();
    }

    @Test
    void filter_shouldReject_whenNoClientCertificatePresent() {
        var filter = filter(Set.of("bff-client.svc.cluster.local"));
        when(requestContext.getProperty(X509_CERTIFICATE_ATTRIBUTE)).thenReturn(null);

        filter.filter(requestContext);

        assertForbidden();
    }

    @Test
    void filter_shouldReject_whenCertificateChainIsEmpty() {
        var filter = filter(Set.of("bff-client.svc.cluster.local"));
        when(requestContext.getProperty(X509_CERTIFICATE_ATTRIBUTE)).thenReturn(new X509Certificate[0]);

        filter.filter(requestContext);

        assertForbidden();
    }

    @Test
    void filter_shouldReject_whenOnlyNonDnsSanMatchesAllowlist() throws Exception {
        var filter = filter(Set.of("10.0.0.1"));
        var certificate = mock(X509Certificate.class);
        // type 7 = iPAddress; only dNSName entries are authorized
        when(certificate.getSubjectAlternativeNames()).thenReturn(List.of(List.of(7, "10.0.0.1")));
        when(requestContext.getProperty(X509_CERTIFICATE_ATTRIBUTE)).thenReturn(new X509Certificate[] { certificate });

        filter.filter(requestContext);

        assertForbidden();
    }

    @Test
    void filter_shouldReject_whenSanExtensionCannotBeParsed() throws Exception {
        var filter = filter(Set.of("bff-client.svc.cluster.local"));
        var certificate = mock(X509Certificate.class);
        when(certificate.getSubjectAlternativeNames()).thenThrow(new CertificateParsingException("malformed"));
        when(requestContext.getProperty(X509_CERTIFICATE_ATTRIBUTE)).thenReturn(new X509Certificate[] { certificate });

        filter.filter(requestContext);

        assertForbidden();
    }

    private ClientCertificateSanFilter filter(Set<String> allowedSans) {
        return new ClientCertificateSanFilter(allowedSans, monitor);
    }

    private void givenClientCertificate(String... dnsNames) throws CertificateParsingException {
        var certificate = mock(X509Certificate.class);
        List<List<?>> sans = Arrays.stream(dnsNames)
                .<List<?>>map(dnsName -> List.of(2, dnsName))
                .toList();
        when(certificate.getSubjectAlternativeNames()).thenReturn(sans);
        when(requestContext.getProperty(X509_CERTIFICATE_ATTRIBUTE)).thenReturn(new X509Certificate[] { certificate });
    }

    private void assertForbidden() {
        var captor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    }

}
