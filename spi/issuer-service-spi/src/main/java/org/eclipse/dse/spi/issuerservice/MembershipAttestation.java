package org.eclipse.dse.spi.issuerservice;

import java.time.Instant;
import java.util.Map;

public record MembershipAttestation(String id, String holderId, String name, String membershipType, Instant membershipStartDate, Map<String, Object> properties) {
    public MembershipAttestation {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
}
