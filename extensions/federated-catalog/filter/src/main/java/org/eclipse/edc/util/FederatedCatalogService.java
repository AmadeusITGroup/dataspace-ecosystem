package org.eclipse.edc.util;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import org.eclipse.dse.iam.policy.CatalogDiscoveryPolicyContext;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.spi.exception.AuthenticationFailedException;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.eclipse.edc.web.spi.exception.EdcApiException;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;
import org.eclipse.edc.web.spi.exception.NotAuthorizedException;
import org.eclipse.edc.web.spi.exception.ObjectConflictException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.eclipse.edc.FilterConstants.VC_CLAIMS;
import static org.eclipse.edc.VcCatalogFilterExtension.CATALOG_FILTER_SCOPE;


public class FederatedCatalogService {

    private final HttpClient httpClient;
    private final PolicyEngine policyEngine;
    private final Monitor monitor;
    private final AuthorityCatalogFilterDidResolver didResolver;
    private final TypeTransformerRegistry transformerRegistry;
    private final JsonLd jsonLd;

    public FederatedCatalogService(PolicyEngine policyEngine, Monitor monitor, AuthorityCatalogFilterDidResolver didResolver, TypeTransformerRegistry transformerRegistry, JsonLd jsonLd, HttpClient httpClient) {
        this.policyEngine = policyEngine;
        this.httpClient = httpClient;
        this.monitor = monitor;
        this.didResolver = didResolver;
        this.transformerRegistry = transformerRegistry;
        this.jsonLd = jsonLd;
    }

    public Collection<Catalog> fetchAndFilterCatalog(ClaimToken participantVcs, String participantDid, QuerySpec query) throws Exception {
        CatalogDiscoveryPolicyContext policyContext = createContext(participantVcs, participantDid);
        Collection<Catalog> catalogs = retrieveCatalog(query);
        if (catalogs == null || catalogs.isEmpty()) {
            monitor.warning("No catalogs retrieved from the remote source");
            return Collections.emptyList();
        }
        return filterCatalog(catalogs, policyContext, participantDid);
    }

    protected Collection<Catalog> filterCatalog(Collection<Catalog> catalogs, CatalogDiscoveryPolicyContext policyContext, String participantDid) {
        Collection<Catalog> filteredCatalogs = new ArrayList<>();
        for (Catalog catalog : catalogs) {
            if (catalog == null) {
                monitor.warning("Encountered null catalog entry, skipping");
                continue;
            }
            if (Objects.equals(catalog.getParticipantId(), participantDid)) {
                filteredCatalogs.add(catalog);
                continue;
            }
            
            List<Dataset> filteredDatasets = catalog.getDatasets().stream()
                    .filter(dataset -> evaluateDatasetPolicy(dataset, policyContext))
                    .collect(Collectors.toList());

            Catalog filteredCatalog = Catalog.Builder.newInstance()
                    .id(catalog.getId())
                    .participantId(catalog.getParticipantId())
                    .datasets(filteredDatasets)
                    .dataServices(catalog.getDataServices())
                    .properties(catalog.getProperties())
                    .build();

            filteredCatalogs.add(filteredCatalog);
        }
        return filteredCatalogs;
    }

    private boolean evaluateDatasetPolicy(Dataset dataset, CatalogDiscoveryPolicyContext policyContext) {
        List<Policy> policies = dataset.getOffers().values().stream().toList();

        if (policies.isEmpty()) {
            monitor.debug(String.format("Dataset %s has no policies", dataset.getId()));
            return true;
        }

        for (Policy policy : policies) {
            Result<Void> evaluationResult = policyEngine.evaluate(policy, policyContext);
            if (!evaluationResult.succeeded()) {
                monitor.debug(String.format("Dataset %s failed policy evaluation", dataset.getId()));
                return false;
            }
        }

        monitor.debug(String.format("Dataset %s passed all policy evaluations", dataset.getId()));
        return true;
    }

