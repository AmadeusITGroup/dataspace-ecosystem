package org.eclipse.edc.dse.controlplane.asset;


import org.eclipse.edc.connector.controlplane.asset.spi.event.AssetCreated;
import org.eclipse.edc.connector.controlplane.asset.spi.event.AssetUpdated;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.dse.controlplane.asset.decorator.AssetCreatedAtDecorator;
import org.eclipse.edc.dse.controlplane.asset.decorator.AssetUpdatedAtDecorator;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.event.EventRouter;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.time.Clock;

public class AssetCustomPropertySubscriberExtension implements ServiceExtension {

    @Inject
    private EventRouter eventRouter;

    @Inject
    private AssetIndex assetIndex;

    @Inject
    private Clock clock;

    @Override
    public String name() {
        return "Asset Custom Property Subscriber";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        eventRouter.registerSync(
                AssetCreated.class,
                new AssetCustomPropertySubscriber(assetIndex)
                        .register(new AssetCreatedAtDecorator(clock))
        );
        eventRouter.registerSync(
                AssetUpdated.class,
                new AssetCustomPropertySubscriber(assetIndex)
                        .register(new AssetCreatedAtDecorator(clock))
                        .register(new AssetUpdatedAtDecorator(clock))
        );
    }

}
