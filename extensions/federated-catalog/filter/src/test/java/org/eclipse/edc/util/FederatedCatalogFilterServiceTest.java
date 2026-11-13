package org.eclipse.edc.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.dse.iam.core.DefaultScopeExtractor;
import org.eclipse.dse.iam.core.RequestCatalogDiscoveryContext;
import org.eclipse.dse.iam.policy.CatalogDiscoveryConstraintFunction;
import org.eclipse.dse.iam.policy.CatalogDiscoveryPolicyContext;
import org.eclipse.dse.iam.policy.PolicyConstants;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.connector.controlplane.transform.odrl.to.JsonObjectToActionTransformer;
import org.eclipse.edc.connector.controlplane.transform.odrl.to.JsonObjectToConstraintTransformer;
import org.eclipse.edc.connector.controlplane.transform.odrl.to.JsonObjectToOperatorTransformer;
import org.eclipse.edc.connector.controlplane.transform.odrl.to.JsonObjectToPermissionTransformer;
import org.eclipse.edc.connector.controlplane.transform.odrl.to.JsonObjectToPolicyTransformer;
import org.eclipse.edc.dse.common.DseNamespaceConfig;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.jsonld.CachedDocumentRegistry;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.jsonld.spi.JsonLdKeywords;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.policy.engine.PolicyEngineImpl;
import org.eclipse.edc.policy.engine.RuleBindingRegistryImpl;
import org.eclipse.edc.policy.engine.ScopeFilter;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.policy.engine.validation.RuleValidator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.protocol.dsp.catalog.transform.to.JsonObjectToDataServiceTransformer;
import org.eclipse.edc.protocol.dsp.catalog.transform.to.JsonObjectToDatasetTransformer;
import org.eclipse.edc.protocol.dsp.catalog.transform.to.JsonObjectToDistributionTransformer;
import org.eclipse.edc.protocol.dsp.catalog.transform.v2025.to.JsonObjectToCatalogTransformer;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.TypeTransformerRegistryImpl;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.transform.transformer.edc.from.JsonObjectFromQuerySpecTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonObjectToCriterionTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonObjectToQuerySpecTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonValueToGenericTypeTransformer;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.StringReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;

