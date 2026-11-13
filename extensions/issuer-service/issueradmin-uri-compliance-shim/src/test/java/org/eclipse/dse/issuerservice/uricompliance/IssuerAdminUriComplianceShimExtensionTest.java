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

import org.eclipse.dse.issuerservice.uricompliance.IssuerAdminUriComplianceShimExtension.IssuerAdminServletDecoding;
import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.identityhub.spi.webcontext.IdentityHubApiContext;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.jetty.JettyService;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.dse.issuerservice.uricompliance.IssuerAdminUriComplianceShimExtension.ISSUERADMIN;
import static org.eclipse.dse.issuerservice.uricompliance.IssuerAdminUriComplianceShimExtension.issuerAdminConnectorCallback;
import static org.eclipse.jetty.ee10.servlet.ServletContextHandler.NO_SESSIONS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(DependencyInjectionExtension.class)
class IssuerAdminUriComplianceShimExtensionTest {

    private final Monitor monitor = mock();
    private final Server server = new Server();

    @AfterEach
    void tearDown() throws Exception {
        server.stop();
    }

    @Test
    void issuerAdminContextName_isTheUpstreamOne() {
        assertThat(ISSUERADMIN).isEqualTo(IdentityHubApiContext.ISSUERADMIN).isEqualTo("issueradmin");
    }

    @Test
    void initialize_registersTheConnectorCallback(ServiceExtensionContext context, ObjectFactory objectFactory) {
        var jettyService = mock(JettyService.class);
        context.registerService(JettyService.class, jettyService);

        objectFactory.constructInstance(IssuerAdminUriComplianceShimExtension.class).initialize(context);

        verify(jettyService).addConnectorConfigurationCallback(any());
    }

    @ParameterizedTest(name = "issueradmin connector allows AMBIGUOUS_PATH_ENCODING only, tls={0}")
    @ValueSource(booleans = {false, true})
    void callback_issuerAdminConnector_allowsAmbiguousPathEncodingOnly(boolean tls) {
        var connector = connector(ISSUERADMIN, tls);

        issuerAdminConnectorCallback(monitor).accept(connector);

        var uriCompliance = connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration().getUriCompliance();
        assertThat(uriCompliance.getAllowed()).isEqualTo(EnumSet.of(UriCompliance.Violation.AMBIGUOUS_PATH_ENCODING));
        assertThat(server.getEventListeners()).hasExactlyElementsOfTypes(IssuerAdminServletDecoding.class);
    }

    @ParameterizedTest(name = "connector {0} keeps the Jetty defaults")
    @ValueSource(strings = {"default", "issuance", "statuslist", "Issueradmin", "issueradmin2"})
    void callback_otherConnectors_keepTheJettyDefaults(String name) {
        var connector = connector(name, true);

        issuerAdminConnectorCallback(monitor).accept(connector);

        var uriCompliance = connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration().getUriCompliance();
        assertThat(uriCompliance).isSameAs(UriCompliance.DEFAULT);
        assertThat(server.getEventListeners()).isEmpty();
    }

    @Test
    void callback_failsFast_whenTheIssuerAdminConnectorHasNoHttpConnectionFactory() {
        var connector = new ServerConnector(server, new SslConnectionFactory(new SslContextFactory.Server(), "http/1.1"));
        connector.setName(ISSUERADMIN);

        assertThatThrownBy(() -> issuerAdminConnectorCallback(monitor).accept(connector))
                .isInstanceOf(EdcException.class)
                .hasMessageContaining("HttpConnectionFactory");
    }

    @Test
    void serverStart_decodesAmbiguousUris_onTheIssuerAdminContextOnly() throws Exception {
        var issuerAdmin = context("@" + ISSUERADMIN);
        var issuance = context("@issuance");
        var websocket = context(null);
        server.setHandler(new ContextHandlerCollection(issuerAdmin, issuance, websocket));
        server.addEventListener(new IssuerAdminServletDecoding(monitor));

        server.start();

        assertThat(issuerAdmin.getServletHandler().isDecodeAmbiguousURIs()).isTrue();
        assertThat(issuance.getServletHandler().isDecodeAmbiguousURIs()).isFalse();
        assertThat(websocket.getServletHandler().isDecodeAmbiguousURIs()).isFalse();
    }

    @Test
    void serverStart_failsFast_whenThereIsNoIssuerAdminContext() {
        server.setHandler(new ContextHandlerCollection(context("@issuance"), context(null)));
        server.addEventListener(new IssuerAdminServletDecoding(monitor));

        assertThatThrownBy(server::start)
                .isInstanceOf(EdcException.class)
                .hasMessageContaining("@" + ISSUERADMIN);
    }

    // as JettyService.createConnector builds them: SslConnectionFactory + HttpConnectionFactory with TLS, else HttpConnectionFactory
    private ServerConnector connector(String name, boolean tls) {
        var connector = tls
                ? new ServerConnector(server, new SslConnectionFactory(new SslContextFactory.Server(), "http/1.1"), new HttpConnectionFactory())
                : new ServerConnector(server, new HttpConnectionFactory());
        connector.setName(name);
        return connector;
    }

    // as JettyService.createHandler builds them: context path "/", virtual host "@<context name>"
    private static ServletContextHandler context(String virtualHost) {
        var handler = new ServletContextHandler("/", NO_SESSIONS);
        if (virtualHost != null) {
            handler.setVirtualHosts(List.of(virtualHost));
        }
        return handler;
    }
}
