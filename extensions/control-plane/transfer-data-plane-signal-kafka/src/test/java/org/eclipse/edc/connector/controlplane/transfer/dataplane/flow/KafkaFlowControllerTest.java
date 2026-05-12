package org.eclipse.edc.connector.controlplane.transfer.dataplane.flow;

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.controlplane.transfer.dataplane.flow.KafkaFlowController.KAFKA_BROKER_PULL;

class KafkaFlowControllerTest {

    private final KafkaFlowController controller = new KafkaFlowController();

    @Test
    void canHandle_kafkaBrokerPull_shouldReturnTrue() {
        var tp = TransferProcess.Builder.newInstance()
                .transferType(KAFKA_BROKER_PULL)
                .contentDataAddress(DataAddress.Builder.newInstance().type("test").build())
                .build();

        assertThat(controller.canHandle(tp)).isTrue();
    }

    @Test
    void canHandle_otherType_shouldReturnFalse() {
        var tp = TransferProcess.Builder.newInstance()
                .transferType("HttpData-PUSH")
                .contentDataAddress(DataAddress.Builder.newInstance().type("test").build())
                .build();

        assertThat(controller.canHandle(tp)).isFalse();
    }

    @Test
    void suspend_shouldReturnFatalError() {
        var tp = TransferProcess.Builder.newInstance()
                .transferType(KAFKA_BROKER_PULL)
                .contentDataAddress(DataAddress.Builder.newInstance().type("test").build())
                .build();

        var result = controller.suspend(tp);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("Suspend");
    }

    @Test
    void terminate_shouldReturnFatalError() {
        var tp = TransferProcess.Builder.newInstance()
                .transferType(KAFKA_BROKER_PULL)
                .contentDataAddress(DataAddress.Builder.newInstance().type("test").build())
                .build();

        var result = controller.terminate(tp);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("Terminate");
    }

    @Test
    void prepare_shouldReturnFatalError() {
        var tp = TransferProcess.Builder.newInstance()
                .transferType(KAFKA_BROKER_PULL)
                .contentDataAddress(DataAddress.Builder.newInstance().type("test").build())
                .build();

        var result = controller.prepare(tp, Policy.Builder.newInstance().build());

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("Prepare");
    }

    @Test
    void transferTypesFor_shouldReturnEmptySet() {
        var asset = Asset.Builder.newInstance().build();

        assertThat(controller.transferTypesFor(asset)).isEmpty();
    }

    @Test
    void start_shouldReturnDataFlowResponseWithKafkaAddress() {
        var contentAddress = DataAddress.Builder.newInstance()
                .type("Kafka")
                .property("topic", "my-topic")
                .property("bootstrap.servers", "localhost:9092")
                .build();
        var tp = TransferProcess.Builder.newInstance()
                .transferType(KAFKA_BROKER_PULL)
                .correlationId("correlation-1")
                .contractId("contract-1")
                .contentDataAddress(contentAddress)
                .build();

        var result = controller.start(tp, Policy.Builder.newInstance().build());

        assertThat(result.succeeded()).isTrue();
        var address = result.getContent().getDataAddress();
        assertThat(address).isNotNull();
        assertThat(address.getStringProperty("topic")).isEqualTo("my-topic");
    }
}
