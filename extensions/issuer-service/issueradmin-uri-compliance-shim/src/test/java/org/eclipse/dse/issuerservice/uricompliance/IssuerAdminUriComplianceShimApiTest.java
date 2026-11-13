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
 *
 */

package org.eclipse.dse.issuerservice.uricompliance;

import org.eclipse.dse.spi.issuerservice.DomainAttestationStore;
import org.eclipse.dse.spi.issuerservice.MembershipAttestationStore;
import org.eclipse.dse.spi.issuerservice.VisibilityAttestationStore;
import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.identityhub.api.configuration.IssuerAdminApiConfigurationExtension;
import org.eclipse.edc.issuerservice.api.DomainAttestationApiController;
import org.eclipse.edc.issuerservice.api.MembershipAttestationApiController;
import org.eclipse.edc.issuerservice.api.VisibilityAttestationApiController;
import org.eclipse.edc.issuerservice.api.admin.holder.v1.unstable.IssuerHolderAdminApiController;
import org.eclipse.edc.issuerservice.spi.holder.HolderService;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.apiversion.ApiVersionService;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.web.jersey.JerseyConfiguration;
import org.eclipse.edc.web.jersey.JerseyRestService;
import org.eclipse.edc.web.jetty.JettyConfiguration;
import org.eclipse.edc.web.jetty.JettyService;
import org.eclipse.edc.web.jetty.PortMappingRegistryImpl;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.dse.issuerservice.uricompliance.IssuerAdminUriComplianceShimExtension.ISSUERADMIN;
import static org.eclipse.edc.identityhub.spi.webcontext.IdentityHubApiContext.ISSUANCE_API;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HTTP-level test of the temporary issueradmin shim on the real EDC stack, with and without TLS: the upstream
 * {@link IssuerAdminApiConfigurationExtension} registers the {@code issueradmin} port mapping from
 * {@code web.http.issueradmin.*} as in the IssuerService, {@link JettyService} creates the connectors and contexts,
 * {@link JerseyRestService} serves the upstream holder controller and the DSE attestation controllers.
 * <p>
 * The requests are written on a raw socket, so the path on the wire is exactly the one in the test
 * ({@code %253A8383}); an HTTP client library could re-encode it.
 */
@ExtendWith(DependencyInjectionExtension.class)
class IssuerAdminUriComplianceShimApiTest {

    private static final String AUTHORITY_DID = "did:web:authority-identityhub:api:did";
    // a retired DSE identity: the port-ful DID, stored with a literal %3A
    private static final String PORTFUL_ID = "did:web:consumer-identityhub%3A8383:api:did";
    // the same id in a path segment: its '%' is sent as %25
    private static final String PORTFUL_ID_ON_THE_WIRE = "did:web:consumer-identityhub%253A8383:api:did";
    private static final String PORTLESS_ID = "did:web:consumer-identityhub:api:did";
    private static final String ISSUERADMIN_PATH = "/api/issueradmin";
    private static final String ISSUANCE_PATH = "/api/issuance";
    private static final String PARTICIPANT_PATH = "/v1beta/participants/" + AUTHORITY_DID;
    private static final String KEYSTORE_PASSWORD = "issuerservice-test";

    @TempDir
    static Path tempDir;
    private static KeyStore keyStore;
    private static SSLContext clientTls;

    private final int issuerAdminPort = getFreePort();
    private final int issuancePort = getFreePort();
    private final Monitor monitor = mock();
    private final HolderService holderService = mock();
    private final HolderService issuanceHolderService = mock();
    private final MembershipAttestationStore membershipStore = mock();
    private final DomainAttestationStore domainStore = mock();
    private final VisibilityAttestationStore visibilityStore = mock();
    private JettyService jetty;
    private boolean tls;

