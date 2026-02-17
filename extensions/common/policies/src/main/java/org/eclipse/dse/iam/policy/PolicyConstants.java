package org.eclipse.dse.iam.policy;

import org.eclipse.edc.dse.common.DseNamespaceConfig;

public class PolicyConstants {
    public static final String MEMBERSHIP_CREDENTIAL_TYPE = "MembershipCredential";
    public static final String DOMAIN_CREDENTIAL_TYPE = "DomainCredential";

    public static final String MEMBERSHIP_CONSTRAINT = "Membership";
    public static final String GENERIC_CLAIM_CONSTRAINT = "GenericClaim";
    public static final String RESTRICTED_CATALOG_DISCOVERY_CONSTRAINT = "RestrictedDiscoveryClaim";

    private final String dseMembershipConstraint;
    private final String dseGenericClaimConstraint;
    private final String dseRestrictedCatalogDiscoveryConstraint;

    public PolicyConstants(DseNamespaceConfig config) {
        this.dseMembershipConstraint = config.dsePolicyNamespace() + MEMBERSHIP_CONSTRAINT;
        this.dseGenericClaimConstraint = config.dsePolicyNamespace() + GENERIC_CLAIM_CONSTRAINT;
        this.dseRestrictedCatalogDiscoveryConstraint = config.dsePolicyNamespace() + RESTRICTED_CATALOG_DISCOVERY_CONSTRAINT;
    }

    public String getDseMembershipConstraint() {
        return dseMembershipConstraint;
    }

    public String getDseGenericClaimConstraint() {
        return dseGenericClaimConstraint;
    }

    public String getDseRestrictedCatalogDiscoveryConstraint() {
        return dseRestrictedCatalogDiscoveryConstraint;
    }
}
