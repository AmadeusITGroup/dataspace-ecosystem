package org.eclipse.eonax.iam.policy;

import static org.eclipse.edc.spi.core.CoreConstants.EONAX_POLICY_NS;

public interface PolicyConstants {
    String MEMBERSHIP_CREDENTIAL_TYPE = "MembershipCredential";
    String DOMAIN_CREDENTIAL_TYPE = "DomainCredential";

    String MEMBERSHIP_CONSTRAINT = "Membership";
    String EONAX_MEMBERSHIP_CONSTRAINT = EONAX_POLICY_NS + MEMBERSHIP_CONSTRAINT;

    String GENERIC_CLAIM_CONSTRAINT = "GenericClaim";
    String EONAX_GENERIC_CLAIM_CONSTRAINT = EONAX_POLICY_NS + GENERIC_CLAIM_CONSTRAINT;
}
