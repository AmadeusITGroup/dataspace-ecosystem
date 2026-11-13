package org.eclipse.edc.test.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.http.ContentType;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.jsonld.JsonLdExtension;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.jsonld.util.JacksonJsonLd;
import org.eclipse.edc.junit.extensions.TestServiceExtensionContext;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.dse.iam.policy.PolicyConstants.VISIBILITY_CREDENTIAL_TYPE;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.STARTED;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VALUE;
import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_2025_1_IRI;
import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_CONTEXT_2025_1;
import static org.eclipse.edc.jsonld.spi.Namespaces.EDC_DSPACE_CONTEXT;

public class AbstractEndToEndTests {

    protected static final ObjectMapper MAPPER = JacksonJsonLd.createObjectMapper();
    protected static final Duration TEST_TIMEOUT = Duration.ofSeconds(60);
    private static final Monitor MONITOR = new ConsoleMonitor();
    private static final JsonLd JSON_LD = new JsonLdExtension().createJsonLdService(TestServiceExtensionContext.testServiceExtensionContext());
    private static final Duration TEST_POLL_INTERVAL = Duration.ofMillis(500);
    public static final String HTTP_DATA_PULL = "HttpData-PULL";
    /**
     * Keys of the control-plane catalog response, the catalog filter's DSP 2025-1 compaction.
     */
    protected static final String CATALOG_FILTER_PARTICIPANT_ID = "participantId";
    protected static final String CATALOG_FILTER_DATASET = "dataset";
    private static final String QUERY_ALL = """
            {
                "@context": { "@vocab": "https://w3id.org/edc/v0.0.1/ns/" },
                "@type": "QuerySpec",
                "filterExpression": [],
                "limit": 100,
                "offset": 0
            }
            """;

    /**
     * Queries the control-plane catalog endpoint ({@code POST /v4/catalogs/request}) and returns the datasets of the
     * given participant's catalog as the control plane returns them, retrying until the crawler has cached the catalog.
     * <p>
     * The control plane returns the authority catalog filter's reply as is: a JSON array of DSP 2025-1 catalogs compacted
     * with the DSP 2025-1 and EDC dspace contexts ({@code @type: Catalog}, {@code participantId}, {@code dataset} with
     * {@code Dataset} entries), not the federated catalog management v4 shape. The keys are read as returned.
     */
    protected List<JsonObject> queryParticipantDatasets(String participantDid, String controlPlaneCatalogUrl) {
        AtomicReference<List<JsonObject>> datasets = new AtomicReference<>();
        await().atMost(TEST_TIMEOUT)
                .pollInterval(TEST_POLL_INTERVAL)
                .untilAsserted(() -> {
                    var catalog = queryControlPlaneCatalogs(controlPlaneCatalogUrl).stream()
                            .filter(candidate -> participantDid.equals(candidate.getString(CATALOG_FILTER_PARTICIPANT_ID, null)))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Failed to find Catalog for participant %s".formatted(participantDid)));
                    var catalogDatasets = Optional.ofNullable(catalog.getJsonArray(CATALOG_FILTER_DATASET))
                            .map(arr -> arr.stream().map(JsonValue::asJsonObject).toList())
                            .orElse(List.of());
                    assertThat(catalogDatasets).allSatisfy(dataset -> assertThat(dataset.getString(TYPE, null)).isEqualTo("Dataset"));
                    datasets.set(catalogDatasets);
                });

        return datasets.get();
    }

    /**
     * The control-plane catalog response, checked to be the catalog filter's DSP 2025-1 compaction (each catalog has
     * {@code @type: Catalog} and the DSP 2025-1 and EDC dspace contexts).
     */
    private static List<JsonObject> queryControlPlaneCatalogs(String controlPlaneCatalogUrl) {
        var body = given()
                .baseUri(controlPlaneCatalogUrl)
                .contentType(ContentType.JSON)
                .body(QUERY_ALL)
                .when()
                .post()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().body().asByteArray();

        List<JsonObject> catalogs;
        try (var reader = Json.createReader(new ByteArrayInputStream(body))) {
            catalogs = reader.readArray().stream().map(JsonValue::asJsonObject).toList();
        }
        assertThat(catalogs).allSatisfy(catalog -> {
            assertThat(catalog.getString(TYPE, null)).isEqualTo("Catalog");
            assertThat(catalog.get(CONTEXT)).isInstanceOf(JsonArray.class);
            assertThat(catalog.getJsonArray(CONTEXT)).contains(Json.createValue(DSPACE_CONTEXT_2025_1), Json.createValue(EDC_DSPACE_CONTEXT));
        });
        return catalogs;
    }

