package org.eclipse.dse.edc.spi.telemetryagent;

import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.spi.types.TypeManager;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class DataConsumptionRecordTest {

    private static final TypeManager MAPPER = new JacksonTypeManager();

    @Test
    public void verifySerDeser() {
        var traceContext = Map.of("traceparent", "123");

        var record = DataConsumptionRecord.Builder.newInstance()
                .responseSize(100L)
                .traceContext(traceContext)
                .contractId("contract-id")
                .responseStatusCode(200)
                .participantId("did:web:" + UUID.randomUUID())
                .build();

        var serialized = MAPPER.writeValueAsString(record);

        assertThat(serialized).contains("\"contractId\":\"contract-id\"").contains("\"responseSize\":100");

        var deserialized = MAPPER.readValue(serialized, DataConsumptionRecord.class);

        assertThat(deserialized.getContractId()).isEqualTo(record.getContractId());
        assertThat(deserialized.getResponseSize()).isEqualTo(record.getResponseSize());

        assertThat(deserialized).usingRecursiveComparison()
                // Don't fail if 2 Numbers does not have the same actual types
                .withComparatorForType((o1, o2) -> {
                    if (o1 instanceof Number && o2 instanceof Number) {
                        return Long.compare(o1.longValue(), o2.longValue());
                    }
                    return 0;
                }, Number.class).isEqualTo(record);
    }

    @Test
    public void verifyNoContractIdFail() {
        var traceContext = Map.of("traceparent", "123");
        assertThatNullPointerException()
                .isThrownBy(() -> DataConsumptionRecord.Builder.newInstance().responseSize(10L).traceContext(traceContext).contractId(null).build())
                .withMessageContaining("contractId");
    }

    @Test
    public void verify_NullResponseSize_ShouldFail() {
        var traceContext = Map.of("traceparent", "123");
        assertThatNullPointerException()
                .isThrownBy(() -> DataConsumptionRecord.Builder.newInstance()
                        .responseSize(null)
                        .traceContext(traceContext)
                        .contractId("contract-id")
                        .build())
                .withMessageContaining("responseSize");
    }

    @Test
    public void verify_nullResponseStatusCode_ShouldFail() {
        var traceContext = Map.of("traceparent", "123");
        assertThatNullPointerException()
                .isThrownBy(() -> DataConsumptionRecord.Builder.newInstance()
                        .responseSize(null)
                        .traceContext(traceContext)
                        .contractId("contract-id")
                        .responseSize(100L)
                        .build())
                .withMessageContaining("responseStatusCode");
    }

    @Test
    public void verify_nullParticipantId_ShouldFail() {
        var traceContext = Map.of("traceparent", "123");
        assertThatNullPointerException()
                .isThrownBy(() -> DataConsumptionRecord.Builder.newInstance()
                        .responseSize(null)
                        .traceContext(traceContext)
                        .contractId("contract-id")
                        .responseSize(100L)
                        .responseStatusCode(200)
                        .build())
                .withMessageContaining("participantId");
    }

    @Test
    public void verify_NullTraceContext_ShouldNotFail() {
        assertThatNoException()
                .isThrownBy(() -> DataConsumptionRecord.Builder.newInstance()
                        .responseSize(10L)
                        .traceContext(null)
                        .contractId("contract-id")
                        .responseStatusCode(200)
                        .participantId("did:web:" + UUID.randomUUID())
                        .build());

    }
}