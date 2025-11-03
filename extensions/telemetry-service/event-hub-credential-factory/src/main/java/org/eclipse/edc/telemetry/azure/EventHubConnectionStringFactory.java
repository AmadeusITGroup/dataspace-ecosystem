package org.eclipse.edc.telemetry.azure;

import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.eonax.spi.telemetry.TelemetryServiceConstants;
import org.eclipse.eonax.spi.telemetry.TelemetryServiceCredentialFactory;
import org.eclipse.eonax.spi.telemetry.TelemetryServiceCredentialType;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class EventHubConnectionStringFactory implements TelemetryServiceCredentialFactory {

    private final Vault vault;
    private final String connectionStringAlias;
    private final long validity;

    public EventHubConnectionStringFactory(Vault vault, String connectionStringAlias, long validity) {
        this.connectionStringAlias = Objects.requireNonNull(connectionStringAlias, "connectionStringAlias");
        this.vault = vault;
        this.validity = validity;
    }

    @Override
    public Result<TokenRepresentation> get() {
        return Optional.ofNullable(vault.resolveSecret(connectionStringAlias))
                .map(cs -> TokenRepresentation.Builder.newInstance()
                        .token(cs)
                        .expiresIn(validity)
                        .additional(Map.of(TelemetryServiceConstants.CREDENTIAL_TYPE, TelemetryServiceCredentialType.CONNECTION_STRING))
                        .build())
                .map(Result::success)
                .orElse(Result.failure("Failed to find connection string with alias '%s' in vault".formatted(connectionStringAlias)));
    }

}
