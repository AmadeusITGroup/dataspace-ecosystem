package org.eclipse.edc.dse.controlplane;

import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class NullSafeDataAddressTransformerExtensionTest {

    private final TypeTransformerRegistry transformerRegistry = mock();
    private final TypeTransformerRegistry signalingApiRegistry = mock();
    private final TypeManager typeManager = mock();

    @BeforeEach
    void setUp(org.eclipse.edc.spi.system.ServiceExtensionContext context) {
        context.registerService(TypeTransformerRegistry.class, transformerRegistry);
        context.registerService(TypeManager.class, typeManager);
        when(transformerRegistry.forContext(eq("signaling-api"))).thenReturn(signalingApiRegistry);
    }

    @Test
    void initialize_shouldRegisterTransformer(NullSafeDataAddressTransformerExtension ext,
                                              org.eclipse.edc.spi.system.ServiceExtensionContext context) {
        ext.initialize(context);

        verify(transformerRegistry).forContext("signaling-api");
        verify(signalingApiRegistry).register(any(NullSafeJsonObjectFromDataAddressDspaceTransformer.class));
    }
}
