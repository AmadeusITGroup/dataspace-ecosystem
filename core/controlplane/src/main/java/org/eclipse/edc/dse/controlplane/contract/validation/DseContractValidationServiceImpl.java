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
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ContractValidationService;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ValidatableConsumerOffer;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ValidatedConsumerOffer;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.result.Result;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import static java.lang.String.format;
import static org.eclipse.edc.spi.result.Result.failure;
import static org.eclipse.edc.spi.result.Result.success;

/**
 * DSE's own version of {@link ContractValidationService}.
 *
 * <p>This is basically a copy-paste of upstream EDC's {@code ContractValidationServiceImpl}
 * ({@code org.eclipse.edc.connector.controlplane.contract.validation.ContractValidationServiceImpl}),
 * with one small but important change.
 *
 * <p>Upstream checks whether the offered asset belongs to the contract definition's
 * assetsSelector by calling {@link AssetIndex#countAssets(java.util.List)}, which re-runs the
 * assetsSelector as a query against the {@link AssetIndex} (e.g. a SQL query). The problem is that this
 * query-based approach doesn't always understand nested/JSON-LD-wrapped asset property paths
 * (e.g. {@code http://www.w3.org/ns/dcat#distribution.http://www.w3.org/ns/dcat#accessService}).
 *
 * <p>So instead, {@link #validateInitialOfferHelper(ValidatableConsumerOffer, ParticipantAgent)} here
 * takes the target {@link Asset} we already fetched and checks the assetsSelector
 * {@link Criterion}s against it directly, in memory, using {@link CriterionOperatorRegistry}.
 *
 * <p>For this to correctly resolve nested/JSON-LD paths, a property-lookup implementation such as
 * {@code DseAssetPropertyLookup} needs to be registered on the {@link CriterionOperatorRegistry}.
 *
 * <p>Everything else in this class works exactly like upstream.
 */
public class DseContractValidationServiceImpl implements ContractValidationService {

    private final AssetIndex assetIndex;
    private final PolicyEngine policyEngine;
    private final PolicyEquality policyEquality;
    private final CriterionOperatorRegistry criterionOperatorRegistry;

    public DseContractValidationServiceImpl(AssetIndex assetIndex,
                                             PolicyEngine policyEngine,
                                             PolicyEquality policyEquality,
                                             CriterionOperatorRegistry criterionOperatorRegistry) {
        this.assetIndex = assetIndex;
        this.policyEngine = policyEngine;
        this.policyEquality = policyEquality;
        this.criterionOperatorRegistry = criterionOperatorRegistry;
    }

    @Override
    public @NotNull Result<ValidatedConsumerOffer> validateInitialOffer(ParticipantAgent agent, ValidatableConsumerOffer consumerOffer) {
        return validateInitialOfferHelper(consumerOffer, agent)
                .compose(policy -> createContractOffer(policy, consumerOffer.getOfferId()))
                .map(contractOffer -> new ValidatedConsumerOffer(agent.getIdentity(), contractOffer));
    }

    @Override
    public @NotNull Result<ContractAgreement> validateAgreement(ParticipantAgent agent, ContractAgreement agreement) {
        var consumerIdentity = agent.getIdentity();
        if (consumerIdentity == null || !consumerIdentity.equals(agreement.getConsumerId())) {
            return failure("Invalid provider credentials");
        }

        var policyContext = new TransferProcessPolicyContext(agent, agreement, Instant.now());
        var policyResult = policyEngine.evaluate(agreement.getPolicy(), policyContext);
        if (!policyResult.succeeded()) {
            return failure(format("Policy does not fulfill the agreement %s, policy evaluation %s",
                    agreement.getId(), policyResult.getFailureDetail()));
        }
        return success(agreement);
    }

    @Override
    public @NotNull Result<Void> validateRequest(ParticipantAgent agent, ContractAgreement agreement) {
        return Optional.ofNullable(agent.getIdentity())
                .filter(id -> id.equals(agreement.getConsumerId()) || id.equals(agreement.getProviderId()))
                .map(id -> Result.success())
                .orElse(Result.failure("Invalid counter-party identity"));
    }

    @Override
    public @NotNull Result<Void> validateRequest(ParticipantAgent agent, ContractNegotiation negotiation) {
        var counterPartyIdentity = agent.getIdentity();
        return counterPartyIdentity != null && counterPartyIdentity.equals(negotiation.getCounterPartyId())
                ? success() : failure("Invalid counter-party identity");
    }

    @Override
    public @NotNull Result<Void> validateConfirmed(ParticipantAgent agent, ContractAgreement agreement, ContractOffer latestOffer) {
        if (!Objects.equals(agent.getIdentity(), agreement.getProviderId())) {
            return failure("Invalid provider credentials");
        }

        if (latestOffer == null) {
            return failure("No offer found");
        }

        return policyEquality.test(agreement.getPolicy().withTarget(latestOffer.getAssetId()), latestOffer.getPolicy())
                ? success()
                : failure("Policy in the contract agreement is not equal to the one in the contract offer");
    }

