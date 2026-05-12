package org.eclipse.edc.dse.controlplane;

import jakarta.json.Json;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.transform.transformer.dspace.from.JsonObjectFromDataAddressDspaceTransformer;

import java.util.Map;

import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

/**
 * Replaces the default {@link JsonObjectFromDataAddressDspaceTransformer} in the signaling-api
 * context with a null-safe version that filters out null property values.
 * <p>
 * In EDC v0.15, {@code TransferProcessProtocolServiceImpl.offloadEventualSecretToVault()} sets
 * the {@code secret} property to {@code null} on the DataAddress. The default transformer
 * passes all properties to {@code JsonObjectBuilder.add()}, which throws NPE on null values.
 */
@Extension("Null-safe DataAddress Transformer for Signaling API")
public class NullSafeDataAddressTransformerExtension implements ServiceExtension {

    @Inject
    private TypeTransformerRegistry transformerRegistry;

    @Inject
    private TypeManager typeManager;

    @Override
    public void initialize(ServiceExtensionContext context) {
        var factory = Json.createBuilderFactory(Map.of());
        var signalingApiRegistry = transformerRegistry.forContext("signaling-api");
        signalingApiRegistry.register(new NullSafeJsonObjectFromDataAddressDspaceTransformer(factory, typeManager, JSON_LD));
    }
}
