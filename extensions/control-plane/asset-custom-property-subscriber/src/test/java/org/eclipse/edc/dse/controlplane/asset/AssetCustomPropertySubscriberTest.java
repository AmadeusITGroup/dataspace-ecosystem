package org.eclipse.edc.dse.controlplane.asset;

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.DataplaneMetadata;
import org.eclipse.edc.connector.controlplane.asset.spi.event.AssetCreated;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetCustomPropertySubscriberTest {

    private static final String TEST_PROP = UUID.randomUUID().toString();

    private final AssetIndex assetIndex = mock();
    private final TransactionContext transactionContext = new NoopTransactionContext();
    private final AssetCustomPropertySubscriber subscriber = new AssetCustomPropertySubscriber(assetIndex, transactionContext);

    @Test
    void success() {
        var assetId = UUID.randomUUID().toString();
        Map<String, Object> props = Map.of("foo", "bar");
        subscriber.register((existing, builder) -> builder.property(TEST_PROP, "test"));
        var asset = Asset.Builder.newInstance()
                .properties(props)
                .dataAddress(DataAddress.Builder.newInstance().type("type").build())
                .build();

        var captor = ArgumentCaptor.forClass(Asset.class);
        when(assetIndex.findById(assetId)).thenReturn(asset);
        when(assetIndex.updateAsset(captor.capture())).thenReturn(StoreResult.success());
        var envelope = EventEnvelope.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .at(Instant.now().getEpochSecond())
                .payload(AssetCreated.Builder.newInstance().assetId(assetId).build())
                .build();

        subscriber.on(envelope);

        var updated = captor.getValue();
        assertThat(updated).isNotNull();
        assertThat(updated.getProperties()).containsAllEntriesOf(props).containsEntry(TEST_PROP, "test");
    }

    @Test
    void keepsEveryFieldOfTheStoredAsset() {
        var assetId = UUID.randomUUID().toString();
        var dataAddress = DataAddress.Builder.newInstance().type("HttpData").property("baseUrl", "https://provider.example/api").build();
        var dataplaneMetadata = DataplaneMetadata.Builder.newInstance()
                .label("label-1")
                .profile("profile-1")
                .property("https://w3id.org/edc/v0.0.1/ns/key", "value")
                .build();
        var asset = Asset.Builder.newInstance()
                .id(assetId)
                .createdAt(1_700_000_000_000L)
                .participantContextId("participant-context-1")
                .properties(Map.of("foo", "bar"))
                .privateProperties(Map.of("secret", "value"))
                .dataAddress(dataAddress)
                .dataplaneMetadata(dataplaneMetadata)
                .build();
        subscriber.register((existing, builder) -> builder.property(TEST_PROP, "test"));
        var captor = ArgumentCaptor.forClass(Asset.class);
        when(assetIndex.findById(assetId)).thenReturn(asset);
        when(assetIndex.updateAsset(captor.capture())).thenReturn(StoreResult.success());

        subscriber.on(EventEnvelope.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .at(Instant.now().getEpochSecond())
                .payload(AssetCreated.Builder.newInstance().assetId(assetId).build())
                .build());

        var updated = captor.getValue();
        assertThat(updated).isNotSameAs(asset);
        assertThat(updated.getId()).isEqualTo(assetId);
        assertThat(updated.getCreatedAt()).isEqualTo(1_700_000_000_000L);
        assertThat(updated.getParticipantContextId()).isEqualTo("participant-context-1");
        assertThat(updated.getProperties()).containsEntry("foo", "bar").containsEntry(TEST_PROP, "test");
        assertThat(updated.getPrivateProperties()).containsExactlyEntriesOf(Map.of("secret", "value"));
        assertThat(updated.getDataplaneMetadata()).isSameAs(dataplaneMetadata);
        assertThat(updated.getDataAddress()).isSameAs(dataAddress);
        // the stored asset is not modified
        assertThat(asset.getProperties()).doesNotContainKey(TEST_PROP);
    }

    @Test
    void assetNotFound_shouldThrow() {
        var assetId = UUID.randomUUID().toString();
        subscriber.register(mock());
        when(assetIndex.findById(any())).thenReturn(null);
        var envelope = EventEnvelope.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .at(Instant.now().getEpochSecond())
                .payload(AssetCreated.Builder.newInstance().assetId(assetId).build())
                .build();

        assertThatExceptionOfType(EdcException.class).isThrownBy(() -> subscriber.on(envelope))
                .withMessage("Failed to find Asset with id %s SHOULD NOT HAPPEN".formatted(assetId));

        verify(assetIndex, never()).updateAsset(any());
    }

    @Test
    void updateFails_shouldThrow() {
        subscriber.register(mock());
        var assetId = UUID.randomUUID().toString();
        Map<String, Object> props = Map.of("foo", "bar");
        var asset = Asset.Builder.newInstance()
                .properties(props)
                .dataAddress(DataAddress.Builder.newInstance().type("type").build())
                .build();
        var captor = ArgumentCaptor.forClass(Asset.class);
        when(assetIndex.findById(assetId)).thenReturn(asset);
        when(assetIndex.updateAsset(captor.capture())).thenReturn(StoreResult.notFound("not found"));
        var envelope = EventEnvelope.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .at(Instant.now().getEpochSecond())
                .payload(AssetCreated.Builder.newInstance().assetId(assetId).build())
                .build();

        assertThatExceptionOfType(EdcException.class).isThrownBy(() -> subscriber.on(envelope))
                .withMessageContaining("not found");
    }

}