    /**
     * Validates an initial contract offer, ensuring that the referenced asset exists, is selected by the
     * corresponding policy definition and the agent fulfills the contract policy.
     * A sanitized policy definition is returned to avoid clients injecting manipulated policies.
     */
    private Result<Policy> validateInitialOfferHelper(ValidatableConsumerOffer consumerOffer, ParticipantAgent agent) {
        var accessResult = validateConsumerAccess(consumerOffer, agent);
        if (accessResult.failed()) {
            return accessResult.mapFailure();
        }

        var assetResult = validateTargetAsset(consumerOffer);
        if (assetResult.failed()) {
            return assetResult.mapFailure();
        }

        var contractPolicy = consumerOffer.getContractPolicy().withTarget(consumerOffer.getOfferId().assetIdPart());
        return policyEngine.evaluate(contractPolicy, new ContractNegotiationPolicyContext(agent))
                .map(v -> contractPolicy);
    }

    /**
     * Verifies the requesting agent has an identity and that it fulfills the offer's access policy.
     */
    private Result<Void> validateConsumerAccess(ValidatableConsumerOffer consumerOffer, ParticipantAgent agent) {
        if (agent.getIdentity() == null) {
            return failure("Invalid consumer identity");
        }

        var accessPolicyResult = policyEngine.evaluate(consumerOffer.getAccessPolicy(), new CatalogPolicyContext(agent));
        return accessPolicyResult.succeeded() ? success() : accessPolicyResult.mapFailure();
    }

    /**
     * Verifies the target asset exists and is actually selected by the contract definition's assetsSelector.
     */
    private Result<Asset> validateTargetAsset(ValidatableConsumerOffer consumerOffer) {
        var targetAsset = assetIndex.findById(consumerOffer.getOfferId().assetIdPart());
        if (targetAsset == null) {
            return failure("Invalid target: " + consumerOffer.getOfferId().assetIdPart());
        }

        // verify that the asset in the offer is actually in the contract definition
        var testCriteria = new ArrayList<>(consumerOffer.getContractDefinition().getAssetsSelector());
        testCriteria.add(new Criterion(Asset.PROPERTY_ID, "=", consumerOffer.getOfferId().assetIdPart()));

        return matchesAssetsSelector(targetAsset, testCriteria)
                ? success(targetAsset)
                : failure("Asset ID from the ContractOffer is not included in the ContractDefinition");
    }

    /**
     * Evaluates the contract definition's assetsSelector {@link Criterion}s directly against the
     * already-fetched {@code targetAsset}, in-memory, via {@link CriterionOperatorRegistry}. This
     * correctly resolves nested/JSON-LD-wrapped asset property paths that a plain
     * {@link AssetIndex#countAssets(java.util.List)} call might not, provided a JSON-LD-aware
     * {@link org.eclipse.edc.spi.query.PropertyLookup} is registered on the registry.
     *
     * <p><strong>Important — criteria are evaluated independently, not correlated.</strong>
     * Each {@link Criterion} is converted to a {@link java.util.function.Predicate} individually and
     * then ANDed together. When a criterion path traverses a nested array (e.g. via
     * {@code skos:hasTopConcept}), the predicate checks whether <em>any</em> element in that array
     * satisfies the condition — it does not require the same array element to satisfy all criteria.
     *
     * <p>For example, given an asset with:
     * <pre>
     * "skos:hasTopConcept": [
     *   { "skos:notation": "50", "skos:narrower": [ { "skos:notation": "111" } ] },
     *   { "skos:notation": "60", "skos:narrower": [ { "skos:notation": "990" } ] }
     * ]
     * </pre>
     * a selector with two criteria:
     * <pre>
     * { "operandLeft": "skos:notation",                         "operator": "=", "operandRight": "50" }
     * { "operandLeft": "skos:notation.skos:narrower.skos:notation", "operator": "=", "operandRight": "990" }
     * </pre>
     * will match this asset, because:
     * <ul>
     *   <li>criterion 1 is satisfied by the element with {@code notation = "50"}</li>
     *   <li>criterion 2 is satisfied by the element with {@code notation = "60"} (which has narrower "990")</li>
     * </ul>
     * The evaluation does <em>not</em> verify that both conditions are met by the <em>same</em>
     * top-concept element. In other words, it only checks whether each value exists somewhere in
     * the structure — it does not enforce that a notation of "50" and a narrower notation of "990"
     * belong to the same parent concept.
     */
    private boolean matchesAssetsSelector(Asset targetAsset, List<Criterion> testCriteria) {
        Predicate<Asset> assetPredicate = testCriteria.stream()
                .map(criterionOperatorRegistry::<Asset>toPredicate)
                .reduce(Predicate::and)
                .orElse(a -> true);

        return assetPredicate.test(targetAsset);
    }

    @NotNull
    private Result<ContractOffer> createContractOffer(Policy policy, ContractOfferId contractOfferId) {
        if (!contractOfferId.assetIdPart().equals(policy.getTarget())) {
            return Result.failure("Policy target %s does not match the asset ID in the contract offer %s"
                    .formatted(policy.getTarget(), contractOfferId.assetIdPart()));
        }
        return Result.success(ContractOffer.Builder.newInstance()
                .id(contractOfferId.toString())
                // we copy the policy and enforce it to be of type OFFER
                .policy(policy.toBuilder().type(PolicyType.OFFER).build())
                .assetId(contractOfferId.assetIdPart())
                .build());
    }

}
