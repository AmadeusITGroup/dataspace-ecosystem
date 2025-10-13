package org.eclipse.edc.test.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.jsonld.util.JacksonJsonLd;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.eclipse.edc.spi.monitor.Monitor;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.STARTED;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VALUE;
import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_SCHEMA;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.DCAT_DATASET_ATTRIBUTE;

public class AbstractEndToEndTests {

    protected static final ObjectMapper MAPPER = JacksonJsonLd.createObjectMapper();
    protected static final Duration TEST_TIMEOUT = Duration.ofSeconds(60);
    private static final Monitor MONITOR = new ConsoleMonitor();
    private static final JsonLd JSON_LD = new TitaniumJsonLd(MONITOR);
    private static final Duration TEST_POLL_INTERVAL = Duration.ofMillis(500);

    protected List<JsonObject> queryParticipantDatasets(AbstractAuthority authority, String participantDid) {
        AtomicReference<List<JsonObject>> datasets = new AtomicReference<>();

        await().atMost(TEST_TIMEOUT)
                .pollInterval(TEST_POLL_INTERVAL)
                .untilAsserted(() -> {
                    var catalog = authority.queryCatalog(MAPPER, JSON_LD).stream()
                            .filter(isCatalogOf(participantDid))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Failed to find Catalog for participant %s".formatted(participantDid)));

                    datasets.set(catalog.getJsonArray(DCAT_DATASET_ATTRIBUTE).stream()
                            .map(JsonValue::asJsonObject)
                            .toList());
                });

        return datasets.get();
    }

    protected String negotiationContractAndStartTransfer(AbstractParticipant consumer, AbstractParticipant provider, String assetId) {
        var transferProcessId = consumer.participantClient().requestAssetFrom(assetId, provider.participantClient())
                .withTransferType("HttpData-PULL")
                .execute();

        await().atMost(TEST_TIMEOUT).untilAsserted(() -> {
            var state = consumer.participantClient().getTransferProcessState(transferProcessId);
            assertThat(state).isEqualTo(STARTED.name());
        });

        return transferProcessId;
    }

    private static Predicate<JsonObject> isCatalogOf(String did) {
        return catalog -> catalog.getJsonArray(DSPACE_SCHEMA + "participantId")
                .stream()
                .allMatch(jsonValue -> did.equals(jsonValue.asJsonObject().getString(VALUE)));
    }


}
