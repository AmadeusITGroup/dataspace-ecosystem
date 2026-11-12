/*
 *  Copyright (c) 2024 Amadeus IT Group
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus IT Group - initial API and implementation
 *
 */

package org.eclipse.edc.dse.controlplane.contract.validation;

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.policy.PolicyEquality;
import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ValidatableConsumerOffer;
import org.eclipse.edc.dse.controlplane.query.asset.DseAssetPropertyLookup;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DseContractValidationServiceImpl}.
 *
 * <p>Tests are grouped by the method under test (one {@code @Nested} class per public method),
 * so each group can be read on its own without scrolling through the whole file.
 *
 * <p>The {@code ValidateInitialOffer} group is the most important one: it verifies that the
 * contract definition's assetsSelector criteria are evaluated in-memory against the already-fetched
 * target {@link Asset}, via {@link CriterionOperatorRegistry}, instead of delegating to
 * {@link AssetIndex#countAssets(java.util.List)} — see {@link DseContractValidationServiceImpl}'s
 * class Javadoc for why.
 */
class DseContractValidationServiceImplTest {

    private static final String CONSUMER_ID = "consumer";
    private static final String PROVIDER_ID = "provider";
    private static final String ASSET_ID = "1";

    private final AssetIndex assetIndex = mock();
    private final PolicyEngine policyEngine = mock();
    private final PolicyEquality policyEquality = mock();
    private final ParticipantAgent agent = mock();
    private final CriterionOperatorRegistry criterionOperatorRegistry = CriterionOperatorRegistryImpl.ofDefaults();

    private final DseContractValidationServiceImpl validationService =
            new DseContractValidationServiceImpl(assetIndex, policyEngine, policyEquality, criterionOperatorRegistry);

    @BeforeEach
    void setUp() {
        criterionOperatorRegistry.registerPropertyLookup(new DseAssetPropertyLookup());
    }

    @Nested
    class ValidateInitialOffer {

        @BeforeEach
        void setUp() {
            when(agent.getIdentity()).thenReturn(CONSUMER_ID);
            when(policyEngine.evaluate(any(), isA(CatalogPolicyContext.class))).thenReturn(Result.success());
            when(policyEngine.evaluate(any(), isA(ContractNegotiationPolicyContext.class))).thenReturn(Result.success());
        }

        @Test
        void shouldSucceed_whenAssetMatchesAssetsSelector() {
            var asset = Asset.Builder.newInstance().id(ASSET_ID).property("keyword", "BUSINESS").build();
            when(assetIndex.findById(ASSET_ID)).thenReturn(asset);
            var consumerOffer = validatableConsumerOffer(contractDefinition(new Criterion("keyword", "=", "BUSINESS")));

            var result = validationService.validateInitialOffer(agent, consumerOffer);

            assertThat(result.succeeded()).isTrue();
            assertThat(result.getContent().getOffer().getAssetId()).isEqualTo(ASSET_ID);
            verify(assetIndex, never()).countAssets(any());
        }

        @Test
        void shouldFail_whenAssetDoesNotMatchAssetsSelector() {
            var asset = Asset.Builder.newInstance().id(ASSET_ID).property("keyword", "BUSINESS").build();
            when(assetIndex.findById(ASSET_ID)).thenReturn(asset);
            var consumerOffer = validatableConsumerOffer(contractDefinition(new Criterion("keyword", "=", "PERSONAL")));

            var result = validationService.validateInitialOffer(agent, consumerOffer);

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureMessages()).anyMatch(m -> m.contains("not included in the ContractDefinition"));
            verify(assetIndex, never()).countAssets(any());
        }

        @Test
        void shouldFail_whenTargetAssetNotFound() {
            when(assetIndex.findById(anyString())).thenReturn(null);
            var consumerOffer = validatableConsumerOffer(contractDefinition());

            var result = validationService.validateInitialOffer(agent, consumerOffer);

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureMessages()).anyMatch(m -> m.contains("Invalid target"));
        }

        @Test
        void shouldFail_whenConsumerIdentityIsMissing() {
            when(agent.getIdentity()).thenReturn(null);
            var consumerOffer = validatableConsumerOffer(contractDefinition());

            var result = validationService.validateInitialOffer(agent, consumerOffer);

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureMessages()).anyMatch(m -> m.contains("Invalid consumer identity"));
        }

        @Test
        void shouldFail_whenAccessPolicyEvaluationFails() {
            when(policyEngine.evaluate(any(), isA(CatalogPolicyContext.class))).thenReturn(Result.failure("access denied"));
            var consumerOffer = validatableConsumerOffer(contractDefinition());

            var result = validationService.validateInitialOffer(agent, consumerOffer);

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureMessages()).anyMatch(m -> m.contains("access denied"));
        }

        @Test
        void shouldFail_whenContractPolicyEvaluationFails() {
            var asset = Asset.Builder.newInstance().id(ASSET_ID).build();
            when(assetIndex.findById(ASSET_ID)).thenReturn(asset);
            when(policyEngine.evaluate(any(), isA(ContractNegotiationPolicyContext.class))).thenReturn(Result.failure("contract policy not fulfilled"));
            var consumerOffer = validatableConsumerOffer(contractDefinition());

            var result = validationService.validateInitialOffer(agent, consumerOffer);

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureMessages()).anyMatch(m -> m.contains("contract policy not fulfilled"));
        }

        private ContractDefinition contractDefinition(Criterion... assetsSelector) {
            return ContractDefinition.Builder.newInstance()
                    .id("cd1")
                    .accessPolicyId("access")
                    .contractPolicyId("contract")
                    .assetsSelector(List.of(assetsSelector))
                    .build();
        }

        private ValidatableConsumerOffer validatableConsumerOffer(ContractDefinition contractDefinition) {
            return ValidatableConsumerOffer.Builder.newInstance()
                    .offerId(ContractOfferId.create("def1", ASSET_ID))
                    .accessPolicy(Policy.Builder.newInstance().build())
                    .contractPolicy(Policy.Builder.newInstance().build())
                    .contractDefinition(contractDefinition)
                    .build();
        }
    }

    @Nested
    class ValidateAgreement {

        @Test
        void shouldSucceed_whenIdentityMatchesAndPolicyIsFulfilled() {
            when(agent.getIdentity()).thenReturn(CONSUMER_ID);
            when(policyEngine.evaluate(any(), isA(TransferProcessPolicyContext.class))).thenReturn(Result.success());

            var result = validationService.validateAgreement(agent, contractAgreement());

            assertThat(result.succeeded()).isTrue();
        }

        @Test
        void shouldFail_whenIdentityIsMissing() {
            when(agent.getIdentity()).thenReturn(null);

            var result = validationService.validateAgreement(agent, contractAgreement());

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureMessages()).anyMatch(m -> m.contains("Invalid provider credentials"));
        }

        @Test
        void shouldFail_whenIdentityDoesNotMatchConsumer() {
            when(agent.getIdentity()).thenReturn("someone-else");

            var result = validationService.validateAgreement(agent, contractAgreement());

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureMessages()).anyMatch(m -> m.contains("Invalid provider credentials"));
        }

        @Test
        void shouldFail_whenPolicyDoesNotFulfillTheAgreement() {
            when(agent.getIdentity()).thenReturn(CONSUMER_ID);
            when(policyEngine.evaluate(any(), isA(TransferProcessPolicyContext.class))).thenReturn(Result.failure("expired"));

            var result = validationService.validateAgreement(agent, contractAgreement());

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureMessages()).anyMatch(m -> m.contains("does not fulfill the agreement"));
        }

        private ContractAgreement contractAgreement() {
            return ContractAgreement.Builder.newInstance()
                    .id("agreement1")
                    .providerId(PROVIDER_ID)
                    .consumerId(CONSUMER_ID)
                    .assetId(ASSET_ID)
                    .policy(Policy.Builder.newInstance().build())
                    .build();
        }
    }

    @Nested
    class ValidateRequestForAgreement {

        @Test
        void shouldSucceed_whenIdentityMatchesConsumer() {
            when(agent.getIdentity()).thenReturn(CONSUMER_ID);

            var result = validationService.validateRequest(agent, contractAgreement());

            assertThat(result.succeeded()).isTrue();
        }

        @Test
        void shouldSucceed_whenIdentityMatchesProvider() {
            when(agent.getIdentity()).thenReturn(PROVIDER_ID);

            var result = validationService.validateRequest(agent, contractAgreement());

            assertThat(result.succeeded()).isTrue();
        }

        @Test
        void shouldFail_whenIdentityMatchesNeitherConsumerNorProvider() {
            when(agent.getIdentity()).thenReturn("someone-else");

            var result = validationService.validateRequest(agent, contractAgreement());

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureMessages()).anyMatch(m -> m.contains("Invalid counter-party identity"));
        }

        @Test
        void shouldFail_whenIdentityIsMissing() {
            when(agent.getIdentity()).thenReturn(null);

            var result = validationService.validateRequest(agent, contractAgreement());

            assertThat(result.failed()).isTrue();
        }

        private ContractAgreement contractAgreement() {
            return ContractAgreement.Builder.newInstance()
                    .id("agreement1")
                    .providerId(PROVIDER_ID)
                    .consumerId(CONSUMER_ID)
                    .assetId(ASSET_ID)
                    .policy(Policy.Builder.newInstance().build())
                    .build();
        }
    }

    @Nested
    class ValidateRequestForNegotiation {

        @Test
        void shouldSucceed_whenIdentityMatchesCounterParty() {
            when(agent.getIdentity()).thenReturn("counter-party");
            var negotiation = mock(ContractNegotiation.class);
            when(negotiation.getCounterPartyId()).thenReturn("counter-party");

            var result = validationService.validateRequest(agent, negotiation);

            assertThat(result.succeeded()).isTrue();
        }

        @Test
        void shouldFail_whenIdentityDoesNotMatchCounterParty() {
            when(agent.getIdentity()).thenReturn("someone-else");
            var negotiation = mock(ContractNegotiation.class);
            when(negotiation.getCounterPartyId()).thenReturn("counter-party");

            var result = validationService.validateRequest(agent, negotiation);

            assertThat(result.failed()).isTrue();
        }

        @Test
        void shouldFail_whenIdentityIsMissing() {
            when(agent.getIdentity()).thenReturn(null);
            var negotiation = mock(ContractNegotiation.class);
            when(negotiation.getCounterPartyId()).thenReturn("counter-party");

            var result = validationService.validateRequest(agent, negotiation);

            assertThat(result.failed()).isTrue();
        }
    }

    @Nested
    class ValidateConfirmed {

        @Test
        void shouldSucceed_whenProviderIdentityMatchesAndPoliciesAreEqual() {
            when(agent.getIdentity()).thenReturn(PROVIDER_ID);
            when(policyEquality.test(any(), any())).thenReturn(true);

            var result = validationService.validateConfirmed(agent, contractAgreement(), contractOffer());

            assertThat(result.succeeded()).isTrue();
        }

        @Test
        void shouldFail_whenProviderIdentityDoesNotMatch() {
            when(agent.getIdentity()).thenReturn("someone-else");

            var result = validationService.validateConfirmed(agent, contractAgreement(), contractOffer());

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureMessages()).anyMatch(m -> m.contains("Invalid provider credentials"));
        }

        @Test
        void shouldFail_whenLatestOfferIsMissing() {
            when(agent.getIdentity()).thenReturn(PROVIDER_ID);

            var result = validationService.validateConfirmed(agent, contractAgreement(), null);

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureMessages()).anyMatch(m -> m.contains("No offer found"));
        }

        @Test
        void shouldFail_whenAgreementPolicyDoesNotMatchOfferPolicy() {
            when(agent.getIdentity()).thenReturn(PROVIDER_ID);
            when(policyEquality.test(any(), any())).thenReturn(false);

            var result = validationService.validateConfirmed(agent, contractAgreement(), contractOffer());

            assertThat(result.failed()).isTrue();
            assertThat(result.getFailureMessages()).anyMatch(m -> m.contains("not equal to the one in the contract offer"));
        }

        private ContractAgreement contractAgreement() {
            return ContractAgreement.Builder.newInstance()
                    .id("agreement1")
                    .providerId(PROVIDER_ID)
                    .consumerId(CONSUMER_ID)
                    .assetId(ASSET_ID)
                    .policy(Policy.Builder.newInstance().build())
                    .build();
        }

        private ContractOffer contractOffer() {
            return ContractOffer.Builder.newInstance()
                    .id(ContractOfferId.create("def1", ASSET_ID).toString())
                    .assetId(ASSET_ID)
                    .policy(Policy.Builder.newInstance().build())
                    .build();
        }
    }
}
