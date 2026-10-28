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

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.jetty.JettyService;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Enables mutual TLS (client certificate authentication) on selected Jetty web contexts.
 *
 * <p>EDC's stock {@code jetty-core} module only ever configures server-side TLS (keystore), it has
 * no config-driven support for a truststore or {@code needClientAuth}. This extension hooks into
 * {@link JettyService#addConnectorConfigurationCallback(java.util.function.Consumer)} — the one
 * public extension point {@code JettyService} exposes — to load a truststore and flip client-auth
 * on for the connectors whose context name matches {@link #mtlsContexts}, after the connector's TLS
 * (keystore) is already wired up by {@code JettyExtension}, and before the Jetty {@code Server} is
 * started.
 *
 * <p>Only the configured contexts (e.g. the management API) require client certificates; the
 * protocol (DSP) context is intentionally left untouched since inter-participant trust is based on
 * DCP/DIDs rather than a shared mTLS CA.
 */
@Extension(value = JettyMtlsExtension.NAME)
public class JettyMtlsExtension implements ServiceExtension {

    public static final String NAME = "DSE Jetty mTLS";

    @Setting(key = "edc.web.https.truststore.path",
            description = "Path to a truststore containing the CA(s) trusted to authenticate client certificates. mTLS is disabled when unset.",
            required = false)
    private String truststorePath;

    @Setting(key = "edc.web.https.truststore.type", description = "Truststore type", defaultValue = "PKCS12")
    private String truststoreType;

    @Setting(key = "edc.web.https.truststore.password", description = "Truststore password", defaultValue = "password")
    private String truststorePassword;

    @Setting(key = "edc.web.https.mtls.contexts",
            description = "Comma-separated web context names (matching web.http.<context>.port) that require client certificate authentication",
            defaultValue = "management")
    private String mtlsContexts;

    @Setting(key = "edc.web.https.mtls.client.auth.required",
            description = "When true the client certificate is mandatory (connection rejected without one); when false it's only " +
                    "requested at TLS level, but requests without one are still rejected (403) by the SAN allowlist when it is set",
            defaultValue = "true")
    private boolean clientAuthRequired;

    @Setting(key = "edc.web.https.mtls.client.san.allowlist",
            description = "Comma-separated list of client certificate Subject Alternative Name (dNSName) entries authorized " +
                    "to call the mTLS contexts. Must be set when mTLS is enabled, unless " +
                    "edc.web.https.mtls.allow.any.trusted.client is true.",
            required = false)
    private String allowedClientSans;

    @Setting(key = "edc.web.https.mtls.allow.any.trusted.client",
            description = "Explicit opt-in to authorize any client certificate chaining to the truststore when the SAN " +
                    "allowlist is empty. Only proves the cert was issued by a trusted CA, not that it belongs to an " +
                    "authorized caller; intended for local development only.",
            defaultValue = "false")
    private boolean allowAnyTrustedClient;

    @Inject
    private JettyService jettyService;

    @Inject
    private WebService webService;

    @Inject
    private PortMappingRegistry portMappingRegistry;

    private Set<String> contexts = Set.of();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor().withPrefix(NAME);

        if (truststorePath == null) {
            monitor.info("edc.web.https.truststore.path not set, mTLS stays disabled");
            return;
        }

        contexts = parseCsv(mtlsContexts);
        if (contexts.isEmpty()) {
            throw new EdcException("%s: edc.web.https.mtls.contexts must contain at least one context when mTLS is enabled"
                    .formatted(NAME));
        }

        var allowedSans = parseCsv(allowedClientSans);
        if (allowedSans.isEmpty() && !allowAnyTrustedClient) {
            throw new EdcException(("%s: mTLS is enabled but edc.web.https.mtls.client.san.allowlist is empty, which would " +
                    "authorize any certificate issued by a trusted CA. Set the allowlist, or set " +
                    "edc.web.https.mtls.allow.any.trusted.client=true to opt in explicitly").formatted(NAME));
        }

        var trustStore = loadTrustStore();

        jettyService.addConnectorConfigurationCallback(clientAuthCallback(trustStore, contexts, clientAuthRequired, monitor));

        if (allowedSans.isEmpty()) {
            monitor.warning(("SAN allowlist is empty and allow.any.trusted.client is enabled: any certificate trusted by " +
                    "the truststore is authorized on context(s) '%s'").formatted(mtlsContexts));
            if (!clientAuthRequired) {
                monitor.warning("client.auth.required is false: clients without a certificate are also accepted on context(s) '%s'"
                        .formatted(mtlsContexts));
            }
            return;
        }

        var filter = new ClientCertificateSanFilter(allowedSans, monitor);
        contexts.forEach(ctx -> webService.registerResource(ctx, filter));
        monitor.info("Client certificate SAN allowlist enforced on context(s) '%s'".formatted(mtlsContexts));
    }

    /**
     * Fails on configured mTLS contexts that match no registered web context (e.g. a typo), which would otherwise
     * silently leave the intended context unprotected. Port mappings are complete here and Jetty is not started yet.
     */
    @Override
    public void prepare() {
        if (contexts.isEmpty()) {
            return;
        }
        var registered = portMappingRegistry.getAll().stream()
                .map(PortMapping::name)
                .collect(Collectors.toSet());
        var unknown = contexts.stream()
                .filter(ctx -> !registered.contains(ctx))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            throw new EdcException("%s: edc.web.https.mtls.contexts contains unknown context(s) %s, registered contexts are %s"
                    .formatted(NAME, unknown, registered.stream().sorted().toList()));
        }
    }

    /**
     * Builds the {@link JettyService} connector callback that enables client certificate
     * authentication on the given context names. Package-private and parameterized (rather than
     * reading instance fields directly) so it can be unit-tested without a full DI context.
     */
    static Consumer<ServerConnector> clientAuthCallback(KeyStore trustStore, Set<String> contexts, boolean clientAuthRequired, Monitor monitor) {
        return (ServerConnector connector) -> {
            if (contexts.contains(connector.getName())) {
                applyClientAuth(connector, trustStore, clientAuthRequired, monitor);
            }
        };
    }

    private static void applyClientAuth(ServerConnector connector, KeyStore trustStore, boolean clientAuthRequired, Monitor monitor) {
        var sslConnectionFactory = connector.getConnectionFactory(SslConnectionFactory.class);
        if (sslConnectionFactory == null) {
            throw new EdcException("%s: context '%s' has no TLS connector (enable internalTls for it before mTLS)"
                    .formatted(NAME, connector.getName()));
        }

        var sslContextFactory = (SslContextFactory.Server) sslConnectionFactory.getSslContextFactory();
        sslContextFactory.setTrustStore(trustStore);
        sslContextFactory.setNeedClientAuth(clientAuthRequired);
        sslContextFactory.setWantClientAuth(!clientAuthRequired);
        monitor.info("Client certificate authentication enabled on context '%s'".formatted(connector.getName()));
    }

    static Set<String> parseCsv(String value) {
        if (value == null) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private KeyStore loadTrustStore() {
        try {
            var trustStore = KeyStore.getInstance(truststoreType);
            try (var stream = new FileInputStream(truststorePath)) {
                trustStore.load(stream, truststorePassword.toCharArray());
            }
            return trustStore;
        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
            throw new EdcException("%s: cannot load truststore '%s' (type %s)".formatted(NAME, truststorePath, truststoreType), e);
        }
    }

}
