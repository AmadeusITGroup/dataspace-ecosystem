package org.eclipse.edc.dse.controlplane.asset.decorator;

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;

@FunctionalInterface
public interface AssetDecorator {

    void decorate(Asset existing, Asset.Builder builder);

}
