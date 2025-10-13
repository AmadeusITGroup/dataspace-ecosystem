package org.eclipse.edc.eonax.controlplane.asset.decorator;

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.eonax.controlplane.asset.decorator.AssetUpdatedAtDecorator.PROPERTY_UPDATED_AT;

class AssetUpdatedAtDecoratorTest {

    private final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
    private final AssetUpdatedAtDecorator decorator = new AssetUpdatedAtDecorator(clock);

    @Test
    void modify() {
        Map<String, Object> props = Map.of("foo", "bar");
        var builder = Asset.Builder.newInstance()
                .dataAddress(DataAddress.Builder.newInstance().type("type").build())
                .properties(props);

        decorator.decorate(null, builder);

        assertThat(builder.build().getProperties())
                .containsAllEntriesOf(props)
                .containsEntry(PROPERTY_UPDATED_AT, clock.instant());
    }
}