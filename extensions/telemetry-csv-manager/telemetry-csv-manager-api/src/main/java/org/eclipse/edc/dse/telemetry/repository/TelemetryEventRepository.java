package org.eclipse.edc.dse.telemetry.repository;

import jakarta.persistence.EntityManager;
import org.eclipse.edc.dse.telemetry.model.ParticipantId;
import org.eclipse.edc.dse.telemetry.model.TelemetryEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TelemetryEventRepository extends GenericRepository<TelemetryEvent> {
    public TelemetryEventRepository(EntityManager em) {
        super(em, TelemetryEvent.class);
    }

    public List<TelemetryEvent> findByParticipantIdForMonth(String participantId, Integer month, int year) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = startDate.plusMonths(1).atStartOfDay();
        return em.createQuery("SELECT e FROM TelemetryEvent e WHERE e.participant.id = :participantId AND e.timestamp >= :startDate AND e.timestamp < :endDate ", TelemetryEvent.class)
                .setParameter("participantId", participantId)
                .setParameter("startDate", start)
                .setParameter("endDate", end)
                .getResultList();
    }

    public List<ContractStats> findContractStatsForMonth(String participantId, Integer month, Integer year) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = startDate.plusMonths(1).atStartOfDay();
        return em.createQuery(
                        "SELECT new org.eclipse.edc.dse.telemetry.repository.ContractStats(e.contractId,SUM(e.msgSize),COUNT(e)) FROM TelemetryEvent e " +
                                "WHERE e.participant.id = :participantId AND e.timestamp >= :startDate AND e.timestamp < :endDate GROUP BY e.contractId",
                        ContractStats.class)
                .setParameter("participantId", participantId)
                .setParameter("startDate", start)
                .setParameter("endDate", end)
                .getResultList();
    }

    public ContractStats findContractStatsForContractIdForMonth(String participantId, Integer month, Integer year, String contractId) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = startDate.plusMonths(1).atStartOfDay();
        return em.createQuery(
                        "SELECT new org.eclipse.edc.dse.telemetry.repository.ContractStats(e.contractId,SUM(e.msgSize),COUNT(e)) FROM TelemetryEvent e " +
                                "WHERE e.participant.id = :participantId AND e.timestamp >= :startDate AND e.timestamp < :endDate AND e.contractId = :contractId GROUP BY e.contractId",
                        ContractStats.class)
                .setParameter("participantId", participantId)
                .setParameter("startDate", start)
                .setParameter("endDate", end)
                .setParameter("contractId", contractId)
                .getSingleResult();
    }

    public List<ParticipantId> findContractParties(String contractId) {
        return em.createQuery("SELECT DISTINCT e.participant FROM TelemetryEvent e WHERE e.contractId = :contractId", ParticipantId.class)
                .setParameter("contractId", contractId)
                .getResultList();
    }
}
