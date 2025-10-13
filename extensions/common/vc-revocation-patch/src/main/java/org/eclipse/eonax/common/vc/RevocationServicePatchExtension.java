package org.eclipse.eonax.common.vc;


import org.eclipse.edc.iam.verifiablecredentials.spi.RevocationListService;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialStatus;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.RevocationServiceRegistry;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.statuslist2021.StatusList2021Status;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ServiceExtension;

import java.util.List;

public class RevocationServicePatchExtension implements ServiceExtension {

    @Inject
    private RevocationServiceRegistry revocationServiceRegistry;

    @Override
    public String name() {
        return "Revocation Service Patch";
    }

    /**
     * As of now revocation service is not stable in the EDC. This extension ensure revocation checks are by-passed.
     */
    @Override
    public void start() {
        List.of(StatusList2021Status.TYPE, BitstringStatusListStatus.TYPE).forEach(type -> revocationServiceRegistry.addService(type, new NoopRevocationListService()));
    }

    private static final class NoopRevocationListService implements RevocationListService {

        @Override
        public Result<Void> checkValidity(CredentialStatus credentialStatus) {
            return Result.success();
        }

        @Override
        public Result<String> getStatusPurpose(VerifiableCredential verifiableCredential) {
            return Result.success(null);
        }
    }

}

