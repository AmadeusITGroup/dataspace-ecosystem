package org.eclipse.edc.dse.agreements.retirement.spi.types;

import org.eclipse.edc.dse.common.DseNamespaceConfig;

/**
 * Constants for agreement retirement JSON-LD schema, dynamically built from DseNamespaceConfig.
 * These constants define the property names used in JSON-LD serialization of agreement retirement entries.
 */
public class AgreementConstants {
    
    private final String arEntryReason;
    private final String arEntryRetirementDate;

    public AgreementConstants(DseNamespaceConfig config) {
        this.arEntryReason = config.dseNamespace() + "reason";
        this.arEntryRetirementDate = config.dseNamespace() + "agreementRetirementDate";
    }

    public String getArEntryReason() {
        return arEntryReason;
    }

    public String getArEntryRetirementDate() {
        return arEntryRetirementDate;
    }
}
