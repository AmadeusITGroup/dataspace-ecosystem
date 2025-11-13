package org.eclipse.edc.dse.controlplane.asset.decorator;

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;

import java.time.Clock;

import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

public class AssetUpdatedAtDecorator implements AssetDecorator {

    public static final String PROPERTY_UPDATED_AT = EDC_NAMESPACE + "updatedAt";

    private final Clock clock;

    public AssetUpdatedAtDecorator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void decorate(Asset existing, Asset.Builder builder) {
        builder.property(PROPERTY_UPDATED_AT, clock.instant());
    }

}