import static org.eclipse.dse.iam.policy.CatalogDiscoveryPolicyContext.CATALOG_DISCOVERY_SCOPE;
import static org.eclipse.dse.iam.policy.PolicyConstants.DOMAIN_CREDENTIAL_TYPE;
import static org.eclipse.dse.iam.policy.PolicyConstants.MEMBERSHIP_CREDENTIAL_TYPE;
import static org.eclipse.edc.VcCatalogFilterExtension.CATALOG_FILTER_SCOPE;
import static org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext.NEGOTIATION_SCOPE;
import static org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext.TRANSFER_SCOPE;
import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_CONTEXT_2025_1;
import static org.eclipse.edc.jsonld.spi.Namespaces.EDC_DSPACE_CONTEXT;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_USE_ACTION_ATTRIBUTE;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;
import static org.eclipse.edc.util.IdentityServiceValidator.READ_ALL_CREDENTIAL_SCOPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class FederatedCatalogFilterServiceTest {

    private static final DseNamespaceConfig DEFAULT_CONFIG = new DseNamespaceConfig(
            "https://w3id.org/dse/v0.0.1/ns/",
            "dse-policy",
            "https://w3id.org/dse/policy/"
    );
    private static final PolicyConstants POLICY_CONSTANTS = new PolicyConstants(DEFAULT_CONFIG);
    private static AuthorityCatalogFilterDidResolver didResolverRegistry = mock();
    private static PolicyEngine policyEngine = null;
    private static RuleBindingRegistry registry = new RuleBindingRegistryImpl();
    private static final String ISSUER = "did:web:issuer";
    private static final String SUBJECT = "did:web:subject";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Monitor monitor = mock();
    private static final String PARTICIPANT_DID = "did:web:participant";
    private static ParticipantIdMapper participantIdMapper = mock(ParticipantIdMapper.class);
    private static final String CATALOG_REPLY = "/catalogReply.txt";
    private static TypeTransformerRegistry transformerRegistry = new TypeTransformerRegistryImpl(mock(Monitor.class));
    private final JsonLd jsonLd = new TitaniumJsonLd(monitor);
    private static HttpClient httpClient = mock(HttpClient.class);
    private static HttpResponse<String> httpResponse = mock(HttpResponse.class);
    private static TypeManager typeManager = mock(TypeManager.class);


    @BeforeAll
    static void init() throws IOException, InterruptedException {
        policyEngine = new PolicyEngineImpl(new ScopeFilter(registry), new RuleValidator(registry));
        policyEngine.registerPostValidator(RequestCatalogDiscoveryContext.class, new DefaultScopeExtractor<>(Set.of(READ_ALL_CREDENTIAL_SCOPE)));
        registry.bind(ODRL_USE_ACTION_ATTRIBUTE,  CATALOG_DISCOVERY_SCOPE);
        policyEngine.registerFunction(CatalogDiscoveryPolicyContext.class, Permission.class, new CatalogDiscoveryConstraintFunction<>(POLICY_CONSTANTS));
        registry.dynamicBind(s -> s.startsWith(POLICY_CONSTANTS.getDseRestrictedCatalogDiscoveryConstraint()) ? Set.of(CATALOG_DISCOVERY_SCOPE, NEGOTIATION_SCOPE, TRANSFER_SCOPE) : Set.of());
        registerTransformers();
        when(didResolverRegistry.fetchCatalogFilterUrl()).thenReturn(Result.success("http://example.com/catalog"));
        String reply = loadCatalogFromFile(CATALOG_REPLY);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(reply);
        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenReturn(httpResponse);
    }

    private static void registerTransformers() {
        transformerRegistry.register(new JsonObjectToCatalogTransformer());
        transformerRegistry.register(new JsonObjectToDatasetTransformer());
        transformerRegistry.register(new JsonObjectToPolicyTransformer(participantIdMapper));
        transformerRegistry.register(new JsonObjectToDataServiceTransformer());
        transformerRegistry.register(new JsonObjectToDistributionTransformer());
        transformerRegistry.register(new JsonObjectToQuerySpecTransformer());
        transformerRegistry.register(new JsonObjectToCriterionTransformer(CriterionOperatorRegistryImpl.ofDefaults()));
        transformerRegistry.register(new JsonObjectToPermissionTransformer());
        transformerRegistry.register(new JsonObjectToActionTransformer());
        transformerRegistry.register(new JsonObjectToConstraintTransformer());
        transformerRegistry.register(new JsonObjectToOperatorTransformer());
        transformerRegistry.register(new JsonValueToGenericTypeTransformer(typeManager, JSON_LD));
    }

    @Test
    void filterCatalogSimplePolicies() throws IOException {
        var membershipVc = createVc(MEMBERSHIP_CREDENTIAL_TYPE, Map.of("hello", "world"));
        var domainVc = createVc(DOMAIN_CREDENTIAL_TYPE, Map.of("domain", "route"));
        var credentials = List.of(membershipVc, domainVc);
        ClaimToken tokens = ClaimToken.Builder.newInstance().claim("vc", credentials).build();
        FederatedCatalogService service = new FederatedCatalogService(policyEngine, monitor, didResolverRegistry, transformerRegistry, jsonLd, httpClient);
        Collection<Catalog> catalogs = new ArrayList<>();
        try {
            catalogs = service.retrieveCatalog(null);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Collection<Catalog> result = service.filterCatalog(catalogs, service.createContext(tokens, PARTICIPANT_DID), PARTICIPANT_DID);
        monitor.warning(result.toString());
        assertFalse(result.isEmpty());
        assertTrue(
                result.stream()
                        .anyMatch(c -> c.getDatasets().stream()
                                .anyMatch(d -> d.getId().equals("restricted-route-asset")))
        );
        assertFalse(
                result.stream()
                        .anyMatch(c -> c.getDatasets().stream()
                                .anyMatch(d -> d.getId().equals("restricted-travel-asset")))
        );
        assertTrue(
                result.stream()
                        .anyMatch(c -> c.getDatasets().stream()
                                .anyMatch(d -> d.getId().equals("visible-restricted-asset")))
        );
        assertFalse(
                result.stream()
                        .anyMatch(c -> c.getDatasets().stream()
                                .anyMatch(d -> d.getId().equals("restricted-and-asset")))
        );
        assertTrue(
                result.stream()
                        .anyMatch(c -> c.getDatasets().stream()
                                .anyMatch(d -> d.getId().equals("visible-list-asset")))
        );
        assertFalse(
                result.stream()
                        .anyMatch(c -> c.getDatasets().stream()
                                .anyMatch(d -> d.getId().equals("restricted-list-asset")))
        );
    }

    @Test
    void shouldExposeRestrictedAssetsFromOwnDsp2025Catalog() throws IOException, InterruptedException {
        var service = new FederatedCatalogService(policyEngine, monitor, didResolverRegistry, transformerRegistry,
                jsonLd, httpClient);
        var catalogs = service.retrieveCatalog(null);
        var ownerDid = "did:web:consumer-identityhub:api:did";
        var tokens = ClaimToken.Builder.newInstance().claim("vc", List.of()).build();

        var result = service.filterCatalog(catalogs, service.createContext(tokens, ownerDid), ownerDid);

        assertTrue(result.stream()
                .flatMap(catalog -> catalog.getDatasets().stream())
                .anyMatch(dataset -> dataset.getId().equals("restricted-travel-asset")));
    }

    @Test
    void shouldExposeOwnCatalogWithoutEvaluatingPolicies() {
        var rejectingPolicyEngine = mock(PolicyEngine.class);
        var service = new FederatedCatalogService(rejectingPolicyEngine, monitor, didResolverRegistry,
                transformerRegistry, jsonLd, httpClient);
        var restrictedDataset = Dataset.Builder.newInstance()
                .id("restricted-asset")
                .offer("offer-id", Policy.Builder.newInstance().build())
                .build();
        var ownCatalog = Catalog.Builder.newInstance()
                .id("catalog-id")
                .participantId(PARTICIPANT_DID)
                .dataset(restrictedDataset)
                .build();

        var result = service.filterCatalog(List.of(ownCatalog), mock(CatalogDiscoveryPolicyContext.class), PARTICIPANT_DID);

        assertTrue(result.iterator().next().getDatasets().contains(restrictedDataset));
        verifyNoInteractions(rejectingPolicyEngine);
    }

    @Test
    void shouldEvaluatePoliciesForAnotherParticipantsCatalog() {
        var rejectingPolicyEngine = mock(PolicyEngine.class);
        when(rejectingPolicyEngine.evaluate(any(), any())).thenReturn(Result.failure("missing credential"));
        var service = new FederatedCatalogService(rejectingPolicyEngine, monitor, didResolverRegistry,
                transformerRegistry, jsonLd, httpClient);
        var restrictedDataset = Dataset.Builder.newInstance()
                .id("restricted-asset")
                .offer("offer-id", Policy.Builder.newInstance().build())
                .build();
        var foreignCatalog = Catalog.Builder.newInstance()
                .id("catalog-id")
                .participantId("did:web:another-participant")
                .dataset(restrictedDataset)
                .build();

        var result = service.filterCatalog(List.of(foreignCatalog), mock(CatalogDiscoveryPolicyContext.class), PARTICIPANT_DID);

        assertTrue(result.iterator().next().getDatasets().isEmpty());
    }

    @Test
    void retrieveCatalog_withQuerySpec_postsCompactedQuerySpecAsJson() throws IOException, InterruptedException {
        var registry = new TypeTransformerRegistryImpl(mock(Monitor.class));
        registry.register(new JsonObjectFromQuerySpecTransformer(Json.createBuilderFactory(Map.of())));
        registry.register(new JsonObjectToCatalogTransformer());
        registry.register(new JsonObjectToDatasetTransformer());
        registry.register(new JsonObjectToPolicyTransformer(participantIdMapper));
        registry.register(new JsonObjectToDataServiceTransformer());
        registry.register(new JsonObjectToDistributionTransformer());
        registry.register(new JsonObjectToPermissionTransformer());
        registry.register(new JsonObjectToActionTransformer());
        registry.register(new JsonObjectToConstraintTransformer());
        registry.register(new JsonObjectToOperatorTransformer());
        registry.register(new JsonValueToGenericTypeTransformer(typeManager, JSON_LD));
        HttpClient capturingClient = mock();
        var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        when(capturingClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        // same contexts as VcCatalogFilterExtension registers for CATALOG_FILTER_SCOPE in production
        var scopedJsonLd = new TitaniumJsonLd(monitor);
        CachedDocumentRegistry.getDocuments().forEach(document -> document
                .onSuccess(context -> scopedJsonLd.registerCachedDocument(context.url(), context.resource())));
        scopedJsonLd.registerContext(DSPACE_CONTEXT_2025_1, CATALOG_FILTER_SCOPE);
        scopedJsonLd.registerContext(EDC_DSPACE_CONTEXT, CATALOG_FILTER_SCOPE);
        var service = new FederatedCatalogService(policyEngine, monitor, didResolverRegistry, registry, scopedJsonLd, capturingClient);

        var catalogs = service.retrieveCatalog(QuerySpec.Builder.newInstance().offset(3).limit(9)
                .sortField("properties.'http://purl.org/dc/terms/title'").build());

        assertFalse(catalogs.isEmpty());
        var request = requestCaptor.getValue();
        assertEquals("POST", request.method());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElseThrow());
        var body = Json.createReader(new StringReader(readBody(request))).readObject();
        assertEquals("QuerySpec", body.getString(JsonLdKeywords.TYPE), body.toString());
        assertEquals(3, body.getInt("offset"), body.toString());
        assertEquals(9, body.getInt("limit"), body.toString());
        // compact "sortField": the key the federated catalog v4 validation reads (DatasetSortFieldValidator)
        assertEquals("properties.'http://purl.org/dc/terms/title'", body.getString("sortField"), body.toString());
    }

    @Test
    void retrieveCatalog_whenQuerySpecCannotBeTransformed_doesNotCallTheCatalog() {
        TypeTransformerRegistry failingRegistry = mock();
        when(failingRegistry.transform(any(QuerySpec.class), eq(JsonObject.class))).thenReturn(Result.failure("boom"));
        HttpClient unusedClient = mock();
        var service = new FederatedCatalogService(policyEngine, monitor, didResolverRegistry, failingRegistry, jsonLd, unusedClient);

        var exception = assertThrows(RuntimeException.class, () -> service.retrieveCatalog(QuerySpec.none()));

        assertTrue(exception.getMessage().contains("QuerySpec transform failed"));
        verifyNoInteractions(unusedClient);
    }

    @Test
    void federatedCatalogError_400_keepsEveryMessageOfTheEdcErrorBody() {
        var error = FederatedCatalogService.federatedCatalogError(400,
                "[{\"message\":\"first\",\"type\":\"ValidationFailure\",\"path\":\"sortField\",\"invalidValue\":\"x\"}," +
                        "{\"message\":\"second\",\"type\":\"ValidationFailure\",\"path\":null,\"invalidValue\":null}]");

        assertInstanceOf(InvalidRequestException.class, error);
        assertEquals(List.of("first", "second"), error.getMessages());
    }

    @ParameterizedTest
    @CsvSource({"400, InvalidRequest", "401, AuthenticationFailed", "403, NotAuthorized", "409, ObjectConflict"})
    void federatedCatalogError_statusWithEdcException_keepsTheStatusAndTheMessage(int status, String type) {
        var error = FederatedCatalogService.federatedCatalogError(status, "[{\"message\":\"no\",\"type\":\"X\"}]");

        assertEquals(type, error.getType());
        assertEquals(List.of("no"), error.getMessages());
    }

    @Test
    void federatedCatalogError_withNonJsonBody_doesNotForwardTheBody() {
        var error = FederatedCatalogService.federatedCatalogError(400, "<html><body><h2>HTTP ERROR 400 Bad Request</h2></body></html>");

        assertEquals(List.of("The federated catalog replied HTTP 400"), error.getMessages());
    }

    @Test
    void federatedCatalogError_withJsonThatIsNotAnEdcErrorBody_doesNotForwardTheBody() {
        var error = FederatedCatalogService.federatedCatalogError(403, "{\"statusCode\":403,\"message\":\"Access denied\"}");

        assertEquals(List.of("The federated catalog replied HTTP 403"), error.getMessages());
    }

    @Test
    void federatedCatalogError_withoutBody_stillHasMessage() {
        var error = FederatedCatalogService.federatedCatalogError(400, "");

        assertEquals(List.of("The federated catalog replied HTTP 400"), error.getMessages());
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 500, 502, 503, 504})
    void federatedCatalogError_statusWithoutEdcException_isBadGatewayWithTheStatusAndTheMessage(int status) {
        var error = FederatedCatalogService.federatedCatalogError(status, "[{\"message\":\"upstream says no\",\"type\":\"X\"}]");

        assertInstanceOf(BadGatewayException.class, error);
        assertEquals("The federated catalog query failed with HTTP %d: upstream says no".formatted(status), error.getMessage());
    }

    @Test
    void federatedCatalogError_500WithTheJettyErrorPage_isBadGatewayWithTheStatusOnly() {
        var error = FederatedCatalogService.federatedCatalogError(500, "<html><body><h2>HTTP ERROR 500 Internal Server Error</h2></body></html>");

        assertInstanceOf(BadGatewayException.class, error);
        assertEquals(List.of("The federated catalog query failed with HTTP 500"), error.getMessages());
    }

    @Test
    void retrieveCatalog_whenTheUrlCannotBeResolved_isBadGateway() {
        AuthorityCatalogFilterDidResolver failingResolver = mock();
        when(failingResolver.fetchCatalogFilterUrl()).thenReturn(Result.failure("no FederatedCatalogService"));
        HttpClient unusedClient = mock();
        var service = new FederatedCatalogService(policyEngine, monitor, failingResolver, transformerRegistry, jsonLd, unusedClient);

        var exception = assertThrows(BadGatewayException.class, () -> service.retrieveCatalog(null));

        assertTrue(exception.getMessage().contains("no FederatedCatalogService"), exception.getMessage());
        verifyNoInteractions(unusedClient);
    }

    @Test
    void retrieveCatalog_whenTheFederatedCatalogCannotBeReached_isBadGateway() throws IOException, InterruptedException {
        HttpClient failingClient = mock();
        when(failingClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("Connection refused"));
        var service = new FederatedCatalogService(policyEngine, monitor, didResolverRegistry, transformerRegistry, jsonLd, failingClient);

        var exception = assertThrows(BadGatewayException.class, () -> service.retrieveCatalog(null));

        assertTrue(exception.getMessage().startsWith("Could not reach the federated catalog http://example.com/catalog"), exception.getMessage());
    }

    @Test
    void retrieveCatalog_whenTheFederatedCatalogRepliesWithAnError_throwsTheMappedException() throws IOException, InterruptedException {
        HttpClient errorClient = mock();
        HttpResponse<String> errorResponse = mock();
        when(errorResponse.statusCode()).thenReturn(400);
        when(errorResponse.body()).thenReturn("[{\"message\":\"Cannot sort by 'x': field does not exist in Dataset\",\"type\":\"InvalidRequest\"}]");
        when(errorClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(errorResponse);
        var service = new FederatedCatalogService(policyEngine, monitor, didResolverRegistry, transformerRegistry, jsonLd, errorClient);

        var exception = assertThrows(InvalidRequestException.class, () -> service.retrieveCatalog(null));

        assertEquals(List.of("Cannot sort by 'x': field does not exist in Dataset"), exception.getMessages());
    }

    private static String readBody(HttpRequest request) {
        var body = new StringBuilder();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                body.append(StandardCharsets.UTF_8.decode(item));
            }

            @Override
            public void onError(Throwable throwable) {
                throw new IllegalStateException(throwable);
            }

            @Override
            public void onComplete() {
                // nothing to do
            }
        });
        return body.toString();
    }

    private static VerifiableCredential createVc(String type, Map<String, Object> claims) {
        return VerifiableCredential.Builder.newInstance()
                .type(type)
                .issuer(new Issuer(ISSUER))
                .issuanceDate(Instant.now())
                .credentialSubject(CredentialSubject.Builder.newInstance().claims(claims).id(SUBJECT).build())
                .build();
    }

    private static String loadCatalogFromFile(String resourcePath) throws IOException {
        String catalogReply;
        try (var is = FederatedCatalogFilterServiceTest.class.getResourceAsStream("/catalogReply.txt")) {
            catalogReply = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        return catalogReply;
    }
}
