package org.eclipse.edc.dse.controlplane.asset.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.spi.WebService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset.EDC_ASSET_TYPE;
import static org.eclipse.edc.web.spi.configuration.ApiContext.MANAGEMENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class AssetOntologyValidationExtensionTest {

    @Test
    void shouldRegisterAssetValidator(ServiceExtensionContext context, ObjectFactory factory) {
        var registry = mock(JsonObjectValidatorRegistry.class);
        var typeManager = mock(TypeManager.class);
        var webService = mock(WebService.class);
        when(typeManager.getMapper()).thenReturn(new ObjectMapper());
        context.registerService(JsonObjectValidatorRegistry.class, registry);
        context.registerService(TypeManager.class, typeManager);
        context.registerService(WebService.class, webService);

        var extension = factory.constructInstance(AssetOntologyValidationExtension.class);
        extension.initialize(context);

        verify(registry).register(eq(EDC_ASSET_TYPE), any(AssetOntologyValidator.class));
        verify(webService).registerResource(eq(MANAGEMENT), any(OntologyApiController.class));
    }
}