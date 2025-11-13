package org.eclipse.dse.iam.policy;

import static org.eclipse.edc.spi.core.CoreConstants.DSE_POLICY_NS;

public interface PolicyConstants {
    String MEMBERSHIP_CREDENTIAL_TYPE = "MembershipCredential";
    String DOMAIN_CREDENTIAL_TYPE = "DomainCredential";

    String MEMBERSHIP_CONSTRAINT = "Membership";
    String DSE_MEMBERSHIP_CONSTRAINT = DSE_POLICY_NS + MEMBERSHIP_CONSTRAINT;

    String GENERIC_CLAIM_CONSTRAINT = "GenericClaim";
    String DSE_GENERIC_CLAIM_CONSTRAINT = DSE_POLICY_NS + GENERIC_CLAIM_CONSTRAINT;
}