    /**
     * Builds the policy evaluation context for the requesting participant.
     * <p>
     * Since EDC 0.16.0 {@link ParticipantAgent} requires a non-null identity, so the requesting participant's DID is
     * passed explicitly rather than relying on the two-argument constructor, which derives the identity from the
     * attributes map.
     *
     * @param participantVcs the verified claims of the requesting participant
     * @param participantDid the DID of the requesting participant, used as the agent identity
     * @return the discovery policy context
     */
    protected CatalogDiscoveryPolicyContext createContext(ClaimToken participantVcs, String participantDid) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(VC_CLAIMS, participantVcs.getClaim(VC_CLAIMS));
        ParticipantAgent agent = new ParticipantAgent(participantDid, claims, Collections.emptyMap());
        return new CatalogDiscoveryPolicyContext(agent);
    }

    /**
     * Queries the federated catalog of the authority (management v4 {@code POST /v4/catalogs/request}).
     * <p>
     * Failures are EDC exceptions, written by the EDC exception mappers of the catalog filter API: the federated
     * catalog URL cannot be resolved or the federated catalog cannot be reached: {@link BadGatewayException} (502);
     * the federated catalog replies with an error: see {@link #federatedCatalogError(int, String)}.
     */
    protected Collection<Catalog> retrieveCatalog(QuerySpec query) throws InterruptedException {
        String catalogUrl = didResolver.fetchCatalogFilterUrl()
                .orElseThrow(failure -> new BadGatewayException("Could not resolve the federated catalog url: " + failure.getFailureDetail()));

        JsonObject jsonBody = null;
        if (query != null) {
            JsonObject expandedBody = transformerRegistry.transform(query, JsonObject.class)
                    .orElseThrow(failure -> new EdcException("QuerySpec transform failed: " + failure.getFailureDetail()));
            // the federated catalog management v4 API expects the compacted form ("@type": "QuerySpec")
            jsonBody = jsonLd.compact(expandedBody, CATALOG_FILTER_SCOPE)
                    .orElseThrow(failure -> new EdcException("QuerySpec compaction failed: " + failure.getFailureDetail()));
        }

        HttpResponse<String> response = send(catalogUrl, jsonBody);
        if (response.statusCode() != 200) {
            monitor.warning("The federated catalog %s replied HTTP %d: %s".formatted(catalogUrl, response.statusCode(), response.body()));
            throw federatedCatalogError(response.statusCode(), response.body());
        }

        try (JsonReader reader = Json.createReader(new StringReader(response.body()))) {
            JsonArray array = reader.readArray();
            List<Catalog> catalogs = new ArrayList<>(array.size());

            for (var jsonValue : array) {
                if (!(jsonValue instanceof JsonObject jsonObject)) {
                    monitor.warning("Skipping non-object entry in catalog array");
                    continue;
                }

                var expanded = jsonLd.expand(jsonObject);

                if (expanded.failed()) {
                    monitor.warning("Issues with JSON expansion");
                    continue;
                }
                var result = transformerRegistry.transform(expanded.getContent(), Catalog.class);
                if (result.succeeded()) {
                    catalogs.add(result.getContent());
                } else {
                    monitor.warning("Failed to transform catalog entry: " + String.join("; ", result.getFailureMessages()));
                }
            }
            return catalogs;
        }
    }

    /**
     * Maps an error reply of the federated catalog to the upstream EDC exception that the EDC exception mapper turns
     * into the same HTTP status, keeping the federated catalog messages: the {@code message} fields of its EDC error body
     * ({@code [{"message": ..., "type": ...}]}). Any other body (e.g. the Jetty error page of a 500, which EDC sends
     * without an entity) is not forwarded; {@link #retrieveCatalog(QuerySpec)} logs it.
     * <p>
     * Only 400, 401, 403 and 409 have an upstream EDC exception that keeps these messages. Any other status (e.g. 404,
     * 500, 503) has no upstream exception that keeps the federated catalog message ({@code ObjectNotFoundException} has a
     * fixed text), so it is reported as {@link BadGatewayException} (502), the EDC exception for a failed call to an
     * external endpoint, with the federated catalog status and messages in its message.
     */
    static EdcApiException federatedCatalogError(int status, @Nullable String body) {
        var messages = edcErrorMessages(body);
        var keptMessages = messages.isEmpty()
                ? List.of("The federated catalog replied HTTP %d".formatted(status))
                : messages;
        return switch (status) {
            case 400 -> new InvalidRequestException(keptMessages);
            case 401 -> new AuthenticationFailedException(String.join(", ", keptMessages));
            case 403 -> new NotAuthorizedException(String.join(", ", keptMessages));
            case 409 -> new ObjectConflictException(keptMessages);
            default -> new BadGatewayException(messages.isEmpty()
                    ? "The federated catalog query failed with HTTP %d".formatted(status)
                    : "The federated catalog query failed with HTTP %d: %s".formatted(status, String.join(", ", messages)));
        };
    }

    private HttpResponse<String> send(String catalogUrl, @Nullable JsonObject body) throws InterruptedException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(catalogUrl));
            if (body != null) {
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
            } else {
                builder.POST(HttpRequest.BodyPublishers.noBody());
            }
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | IllegalArgumentException e) {
            var message = "Could not reach the federated catalog %s: %s".formatted(catalogUrl, e);
            monitor.warning(message, e);
            throw new BadGatewayException(message);
        }
    }

    /**
     * The {@code message} fields of an EDC error body; empty for any other body.
     */
    private static List<String> edcErrorMessages(@Nullable String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            if (reader.readValue() instanceof JsonArray errors) {
                return errors.stream()
                        .filter(JsonObject.class::isInstance)
                        .map(error -> error.asJsonObject().get("message"))
                        .filter(JsonString.class::isInstance)
                        .map(message -> ((JsonString) message).getString())
                        .toList();
            }
            return List.of();
        } catch (JsonException e) {
            return List.of();
        }
    }
}