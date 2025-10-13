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
    private final long expiry = 10000;

    public EventHubConnectionStringFactory(Vault vault, String connectionStringAlias) {
        this.connectionStringAlias = Objects.requireNonNull(connectionStringAlias, "connectionStringAlias");
        this.vault = vault;
    }

    @Override
    public Result<TokenRepresentation> get() {
        return Optional.ofNullable(vault.resolveSecret(connectionStringAlias))
                .map(cs -> TokenRepresentation.Builder.newInstance()
                        .token(cs)
                        .expiresIn(expiry)
                        .additional(Map.of(TelemetryServiceConstants.CREDENTIAL_TYPE, TelemetryServiceCredentialType.CONNECTION_STRING))
                        .build())
                .map(Result::success)
                .orElse(Result.failure("Failed to find connection string with alias '%s' in vault".formatted(connectionStringAlias)));
    }

}
