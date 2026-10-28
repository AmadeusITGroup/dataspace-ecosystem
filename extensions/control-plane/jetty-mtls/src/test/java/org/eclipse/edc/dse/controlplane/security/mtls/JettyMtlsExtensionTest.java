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

import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.web.jetty.JettyService;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.dse.controlplane.security.mtls.JettyMtlsExtension.clientAuthCallback;
import static org.eclipse.edc.dse.controlplane.security.mtls.JettyMtlsExtension.parseCsv;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class JettyMtlsExtensionTest {

    private static final String TRUSTSTORE_PASSWORD = "changeit";

    private final Monitor monitor = mock(Monitor.class);
    private final JettyService jettyService = mock(JettyService.class);
    private final WebService webService = mock(WebService.class);
    private final PortMappingRegistry portMappingRegistry = mock(PortMappingRegistry.class);
    private Server server;

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(JettyService.class, jettyService);
        context.registerService(WebService.class, webService);
        context.registerService(PortMappingRegistry.class, portMappingRegistry);
        when(portMappingRegistry.getAll()).thenReturn(List.of(
                new PortMapping("default", 8080, "/api"),
                new PortMapping("management", 8181, "/management"),
                new PortMapping("protocol", 8282, "/protocol")));
        when(context.getMonitor()).thenReturn(monitor);
        when(monitor.withPrefix(anyString())).thenReturn(monitor);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void initialize_shouldNotConfigureMtls_whenTruststorePathUnset(ServiceExtensionContext context, ObjectFactory factory) {
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of()));

        var extension = factory.constructInstance(JettyMtlsExtension.class);
        extension.initialize(context);

        assertThat(extension.name()).isEqualTo(JettyMtlsExtension.NAME);
        assertThatNoException().isThrownBy(extension::prepare);
        verifyNoInteractions(jettyService, webService, portMappingRegistry);
    }

    @Test
    void prepare_shouldFailStartup_whenMtlsContextUnknownWithAnyTrustedClientOptIn(ServiceExtensionContext context, ObjectFactory factory,
                                                                                  @TempDir Path tempDir) throws Exception {
        var config = mtlsConfig(tempDir);
        config.put("edc.web.https.mtls.contexts", "managment");
        config.put("edc.web.https.mtls.allow.any.trusted.client", "true");
        config.put("edc.web.https.mtls.client.auth.required", "true");
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(config));

        var extension = factory.constructInstance(JettyMtlsExtension.class);
        extension.initialize(context);

        assertThatThrownBy(extension::prepare)
                .isInstanceOf(EdcException.class)
                .hasMessageContaining("managment")
                .hasMessageContaining("management");
    }

    @Test
    void prepare_shouldFailStartup_whenAnyMtlsContextUnknownWithSanAllowlist(ServiceExtensionContext context, ObjectFactory factory,
                                                                            @TempDir Path tempDir) throws Exception {
        var config = mtlsConfig(tempDir);
        config.put("edc.web.https.mtls.contexts", "management,managment");
        config.put("edc.web.https.mtls.client.san.allowlist", "bff-client.svc.cluster.local");
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(config));

        var extension = factory.constructInstance(JettyMtlsExtension.class);
        extension.initialize(context);

        assertThatThrownBy(extension::prepare)
                .isInstanceOf(EdcException.class)
                .hasMessageContaining("[managment]");
    }

    @Test
    void prepare_shouldSucceed_whenAllMtlsContextsRegistered(ServiceExtensionContext context, ObjectFactory factory,
                                                             @TempDir Path tempDir) throws Exception {
        var config = mtlsConfig(tempDir);
        config.put("edc.web.https.mtls.contexts", "management,protocol");
        config.put("edc.web.https.mtls.client.san.allowlist", "bff-client.svc.cluster.local");
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(config));

        var extension = factory.constructInstance(JettyMtlsExtension.class);
        extension.initialize(context);

        assertThatNoException().isThrownBy(extension::prepare);
    }

    @Test
    void initialize_shouldRegisterClientAuthAndSanFilter_whenAllowlistSet(ServiceExtensionContext context, ObjectFactory factory,
                                                                          @TempDir Path tempDir) throws Exception {
        var config = mtlsConfig(tempDir);
        config.put("edc.web.https.mtls.contexts", "management,custom");
        config.put("edc.web.https.mtls.client.san.allowlist", "bff-client.svc.cluster.local");
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(config));

        factory.constructInstance(JettyMtlsExtension.class).initialize(context);

        verify(jettyService).addConnectorConfigurationCallback(any());
        verify(webService).registerResource(eq("management"), isA(ClientCertificateSanFilter.class));
        verify(webService).registerResource(eq("custom"), isA(ClientCertificateSanFilter.class));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", ",", " , ," })
    void initialize_shouldFailStartup_whenMtlsContextsBlank(String contexts, ServiceExtensionContext context, ObjectFactory factory,
                                                            @TempDir Path tempDir) throws Exception {
        var config = mtlsConfig(tempDir);
        config.put("edc.web.https.mtls.contexts", contexts);
        config.put("edc.web.https.mtls.client.san.allowlist", "bff-client.svc.cluster.local");
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(config));

        var extension = factory.constructInstance(JettyMtlsExtension.class);

        assertThatThrownBy(() -> extension.initialize(context))
                .isInstanceOf(EdcException.class)
                .hasMessageContaining("edc.web.https.mtls.contexts");
        verifyNoInteractions(jettyService, webService);
    }

    @Test
    void initialize_shouldFailStartup_whenAllowlistEmptyWithoutOptIn(ServiceExtensionContext context, ObjectFactory factory,
                                                                     @TempDir Path tempDir) throws Exception {
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(mtlsConfig(tempDir)));

        var extension = factory.constructInstance(JettyMtlsExtension.class);

        assertThatThrownBy(() -> extension.initialize(context))
                .isInstanceOf(EdcException.class)
                .hasMessageContaining("edc.web.https.mtls.client.san.allowlist");
        verifyNoInteractions(jettyService, webService);
    }

    @Test
    void initialize_shouldWarnAndSkipSanFilter_whenAnyTrustedClientOptedIn(ServiceExtensionContext context, ObjectFactory factory,
                                                                           @TempDir Path tempDir) throws Exception {
        var config = mtlsConfig(tempDir);
        config.put("edc.web.https.mtls.allow.any.trusted.client", "true");
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(config));

        factory.constructInstance(JettyMtlsExtension.class).initialize(context);

        verify(jettyService).addConnectorConfigurationCallback(any());
        verify(webService, never()).registerResource(anyString(), any());
        verify(monitor, times(1)).warning(anyString());
    }

    @Test
    void initialize_shouldAlsoWarnAboutAnonymousClients_whenOptedInAndClientAuthNotRequired(ServiceExtensionContext context, ObjectFactory factory,
                                                                                            @TempDir Path tempDir) throws Exception {
        var config = mtlsConfig(tempDir);
        config.put("edc.web.https.mtls.allow.any.trusted.client", "true");
        config.put("edc.web.https.mtls.client.auth.required", "false");
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(config));

        factory.constructInstance(JettyMtlsExtension.class).initialize(context);

        verify(monitor, times(2)).warning(anyString());
    }

    @Test
    void initialize_shouldThrowWithPathAndType_whenTruststoreCannotBeLoaded(ServiceExtensionContext context, ObjectFactory factory,
                                                                            @TempDir Path tempDir) {
        var missingPath = tempDir.resolve("missing.p12").toString();
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(Map.of(
                "edc.web.https.truststore.path", missingPath,
                "edc.web.https.mtls.client.san.allowlist", "bff-client.svc.cluster.local")));

        var extension = factory.constructInstance(JettyMtlsExtension.class);

        assertThatThrownBy(() -> extension.initialize(context))
                .isInstanceOf(EdcException.class)
                .hasMessageContaining(missingPath)
                .hasMessageContaining("PKCS12");
        verifyNoInteractions(jettyService, webService);
    }

    @Test
    void parseCsv_shouldTrimAndDropBlankEntries() {
        assertThat(parseCsv(" management ,,protocol")).isEqualTo(Set.of("management", "protocol"));
        assertThat(parseCsv(" cn-one , ,cn-two")).isEqualTo(Set.of("cn-one", "cn-two"));
        assertThat(parseCsv("")).isEmpty();
        assertThat(parseCsv(null)).isEmpty();
    }

    @Test
    void clientAuthCallback_shouldEnableNeedClientAuth_onlyOnMatchingContext() throws Exception {
        server = new Server();
        var trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);

        var callback = clientAuthCallback(trustStore, Set.of("management"), true, monitor);

        var managementConnector = sslConnector("management");
        var protocolConnector = plainConnector("protocol");

        callback.accept(managementConnector);
        callback.accept(protocolConnector);

        var managementSsl = (SslContextFactory.Server) managementConnector.getConnectionFactory(SslConnectionFactory.class).getSslContextFactory();
        assertThat(managementSsl.getNeedClientAuth()).isTrue();
        assertThat(managementSsl.getTrustStore()).isSameAs(trustStore);

        // non-configured, plain-HTTP context must be left untouched
        assertThat(protocolConnector.getConnectionFactory(SslConnectionFactory.class)).isNull();
    }

    @Test
    void clientAuthCallback_shouldUseWantClientAuth_whenNotRequired() throws Exception {
        server = new Server();
        var trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);

        var callback = clientAuthCallback(trustStore, Set.of("management"), false, monitor);
        var managementConnector = sslConnector("management");

        callback.accept(managementConnector);

        var managementSsl = (SslContextFactory.Server) managementConnector.getConnectionFactory(SslConnectionFactory.class).getSslContextFactory();
        assertThat(managementSsl.getNeedClientAuth()).isFalse();
        assertThat(managementSsl.getWantClientAuth()).isTrue();
    }

    @Test
    void clientAuthCallback_shouldThrow_whenMtlsContextHasNoTlsConnector() throws Exception {
        server = new Server();
        var trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);

        var callback = clientAuthCallback(trustStore, Set.of("management"), true, monitor);
        var plainManagementConnector = plainConnector("management");

        assertThatThrownBy(() -> callback.accept(plainManagementConnector))
                .isInstanceOf(EdcException.class)
                .hasMessageContaining("management");
    }

    private Map<String, String> mtlsConfig(Path tempDir) throws Exception {
        var truststorePath = tempDir.resolve("truststore.p12");
        var trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        try (var out = Files.newOutputStream(truststorePath)) {
            trustStore.store(out, TRUSTSTORE_PASSWORD.toCharArray());
        }
        var config = new HashMap<String, String>();
        config.put("edc.web.https.truststore.path", truststorePath.toString());
        config.put("edc.web.https.truststore.password", TRUSTSTORE_PASSWORD);
        return config;
    }

    private ServerConnector sslConnector(String name) {
        var sslContextFactory = new SslContextFactory.Server();
        var connector = new ServerConnector(server, new SslConnectionFactory(sslContextFactory, "HTTP/1.1"));
        connector.setName(name);
        return connector;
    }

    private ServerConnector plainConnector(String name) {
        var connector = new ServerConnector(server);
        connector.setName(name);
        return connector;
    }

}
