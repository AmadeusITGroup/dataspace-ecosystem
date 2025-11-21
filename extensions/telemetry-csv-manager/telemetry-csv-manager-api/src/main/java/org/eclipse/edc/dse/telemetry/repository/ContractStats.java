package org.eclipse.edc.dse.telemetry.repository;

public record ContractStats(String contractId, Integer responseStatus, Long msgSize, Long eventCount) {
}