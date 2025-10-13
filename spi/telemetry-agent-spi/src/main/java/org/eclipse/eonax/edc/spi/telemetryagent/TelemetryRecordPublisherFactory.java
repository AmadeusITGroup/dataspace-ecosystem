package org.eclipse.eonax.edc.spi.telemetryagent;

import org.eclipse.edc.runtime.metamodel.annotation.ExtensionPoint;
import org.eclipse.edc.spi.iam.TokenRepresentation;

@FunctionalInterface
@ExtensionPoint
public interface TelemetryRecordPublisherFactory {

    TelemetryRecordPublisher createClient(TokenRepresentation token);
    
}
