package org.eclipse.edc.test.system;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.jersey.mapper.EdcApiExceptionMapper;
import org.eclipse.edc.web.spi.WebService;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.UUID;

public class ProviderBackendServiceExtension implements ServiceExtension {

    @Inject
    private WebService webService;

    @Override
    public String name() {
        return "[TEST] Backend service";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var keys = generateKeyPair();
        webService.registerResource(new ProviderBackendApiController((ECPublicKey) keys.getPublic(), context.getMonitor()));
        webService.registerResource(new ProviderOauth2TokenController(context.getMonitor(), (ECPrivateKey) keys.getPrivate()));
        webService.registerResource(new EdcApiExceptionMapper());
    }

    public static KeyPair generateKeyPair() {
        try {
            return new ECKeyGenerator(Curve.P_256)
                    .keyUse(KeyUse.SIGNATURE) // indicate the intended use of the key
                    .keyID(UUID.randomUUID().toString()) // give the key a unique ID
                    .generate()
                    .toKeyPair();
        } catch (JOSEException e) {
            throw new EdcException(e);
        }
    }
}
