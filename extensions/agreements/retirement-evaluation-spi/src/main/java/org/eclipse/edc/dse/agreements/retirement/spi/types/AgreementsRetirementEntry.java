/********************************************************************************
 * Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

package org.eclipse.edc.dse.agreements.retirement.spi.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.eclipse.edc.spi.entity.Entity;

import static java.util.Objects.requireNonNull;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

/**
 * Representation of a Contract Agreement Retirement entry, to be stored in the {@link org.eclipse.edc.dse.agreements.retirement.spi.store.AgreementsRetirementStore}.
 * 
 * NOTE: Use {@link AgreementConstants} to get the configurable DSE namespace property names.
 * The Builder requires AgreementConstants to be passed for proper field validation.
 */
public class AgreementsRetirementEntry extends Entity {
    
    public static final String AR_ENTRY_TYPE = EDC_NAMESPACE + "AgreementsRetirementEntry";
    public static final String AR_ENTRY_AGREEMENT_ID = EDC_NAMESPACE + "agreementId";
    
    /**
     * Property name for the retirement reason field.
     *
     * @deprecated Use {@link AgreementConstants#getArEntryReason()} instead.
     */
    @Deprecated(since = "0.6.4", forRemoval = true)
    public static final String AR_ENTRY_REASON = EDC_NAMESPACE + "reason";
    
    /**
     * Property name for the retirement date field.
     *
     * @deprecated Use {@link AgreementConstants#getArEntryRetirementDate()} instead.
     */
    @Deprecated(since = "0.6.4", forRemoval = true)
    public static final String AR_ENTRY_RETIREMENT_DATE = EDC_NAMESPACE + "agreementRetirementDate";

    private String agreementId;
    private String reason;
    private long agreementRetirementDate = 0L;

    public AgreementsRetirementEntry() {}

    public String getAgreementId() {
        return agreementId;
    }

    public String getReason() {
        return reason;
    }

    public long getAgreementRetirementDate() {
        return agreementRetirementDate;
    }

    public static class Builder extends Entity.Builder<AgreementsRetirementEntry, AgreementsRetirementEntry.Builder> {

        private AgreementConstants agreementConstants;

        private Builder() {
            super(new AgreementsRetirementEntry());
        }
        
        private Builder(AgreementConstants agreementConstants) {
            super(new AgreementsRetirementEntry());
            this.agreementConstants = agreementConstants;
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }
        
        public static Builder newInstance(AgreementConstants agreementConstants) {
            return new Builder(agreementConstants);
        }

        public Builder withAgreementId(String agreementId) {
            this.entity.agreementId = agreementId;
            return this;
        }

        public Builder withReason(String reason) {
            this.entity.reason = reason;
            return this;
        }

        public Builder withAgreementRetirementDate(long agreementRetirementDate) {
            this.entity.agreementRetirementDate = agreementRetirementDate;
            return this;
        }

        @Override
        public Builder self() {
            return this;
        }

        @Override
        public AgreementsRetirementEntry build() {
            super.build();
            requireNonNull(entity.agreementId, AR_ENTRY_AGREEMENT_ID);
            
            // Use configurable constants if provided, otherwise use default field name for backward compatibility
            var reasonFieldName = agreementConstants != null ? agreementConstants.getArEntryReason() : "reason";
            requireNonNull(entity.reason, "Field '" + reasonFieldName + "' must not be null");

            if (entity.agreementRetirementDate == 0L) {
                entity.agreementRetirementDate = this.entity.clock.instant().getEpochSecond();
            }

            return entity;
        }
    }
}
