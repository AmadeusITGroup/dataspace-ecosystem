/*
 * Copyright (c) 2024 Dataspace Ecosystem
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contributors:
 *      Dataspace Ecosystem - initial API and implementation
 */

package org.eclipse.dse.revocation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.RevocationServiceRegistry;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.token.spi.TokenValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Covers the interim (deprecated-for-removal) extension wiring; suppress [removal] warnings.
@SuppressWarnings("removal")
@ExtendWith(DependencyInjectionExtension.class)
class ResilientRevocationServiceExtensionTest {

    private final RevocationServiceRegistry registry = mock();
    private final TypeManager typeManager = mock();

    @Test
    void initialize_registersRetryingServiceForBitstringType(ServiceExtensionContext context, ObjectFactory factory) {
        when(typeManager.getMapper()).thenReturn(new ObjectMapper());
        context.registerService(RevocationServiceRegistry.class, registry);
        context.registerService(TypeManager.class, typeManager);
        context.registerService(EdcHttpClient.class, mock(EdcHttpClient.class));
        context.registerService(TokenValidationService.class, mock(TokenValidationService.class));
        context.registerService(DidPublicKeyResolver.class, mock(DidPublicKeyResolver.class));

        var extension = factory.constructInstance(ResilientRevocationServiceExtension.class);
        extension.initialize(context);

        // The retrying revocation service must replace the stock one for the bitstring status type.
        verify(registry).addService(eq(BitstringStatusListStatus.TYPE), any(RetryingBitstringStatusListRevocationService.class));
    }
}
