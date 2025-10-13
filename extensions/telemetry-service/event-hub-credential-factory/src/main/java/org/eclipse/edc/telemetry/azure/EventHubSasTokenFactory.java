package org.eclipse.edc.telemetry.azure;

import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.eonax.spi.telemetry.TelemetryServiceConstants;
import org.eclipse.eonax.spi.telemetry.TelemetryServiceCredentialFactory;
import org.eclipse.eonax.spi.telemetry.TelemetryServiceCredentialType;
import org.jetbrains.annotations.NotNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class EventHubSasTokenFactory implements TelemetryServiceCredentialFactory {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final Clock clock;
    private final long validity;
    private final String resourceUri;
    private final String keyName;
    private final String keyAlias;
    private final Vault vault;

    public EventHubSasTokenFactory(Clock clock, Vault vault, long validity, @NotNull String resourceUri, @NotNull String keyName, @NotNull String keyAlias) {
        this.clock = clock;
        this.vault = vault;
        this.validity = validity;
        this.resourceUri = Objects.requireNonNull(resourceUri, "resourceUri");
        this.keyName = Objects.requireNonNull(keyName, "keyName");
        this.keyAlias = Objects.requireNonNull(keyAlias, "keyAlias");
    }

    @Override
    public Result<TokenRepresentation> get() {
        var expiry = clock.instant().getEpochSecond() + validity;
        var key = vault.resolveSecret(keyAlias);
        if (key == null) {
            return Result.failure("Could not find secret with alias %s in vault".formatted(keyAlias));
        }

        return createUrlString(expiry)
                .compose(url -> sign(key, url))
                .map(signature -> toSasToken(signature, expiry))
                .map(sasToken -> TokenRepresentation.Builder.newInstance()
                        .token(sasToken)
                        .expiresIn(expiry)
                        .additional(Map.of(TelemetryServiceConstants.CREDENTIAL_TYPE, TelemetryServiceCredentialType.SAS_TOKEN))
                        .build());
    }

    private String toSasToken(String signature, long expiry) {
        return "SharedAccessSignature sr=" + URLEncoder.encode(resourceUri, StandardCharsets.UTF_8) + "&sig=" + URLEncoder.encode(signature, StandardCharsets.UTF_8) + "&se=" + expiry + "&skn=" + keyName;
    }

    private Result<String> createUrlString(long expiry) {
        return Result.success(URLEncoder.encode(resourceUri, StandardCharsets.UTF_8) + "\n" + expiry);
    }

    private static Result<String> sign(String key, String input) {

        try {
            var mac = Mac.getInstance(HMAC_SHA256);
            var secretKey = new SecretKeySpec(key.getBytes(), HMAC_SHA256);
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(input.getBytes());
            return Result.success(Base64.getEncoder().encodeToString(hmacBytes));
        } catch (Exception e) {
            return Result.failure("Error generating HMAC:" + e.getMessage());
        }
    }

}
