package org.eclipse.edc.dse.telemetry.repository;

public record ContractStats(String contractId, Long msgSize, Long eventCount) {
}