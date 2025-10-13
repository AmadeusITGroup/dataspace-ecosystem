package org.eclipse.eonax.spi.telemetry;


import org.eclipse.edc.runtime.metamodel.annotation.ExtensionPoint;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.result.Result;

import java.util.function.Supplier;

@ExtensionPoint
public interface TelemetryServiceCredentialFactory extends Supplier<Result<TokenRepresentation>> {
}
