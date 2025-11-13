package org.eclipse.dse.edc.spi.telemetryagent;

import org.eclipse.edc.runtime.metamodel.annotation.ExtensionPoint;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.result.Result;

@ExtensionPoint
@FunctionalInterface
public interface TelemetryServiceClient {

    Result<TokenRepresentation> fetchCredential();

}
