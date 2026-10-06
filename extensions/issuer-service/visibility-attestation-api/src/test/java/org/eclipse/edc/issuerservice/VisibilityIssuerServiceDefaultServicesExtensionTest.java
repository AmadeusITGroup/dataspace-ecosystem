package org.eclipse.edc.issuerservice;

import org.eclipse.edc.issuerservice.defaults.InMemoryVisibilityAttestationStore;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(DependencyInjectionExtension.class)
class VisibilityIssuerServiceDefaultServicesExtensionTest {

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(CriterionOperatorRegistry.class, mock());
    }

    @Test
    void shouldProvideInMemoryStore(VisibilityIssuerServiceDefaultServicesExtension extension) {
        assertThat(extension.inMemoryVisibilityAttestationStore())
                .isInstanceOf(InMemoryVisibilityAttestationStore.class);
    }
}