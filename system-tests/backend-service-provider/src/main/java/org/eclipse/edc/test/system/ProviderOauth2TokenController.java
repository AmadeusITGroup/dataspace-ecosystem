package org.eclipse.edc.test.system;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;

import java.security.interfaces.ECPrivateKey;
import java.util.Map;
import java.util.UUID;

import static java.lang.String.format;

@Path("/oauth2")
public class ProviderOauth2TokenController {

    private static final String EXPECTED_CLIENT_ID = "clientId";
    private static final String EXPECTED_CLIENT_SECRET = "supersecret";
    private final Monitor monitor;
    private final ECPrivateKey privateKey;

    public ProviderOauth2TokenController(Monitor monitor, ECPrivateKey privateKey) {
        this.monitor = monitor;
        this.privateKey = privateKey;
    }

    @POST
    @Path("/token")
    public Map<String, String> getToken(@FormParam("client_id") String clientId, @FormParam("client_secret") String clientSecret) {
        monitor.info("Oauth2 token requested");
        if (!EXPECTED_CLIENT_ID.equals(clientId) || !EXPECTED_CLIENT_SECRET.equals(clientSecret)) {
            var message = format("Cannot validate token request, parameters are not valid: client_id %s - client_secret %s", clientId, clientSecret);
            monitor.severe(message);
            throw new InvalidRequestException(message);
        }
        return Map.of("access_token", createToken());
    }

    private String createToken() {
        try {
            var claims = new JWTClaimsSet.Builder().build();
            var header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(UUID.randomUUID().toString()).build();
            var jwt = new SignedJWT(header, claims);
            jwt.sign(new ECDSASigner(privateKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

}
