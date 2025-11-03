package org.eclipse.edc.telemetry.azure;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.eonax.spi.telemetry.TelemetryServiceCredentialFactory;

import java.time.Clock;

@Extension(value = EventHubCredentialFactoryExtension.NAME)
public class EventHubCredentialFactoryExtension implements ServiceExtension {

    public static final String NAME = "Event Hub Credential Factory";

    @Setting(description = "SAS token validity in seconds", defaultValue = "300", key = "eonax.credential-factory.azure.event-hub.sas.validity", required = false)
    public long validity;
    @Setting(description = "Event hub uri", key = "eonax.credential-factory.azure.event-hub.sas.uri", required = false)
    public String eventHubUri;
    @Setting(description = "Event hub key name", key = "eonax.credential-factory.azure.event-hub.sas.key.name", required = false)
    public String keyName;
    @Setting(description = "Event hub key vault alias", key = "eonax.credential-factory.azure.event-hub.sas.key.alias", required = false)
    public String keyAlias;
    @Setting(description = "Event hub connection string vault alias", key = "eonax.credential-factory.azure.event-hub.connection-string.alias", required = false)
    public String connectionStringAlias;

    @Inject
    public Vault vault;
    @Inject
    public Clock clock;

    @Override
    public String name() {
        return NAME;
    }

    @Provider
    public TelemetryServiceCredentialFactory credentialFactory() {
        return eventHubUri != null ? new EventHubSasTokenFactory(clock, vault, validity, eventHubUri, keyName, keyAlias) :
                new EventHubConnectionStringFactory(vault, connectionStringAlias, validity);
    }

}