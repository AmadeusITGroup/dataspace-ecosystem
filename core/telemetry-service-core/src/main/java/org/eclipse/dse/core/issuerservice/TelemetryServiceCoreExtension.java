package org.eclipse.dse.core.issuerservice;

import org.eclipse.dse.spi.telemetry.TelemetryService;
import org.eclipse.dse.spi.telemetry.TelemetryServiceCredentialFactory;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.issuerservice.spi.holder.store.HolderStore;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.token.spi.TokenValidationService;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.time.Clock;


public class TelemetryServiceCoreExtension implements ServiceExtension {

    @Inject
    private Clock clock;

    @Inject
    private TokenValidationService tokenValidationService;

    @Inject
    private DidPublicKeyResolver didPublicKeyResolver;

    @Inject
    private Monitor monitor;

    @Inject
    private TelemetryServiceCredentialFactory credentialFactory;

    @Inject
    private HolderStore holderStore;

    @Inject
    private TransactionContext transactionContext;


    @Override
    public String name() {
        return "Telemetry Service Core";
    }

    @Provider
    public TelemetryService telemetryService() {
        return new TelemetryServiceImpl(tokenValidationService, didPublicKeyResolver, holderStore, transactionContext, credentialFactory);
    }

}