    @BeforeAll
    static void createKeyStore() throws Exception {
        // self-signed certificate for localhost, made with the keytool of the JDK that runs the test
        var keyStoreFile = tempDir.resolve("keystore.p12");
        var keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        var process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "issuerservice", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-validity", "2", "-storetype", "PKCS12",
                "-keystore", keyStoreFile.toString(), "-storepass", KEYSTORE_PASSWORD, "-keypass", KEYSTORE_PASSWORD)
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), UTF_8);
        assertThat(process.waitFor(60, SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();

        keyStore = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(keyStoreFile)) {
            keyStore.load(in, KEYSTORE_PASSWORD.toCharArray());
        }
        var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        clientTls = SSLContext.getInstance("TLS");
        clientTls.init(null, trustManagerFactory.getTrustManagers(), null);
    }

    @AfterEach
    void tearDown() {
        if (jetty != null) {
            jetty.shutdown();
        }
    }

    @ParameterizedTest(name = "issueradmin DELETE holders/<port-ful id>, tls={0}")
    @ValueSource(booleans = {false, true})
    void deleteHolder_portfulId_holderStoreReceivesTheLiteralId(boolean tls, ServiceExtensionContext context, ObjectFactory objectFactory) throws Exception {
        start(tls, context, objectFactory);
        when(holderService.deleteHolder(anyString())).thenReturn(ServiceResult.success());

        var reply = send(issuerAdminPort, "DELETE", ISSUERADMIN_PATH + PARTICIPANT_PATH + "/holders/" + PORTFUL_ID_ON_THE_WIRE);

        assertThat(reply.status()).as(reply.raw()).isEqualTo(204);
        var id = ArgumentCaptor.forClass(String.class);
        verify(holderService).deleteHolder(id.capture());
        assertThat(id.getValue()).isEqualTo(PORTFUL_ID).contains("%3A8383").doesNotContain(":8383");
    }

    @ParameterizedTest(name = "issueradmin DELETE {1}/<port-ful id>, tls={0}")
    @CsvSource({
            "false, attestation-membership", "false, attestation-domain", "false, attestation-visibility",
            "true, attestation-membership", "true, attestation-domain", "true, attestation-visibility"
    })
    void deleteAttestation_portfulId_attestationStoreReceivesTheLiteralId(boolean tls, String attestationApi,
                                                                          ServiceExtensionContext context, ObjectFactory objectFactory) throws Exception {
        start(tls, context, objectFactory);
        when(membershipStore.deleteById(anyString())).thenReturn(StoreResult.success());
        when(domainStore.deleteById(anyString())).thenReturn(StoreResult.success());
        when(visibilityStore.deleteById(anyString())).thenReturn(StoreResult.success());

        var reply = send(issuerAdminPort, "DELETE", ISSUERADMIN_PATH + PARTICIPANT_PATH + "/" + attestationApi + "/" + PORTFUL_ID_ON_THE_WIRE);

        assertThat(reply.status()).as(reply.raw()).isEqualTo(204);
        var id = ArgumentCaptor.forClass(String.class);
        switch (attestationApi) {
            case "attestation-membership" -> verify(membershipStore).deleteById(id.capture());
            case "attestation-domain" -> verify(domainStore).deleteById(id.capture());
            case "attestation-visibility" -> verify(visibilityStore).deleteById(id.capture());
            default -> throw new IllegalArgumentException(attestationApi);
        }
        assertThat(id.getValue()).isEqualTo(PORTFUL_ID).contains("%3A8383").doesNotContain(":8383");
    }

    @ParameterizedTest(name = "issuance DELETE holders/<port-ful id> still 400, tls={0}")
    @ValueSource(booleans = {false, true})
    void portfulId_onAnotherIssuerServiceContext_isStillRejected(boolean tls, ServiceExtensionContext context, ObjectFactory objectFactory) throws Exception {
        start(tls, context, objectFactory);
        var path = ISSUANCE_PATH + PARTICIPANT_PATH + "/holders/" + PORTFUL_ID_ON_THE_WIRE;

        var reply = send(issuancePort, "DELETE", path);

        assertThat(reply.status()).as(reply.raw()).isEqualTo(400);
        // Jetty writes an error page for GET only: the reason is the connector URI compliance
        var errorPage = send(issuancePort, "GET", path, "text/html");
        assertThat(errorPage.status()).as(errorPage.raw()).isEqualTo(400);
        assertThat(errorPage.raw()).contains("Ambiguous URI path encoding");
        verifyNoInteractions(issuanceHolderService);

        // the same controller on that context serves a port-less id
        when(issuanceHolderService.deleteHolder(anyString())).thenReturn(ServiceResult.success());
        assertThat(send(issuancePort, "DELETE", ISSUANCE_PATH + PARTICIPANT_PATH + "/holders/" + PORTLESS_ID).status()).isEqualTo(204);
        verify(issuanceHolderService).deleteHolder(PORTLESS_ID);
    }

    @ParameterizedTest(name = "issueradmin DELETE holders/<port-less id>, tls={0}")
    @ValueSource(booleans = {false, true})
    void deleteHolder_portlessId_stillWorks(boolean tls, ServiceExtensionContext context, ObjectFactory objectFactory) throws Exception {
        start(tls, context, objectFactory);
        when(holderService.deleteHolder(anyString())).thenReturn(ServiceResult.success());

        var reply = send(issuerAdminPort, "DELETE", ISSUERADMIN_PATH + PARTICIPANT_PATH + "/holders/" + PORTLESS_ID);

        assertThat(reply.status()).as(reply.raw()).isEqualTo(204);
        verify(holderService).deleteHolder(PORTLESS_ID);
    }

    private void start(boolean tls, ServiceExtensionContext context, ObjectFactory objectFactory) {
        this.tls = tls;
        when(context.getMonitor()).thenReturn(monitor);
        when(monitor.withPrefix(any())).thenReturn(monitor);
        // the IssuerService reads web.http.issueradmin.* (DSE launchers: WEB_HTTP_ISSUERADMIN_PORT/PATH)
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of(
                "web.http.issueradmin.port", String.valueOf(issuerAdminPort),
                "web.http.issueradmin.path", ISSUERADMIN_PATH)));
        var typeManager = new JacksonTypeManager();
        var portMappings = new PortMappingRegistryImpl();
        context.registerService(TypeManager.class, typeManager);
        context.registerService(PortMappingRegistry.class, portMappings);
        context.registerService(ApiVersionService.class, mock(ApiVersionService.class));

        var issuerAdminApiConfiguration = objectFactory.constructInstance(IssuerAdminApiConfigurationExtension.class);
        issuerAdminApiConfiguration.initialize(context);
        assertThat(portMappings.getAll()).contains(new PortMapping(ISSUERADMIN, issuerAdminPort, ISSUERADMIN_PATH));
        portMappings.register(new PortMapping(ISSUANCE_API, issuancePort, ISSUANCE_PATH));

        jetty = new JettyService(new JettyConfiguration(KEYSTORE_PASSWORD, KEYSTORE_PASSWORD), tls ? keyStore : null, monitor, portMappings);
        context.registerService(JettyService.class, jetty);
        objectFactory.constructInstance(IssuerAdminUriComplianceShimExtension.class).initialize(context);

        // DSE's IssuerService has no issuer-admin authentication/authorization module: the upstream default allows all
        var authorizationService = issuerAdminApiConfiguration.authorizationService();
        var webService = new JerseyRestService(jetty, typeManager, mock(JerseyConfiguration.class), monitor);
        webService.registerResource(ISSUERADMIN, new IssuerHolderAdminApiController(authorizationService, holderService));
        webService.registerResource(ISSUERADMIN, new MembershipAttestationApiController(membershipStore));
        webService.registerResource(ISSUERADMIN, new DomainAttestationApiController(domainStore, "example.com"));
        webService.registerResource(ISSUERADMIN, new VisibilityAttestationApiController(visibilityStore));
        // the same holder API on another IssuerService context, which keeps the Jetty defaults
        webService.registerResource(ISSUANCE_API, new IssuerHolderAdminApiController(authorizationService, issuanceHolderService));
        jetty.start();
        webService.start();
    }

    private Reply send(int port, String method, String path) throws IOException {
        return send(port, method, path, null);
    }

    private Reply send(int port, String method, String path, String accept) throws IOException {
        try (var socket = tls ? clientTls.getSocketFactory().createSocket("localhost", port) : new Socket("localhost", port)) {
            socket.setSoTimeout(10_000);
            var out = socket.getOutputStream();
            var acceptHeader = accept == null ? "" : "Accept: " + accept + "\r\n";
            out.write((method + " " + path + " HTTP/1.1\r\nHost: localhost\r\n" + acceptHeader + "Connection: close\r\n\r\n").getBytes(US_ASCII));
            out.flush();
            var raw = new String(socket.getInputStream().readAllBytes(), UTF_8);
            var statusLine = raw.lines().findFirst().orElseThrow();
            return new Reply(Integer.parseInt(statusLine.split(" ")[1]), raw);
        }
    }

    private record Reply(int status, String raw) {
    }
}
