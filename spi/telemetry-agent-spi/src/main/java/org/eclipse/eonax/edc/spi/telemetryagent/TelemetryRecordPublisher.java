package org.eclipse.eonax.edc.spi.telemetryagent;

import org.eclipse.edc.runtime.metamodel.annotation.ExtensionPoint;

@FunctionalInterface
@ExtensionPoint
public interface TelemetryRecordPublisher {

    Boolean sendRecord(TelemetryRecord telemetryRecord);
}
