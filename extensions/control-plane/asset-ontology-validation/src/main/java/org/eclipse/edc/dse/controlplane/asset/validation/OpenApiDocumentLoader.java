package org.eclipse.edc.dse.controlplane.asset.validation;

import java.io.IOException;
import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class OpenApiDocumentLoader implements AutoCloseable {

    private static final Set<String> CONTENT_TYPES = Set.of(
            "application/json",
            "application/yaml",
            "application/x-yaml",
            "text/yaml",
            "application/vnd.oai.openapi"
    );
    private final Set<String> allowedHosts;
    private final int maxDocumentBytes;
    private final Duration requestTimeout;
    private final boolean allowHttp;
    private final boolean allowPrivateAddresses;
    private final HttpClient httpClient;
    private final java.util.concurrent.ScheduledExecutorService timeoutExecutor;

    OpenApiDocumentLoader(Set<String> allowedHosts, int maxDocumentBytes, Duration requestTimeout) {
        this(allowedHosts, maxDocumentBytes, requestTimeout, false, false);
    }

    OpenApiDocumentLoader(Set<String> allowedHosts, int maxDocumentBytes, Duration requestTimeout,
                          boolean allowHttp, boolean allowPrivateAddresses) {
        this(allowedHosts, maxDocumentBytes, requestTimeout, allowHttp, allowPrivateAddresses, HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    OpenApiDocumentLoader(Set<String> allowedHosts, int maxDocumentBytes, Duration requestTimeout, HttpClient httpClient) {
        this(allowedHosts, maxDocumentBytes, requestTimeout, false, false, httpClient);
    }

    OpenApiDocumentLoader(Set<String> allowedHosts, int maxDocumentBytes, Duration requestTimeout,
                          boolean allowHttp, boolean allowPrivateAddresses, HttpClient httpClient) {
        this.allowedHosts = allowedHosts;
        this.maxDocumentBytes = maxDocumentBytes;
        this.requestTimeout = requestTimeout;
        this.allowHttp = allowHttp;
        this.allowPrivateAddresses = allowPrivateAddresses;
        this.httpClient = httpClient;
        timeoutExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "openapi-document-read-timeout");
            thread.setDaemon(true);
            return thread;
        });
    }

    String load(String location) {
        var uri = parseAndValidate(location);
        var request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json, application/yaml, application/x-yaml, text/yaml")
                .GET()
                .build();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException("OpenAPI download returned HTTP " + response.statusCode());
            }
            validateContentType(response.headers().firstValue("Content-Type").orElse(""));
            try (var body = response.body()) {
                var timedOut = new AtomicBoolean();
                var timeoutTask = timeoutExecutor.schedule(() -> {
                    timedOut.set(true);
                    try {
                        body.close();
                    } catch (IOException ignored) {
                        // The blocked read reports the original close failure below.
                    }
                }, requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
                final byte[] bytes;
                try {
                    bytes = body.readNBytes(Math.addExact(maxDocumentBytes, 1));
                } catch (IOException exception) {
                    if (timedOut.get()) {
                        throw new IllegalArgumentException("OpenAPI response body timed out", exception);
                    }
                    throw exception;
                } finally {
                    timeoutTask.cancel(false);
                }
                if (bytes.length > maxDocumentBytes) {
                    throw new IllegalArgumentException("OpenAPI document exceeds %d bytes".formatted(maxDocumentBytes));
                }
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not download OpenAPI document: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("OpenAPI download was interrupted", exception);
        }
    }

    private URI parseAndValidate(String location) {
        final URI uri;
        try {
            uri = URI.create(location);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("dcat:endpointDescription is not a valid URI", exception);
        }
        var https = "https".equalsIgnoreCase(uri.getScheme());
        var permittedHttp = allowHttp && "http".equalsIgnoreCase(uri.getScheme());
        if ((!https && !permittedHttp) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("OpenAPI location must be an HTTPS URL without user information");
        }

        var host = IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
        if (!allowedHosts.contains(host)) {
            throw new IllegalArgumentException("OpenAPI host '%s' is not allowlisted".formatted(host));
        }
        if (!allowPrivateAddresses) {
            validateAddresses(host);
        }
        return uri;
    }

    private void validateAddresses(String host) {
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("OpenAPI host could not be resolved", exception);
        }
        for (var address : addresses) {
            if (isNonPublic(address)) {
                throw new IllegalArgumentException("OpenAPI host resolves to a non-public address");
            }
        }
    }

    private boolean isNonPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() ||
                address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        var bytes = address.getAddress();
        if (address instanceof Inet6Address) {
            return (bytes[0] & 0xfe) == 0xfc;
        }
        var first = Byte.toUnsignedInt(bytes[0]);
        var second = Byte.toUnsignedInt(bytes[1]);
        return first == 0 || first == 127 || first >= 224 || (first == 100 && second >= 64 && second <= 127);
    }

    private void validateContentType(String header) {
        var contentType = header.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!CONTENT_TYPES.contains(contentType) && !contentType.endsWith("+json") && !contentType.endsWith("+yaml")) {
            throw new IllegalArgumentException("Unsupported OpenAPI content type '%s'".formatted(contentType));
        }
    }

    @Override
    public void close() {
        timeoutExecutor.shutdownNow();
    }
}