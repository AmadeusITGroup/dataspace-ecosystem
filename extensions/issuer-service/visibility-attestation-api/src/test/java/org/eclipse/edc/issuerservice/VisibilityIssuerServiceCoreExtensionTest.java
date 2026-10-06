package org.eclipse.edc.issuerservice;

import org.eclipse.dse.spi.issuerservice.VisibilityAttestationStore;
import org.eclipse.edc.issuerservice.api.VisibilityAttestationApiController;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.eclipse.edc.identityhub.spi.webcontext.IdentityHubApiContext.ISSUERADMIN;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(DependencyInjectionExtension.class)
class VisibilityIssuerServiceCoreExtensionTest {

    private final WebService webService = mock();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(WebService.class, webService);
        context.registerService(VisibilityAttestationStore.class, mock());
    }

    @Test
    void shouldRegisterVisibilityApi(VisibilityIssuerServiceCoreExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);

        verify(webService).registerResource(eq(ISSUERADMIN), isA(VisibilityAttestationApiController.class));
    }
}