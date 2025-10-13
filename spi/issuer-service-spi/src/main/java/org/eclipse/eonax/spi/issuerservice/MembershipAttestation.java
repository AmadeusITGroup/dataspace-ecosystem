package org.eclipse.eonax.spi.issuerservice;

import java.time.Instant;

public record MembershipAttestation(String id, String holderId, String name, String membershipType, Instant membershipStartDate) {
}
