package org.eclipse.dse.spi.issuerservice;

public record VisibilityAttestation(String id, String holderId, VisibilityScope visibilityScope) {
}