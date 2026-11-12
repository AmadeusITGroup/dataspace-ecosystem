package org.eclipse.edc.dse.controlplane.asset.validation;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiDocumentLoaderTest {

    @Test
    void shouldRejectHostOutsideAllowlist() {
        var loader = new OpenApiDocumentLoader(Set.of("api.example.com"), 1024, Duration.ofSeconds(1));

        assertThatThrownBy(() -> loader.load("https://other.example.com/openapi.yaml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowlisted");
    }

    @Test
    void shouldRejectAllowlistedHostResolvingToPrivateAddress() {
        var loader = new OpenApiDocumentLoader(Set.of("localhost"), 1024, Duration.ofSeconds(1));

        assertThatThrownBy(() -> loader.load("https://localhost/openapi.yaml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-public address");
    }

    @Test
    void shouldRejectHttpByDefault() {
        var loader = new OpenApiDocumentLoader(Set.of("localhost"), 1024, Duration.ofSeconds(1));

        assertThatThrownBy(() -> loader.load("http://localhost/openapi.yaml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be an HTTPS URL");
    }

    @Test
    void shouldLoadLocalHttpDocumentWhenDemoOptionsAreEnabled() throws Exception {
        var content = "openapi: 3.0.3".getBytes(StandardCharsets.UTF_8);
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/openapi.yaml", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/yaml");
            exchange.sendResponseHeaders(200, content.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(content);
            }
        });
        server.start();

        try (var loader = new OpenApiDocumentLoader(Set.of("localhost"), 1024, Duration.ofSeconds(2), true, true)) {
            var document = loader.load("http://localhost:%d/openapi.yaml".formatted(server.getAddress().getPort()));

            assertThat(document).isEqualTo("openapi: 3.0.3");
        } finally {
            server.stop(0);
        }
    }
}