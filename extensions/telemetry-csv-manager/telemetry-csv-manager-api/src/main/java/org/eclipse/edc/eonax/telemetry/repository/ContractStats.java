package org.eclipse.edc.eonax.telemetry.repository;

public record ContractStats(String contractId, Long msgSize, Long eventCount) {
}