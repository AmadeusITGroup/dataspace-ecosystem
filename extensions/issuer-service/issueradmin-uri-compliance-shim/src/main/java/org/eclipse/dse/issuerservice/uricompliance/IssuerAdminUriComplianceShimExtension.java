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

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.jetty.JettyService;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.component.LifeCycle;

import java.util.function.Consumer;

/**
 * Temporary shim (W3, Q5b): lets the IssuerService {@code issueradmin} context accept an ambiguous path encoding (a
 * {@code %25} in a path segment), so that the holders and attestations of the old port-ful DIDs
 * ({@code did:web:<participant>-identityhub%3A8383:api:did}) can be deleted through the Issuer-admin REST API.
 * <p>
 * Their ids contain a literal {@code %3A}, which a path segment carries as {@code %253A}. Jetty 12 rejects such a path
 * with {@code 400}: the connector ({@link UriCompliance#DEFAULT} does not allow
 * {@link UriCompliance.Violation#AMBIGUOUS_PATH_ENCODING}) and, once the connector lets it through, the servlet layer
 * ({@code ServletHandler.isDecodeAmbiguousURIs()} is {@code false}) both refuse it. This extension relaxes exactly
 * these two checks, and only for {@code issueradmin}:
 * <ul>
 *     <li>the connector named {@code issueradmin} (EDC names each connector after its port mapping,
 *     {@code web.http.issueradmin.*}) allows {@link UriCompliance.Violation#AMBIGUOUS_PATH_ENCODING} and no other
 *     violation, with and without TLS;</li>
 *     <li>the servlet handler of the context with the virtual host {@code @issueradmin}, i.e. the context that serves
 *     that connector, decodes ambiguous URIs. It is set at server start, through a {@link LifeCycle.Listener}:
 *     {@link JettyService} creates the contexts after the connectors.</li>
 * </ul>
 * Every other context (issuance, statuslist, default, ...) keeps the Jetty defaults. Jersey decodes a path parameter
 * once, so a {@code %253A8383} on the wire reaches the stores as the stored {@code %3A8383}.
 * <p>
 * TODO remove once the portful holders are deleted in every environment
 */
@Extension(IssuerAdminUriComplianceShimExtension.NAME)
public class IssuerAdminUriComplianceShimExtension implements ServiceExtension {

    public static final String NAME = "DSE issueradmin URI compliance shim (temporary)";

    /**
     * Name of the Issuer-admin API context, port mapping and Jetty connector (upstream
     * {@code IdentityHubApiContext.ISSUERADMIN}).
     */
    static final String ISSUERADMIN = "issueradmin";

    /**
     * Virtual host that {@link JettyService} gives the servlet context of the {@code issueradmin} port mapping.
     */
    static final String ISSUERADMIN_VIRTUAL_HOST = "@" + ISSUERADMIN;

    /**
     * Jetty default URI compliance plus {@link UriCompliance.Violation#AMBIGUOUS_PATH_ENCODING}, nothing else.
     */
    static final UriCompliance ISSUERADMIN_URI_COMPLIANCE = UriCompliance.DEFAULT
            .with("DSE_ISSUERADMIN_PORTFUL_IDS", UriCompliance.Violation.AMBIGUOUS_PATH_ENCODING);

    @Inject
    private JettyService jettyService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        jettyService.addConnectorConfigurationCallback(issuerAdminConnectorCallback(context.getMonitor().withPrefix(NAME)));
    }

    /**
     * Relaxes the URI compliance of the {@code issueradmin} connector and registers the start hook of its servlet
     * context; leaves every other connector as it is.
     */
    static Consumer<ServerConnector> issuerAdminConnectorCallback(Monitor monitor) {
        return connector -> {
            if (!ISSUERADMIN.equals(connector.getName())) {
                return;
            }
            // with TLS the HttpConnectionFactory follows the SslConnectionFactory, without TLS it is the only one
            var httpConnectionFactory = connector.getConnectionFactory(HttpConnectionFactory.class);
            if (httpConnectionFactory == null) {
                throw new EdcException("%s: the '%s' connector has no HttpConnectionFactory, its URI compliance cannot be set"
                        .formatted(NAME, ISSUERADMIN));
            }
            httpConnectionFactory.getHttpConfiguration().setUriCompliance(ISSUERADMIN_URI_COMPLIANCE);
            connector.getServer().addEventListener(new IssuerAdminServletDecoding(monitor));
        };
    }

    /**
     * At server start, before any handler starts, lets the servlet handler of the {@code @issueradmin} context decode
     * ambiguous URIs. Fails the start if that context does not exist exactly once.
     */
    static final class IssuerAdminServletDecoding implements LifeCycle.Listener {

        private final Monitor monitor;

        IssuerAdminServletDecoding(Monitor monitor) {
            this.monitor = monitor;
        }

        @Override
        public void lifeCycleStarting(LifeCycle event) {
            if (!(event instanceof Server server)) {
                return;
            }
            var contexts = server.getDescendants(ServletContextHandler.class).stream()
                    .filter(handler -> handler.getVirtualHosts().contains(ISSUERADMIN_VIRTUAL_HOST))
                    .toList();
            if (contexts.size() != 1) {
                throw new EdcException("%s: expected one servlet context with the virtual host '%s', found %d"
                        .formatted(NAME, ISSUERADMIN_VIRTUAL_HOST, contexts.size()));
            }
            contexts.get(0).getServletHandler().setDecodeAmbiguousURIs(true);
            monitor.warning(("Temporary shim: the '%s' context accepts ambiguous path encoding (%%25 in a path segment) " +
                    "so that the port-ful holders and attestations can be deleted; remove it once they are deleted in every " +
                    "environment").formatted(ISSUERADMIN));
        }
    }
}
