package org.eclipse.dse.iam.policy;

import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyContextImpl;
import org.eclipse.edc.policy.engine.spi.PolicyScope;

public class CatalogDiscoveryPolicyContext extends PolicyContextImpl implements ParticipantAgentPolicyContext {

    @PolicyScope
    public static final String CATALOG_DISCOVERY_SCOPE = "catalog.discovery";

    private final ParticipantAgent agent;

    public CatalogDiscoveryPolicyContext(ParticipantAgent agent) {
        this.agent = agent;
    }

    @Override
    public ParticipantAgent participantAgent() {
        return agent;
    }

    @Override
    public String scope() {
        return CATALOG_DISCOVERY_SCOPE;
    }
}