    /**
     * Queries the federated catalog directly (authority's management {@code /v4/catalogs/request}) and
     * returns the raw cached {@link JsonObject} for the given participant's catalog, retrying
     * until the crawler has cached it.
     * <p>
     * Unlike {@link #queryParticipantDatasets}, this returns the catalog object itself rather
     * than its flattened dataset list - needed for catalog-level properties such as
     * {@code dcat:themeTaxonomy}, which live on the catalog, not on individual datasets.
     */
    protected JsonObject queryParticipantCatalog(AbstractAuthority authority, String participantDid, String catalogUrl) {
        AtomicReference<JsonObject> catalog = new AtomicReference<>();
        await().atMost(TEST_TIMEOUT)
                .pollInterval(TEST_POLL_INTERVAL)
                .untilAsserted(() -> catalog.set(authority.queryCatalog(MAPPER, JSON_LD, catalogUrl).stream()
                        .filter(isCatalogOf(participantDid))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Failed to find Catalog for participant %s".formatted(participantDid)))));

        return catalog.get();
    }

    /**
     * Queries the federated catalog directly (authority's management {@code /v4/catalogs/request}) with a
     * full {@code QuerySpec} body supporting {@code filterExpression}, {@code sortField},
     * {@code sortOrder}, {@code offset}, and {@code limit}.
     *
     * <p>The returned list contains the flat dataset JSON objects extracted from the
     * response catalogs (all catalogs are flattened into a single dataset list).</p>
     *
     * <p>Retries up to {@link #TEST_TIMEOUT} so that the FC crawler has time to crawl
     * new assets before the assertion is evaluated.</p>
     *
     * @param catalogQueryUrl the full URL of the authority's catalog query endpoint,
     *                        typically {@code AUTHORITY.federatedCatalogManagementUrl() + "/v4/catalogs/request"}
     * @param querySpec       JSON body with filter / sort / pagination fields
     * @param expectedCount   minimum number of datasets expected (used as the await condition)
     */
    protected List<JsonObject> queryCatalogWithFilter(String catalogQueryUrl,
                                                      jakarta.json.JsonObject querySpec,
                                                      int expectedCount) {
        AtomicReference<List<JsonObject>> result = new AtomicReference<>(List.of());
        // management v4 rejects a QuerySpec without @type
        var body = querySpec.containsKey("@type") ? querySpec : Json.createObjectBuilder(querySpec).add("@type", "QuerySpec").build();
        await().atMost(TEST_TIMEOUT)
                .pollInterval(TEST_POLL_INTERVAL)
                .untilAsserted(() -> {
                    var response = given()
                            .baseUri(catalogQueryUrl)
                            .contentType("application/json")
                            .body(body.toString())
                            .when()
                            .post()
                            .then()
                            .statusCode(200)
                            .extract().body().asString();

                    jakarta.json.JsonArray catalogs;
                    try (var reader = Json.createReader(new java.io.StringReader(response))) {
                        catalogs = reader.readArray();
                    }
                    var datasets = catalogs.stream()
                            .map(JsonValue::asJsonObject)
                            .flatMap(catalog -> {
                                // management v4 response key (compacted with the management/v2 context)
                                var ds = catalog.get("dcat:dataset");
                                if (ds == null) return java.util.stream.Stream.empty();
                                if (ds instanceof JsonArray arr) {
                                    return arr.stream().map(JsonValue::asJsonObject);
                                }
                                return java.util.stream.Stream.of(ds.asJsonObject());
                            })
                            .toList();

                    assertThat(datasets).hasSizeGreaterThanOrEqualTo(expectedCount);
                    result.set(datasets);
                });
        return result.get();
    }

    protected String negotiationContractAndStartTransfer(AbstractParticipant consumer, AbstractParticipant provider, String assetId) {
        return negotiationContractAndStartTransfer(consumer, provider, assetId, HTTP_DATA_PULL);
    }

    protected String negotiationContractAndStartTransfer(AbstractParticipant consumer, AbstractParticipant provider, String assetId, String transferType) {
        var transferProcessId = consumer.participantClient().requestAssetFrom(assetId, provider.participantClient())
                .withTransferType(transferType)
                .execute();

        await().atMost(TEST_TIMEOUT).untilAsserted(() -> {
            var state = consumer.participantClient().getTransferProcessState(transferProcessId);
            assertThat(state).isEqualTo(STARTED.name());
        });

        return transferProcessId;
    }

    protected static void getCredentials(AbstractEntity participant) {
        await().atMost(TEST_TIMEOUT).untilAsserted(() -> assertCredentialsPresent(participant));
    }

    protected static void getVisibilityCredential(AbstractEntity participant) {
        await().atMost(TEST_TIMEOUT).untilAsserted(() -> assertVisibilityCredentialPresent(participant));
    }

    private static void assertCredentialsPresent(AbstractEntity participant) throws Exception {
        boolean hasMembershipCredential = false;
        boolean hasDomainCredential = false;
        List<JsonObject> verifiableCredentials = participant.getCredentials(MAPPER);
        for (JsonObject verifiableCredential : verifiableCredentials) {
            JsonObject vc = verifiableCredential.getJsonObject("verifiableCredential");
            JsonObject credential = vc.getJsonObject("credential");
            JsonArray types = credential.getJsonArray("type");
            for (int j = 0; j < types.size(); j++) {
                String type = types.getString(j);
                if (type.equals("MembershipCredential")) {
                    hasMembershipCredential = true;
                    assertMembershipCredentialSubject(credential);
                }
                if (type.equals("DomainCredential")) {
                    hasDomainCredential = true;
                }
            }
        }
        assert hasMembershipCredential : "Missing MembershipCredential type";
        assert hasDomainCredential : "Missing DomainCredential type";
    }

    private static void assertVisibilityCredentialPresent(AbstractEntity participant) throws Exception {
        var credential = participant.getCredentials(MAPPER).stream()
                .map(container -> container.getJsonObject("verifiableCredential").getJsonObject("credential"))
                .filter(candidate -> candidate.getJsonArray("type").contains(Json.createValue(VISIBILITY_CREDENTIAL_TYPE)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing VisibilityCredential type"));

        var subjectValue = credential.get("credentialSubject");
        var credentialSubject = subjectValue instanceof JsonArray subjectArray
                ? subjectArray.getJsonObject(0)
                : (JsonObject) subjectValue;
        assertThat(credentialSubject.getString("visibility_scope")).isEqualTo("ALL");
    }

    private static void assertMembershipCredentialSubject(JsonObject credential) {
        var subjectValue = credential.get("credentialSubject");
        assertThat(subjectValue).isNotNull();
        JsonObject credentialSubject;
        if (subjectValue instanceof JsonArray subjectArray) {
            credentialSubject = subjectArray.getJsonObject(0);
        } else {
            credentialSubject = (JsonObject) subjectValue;
        }
        assertThat(credentialSubject).containsKey("companySegment");
        assertThat(credentialSubject.getString("companySegment")).isEqualTo("Airlines");
    }

    private static Predicate<JsonObject> isCatalogOf(String did) {
        return catalog -> {
            // DSP v0.8 was removed in EDC 0.16.0, only the DSP 2025/1 namespace remains
            var values = catalog.getJsonArray(DSPACE_2025_1_IRI + "participantId");
            if (values == null) {
                return false;
            }
            return values.stream().allMatch(jsonValue -> {
                var obj = jsonValue.asJsonObject();
                // value may be a literal @value or a plain @id
                if (obj.containsKey(VALUE)) {
                    return did.equals(obj.getString(VALUE));
                }
                return did.equals(obj.getString("@id", null));
            });
        };
    }


}
