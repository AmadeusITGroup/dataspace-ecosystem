package org.eclipse.edc.dse.controlplane.asset.decorator;

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.dse.controlplane.asset.decorator.AssetCreatedAtDecorator.PROPERTY_CREATED_AT;

class AssetCreatedAtDecoratorTest {

    private final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
    private final AssetCreatedAtDecorator decorator = new AssetCreatedAtDecorator(clock);

    @Test
    void modify_assetAlreadyHaveCreationDate_shouldUseExistingDate() {
        Map<String, Object> props = Map.of("foo", "bar");
        var builder = Asset.Builder.newInstance()
                .dataAddress(DataAddress.Builder.newInstance().type("type").build())
                .properties(props);
        var createdAt = clock.instant().minusSeconds(100);
        var existing = Asset.Builder.newInstance()
                .property(PROPERTY_CREATED_AT, createdAt)
                .build();

        decorator.decorate(existing, builder);

        assertThat(builder.build().getProperties())
                .containsAllEntriesOf(props)
                .containsEntry(PROPERTY_CREATED_AT, createdAt);
    }

    @Test
    void modify_assetDoesNotHaveCreateDate_useNow() {
        Map<String, Object> props = Map.of("foo", "bar");
        var builder = Asset.Builder.newInstance()
                .dataAddress(DataAddress.Builder.newInstance().type("type").build())
                .properties(props);

        decorator.decorate(Asset.Builder.newInstance().build(), builder);

        assertThat(builder.build().getProperties())
                .containsAllEntriesOf(props)
                .containsEntry(PROPERTY_CREATED_AT, clock.instant());
    }
}