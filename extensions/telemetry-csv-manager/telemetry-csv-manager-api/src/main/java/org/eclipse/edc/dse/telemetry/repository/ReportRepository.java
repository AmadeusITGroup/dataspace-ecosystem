package org.eclipse.edc.dse.telemetry.repository;

import jakarta.persistence.EntityManager;
import org.eclipse.edc.dse.telemetry.model.Report;

import java.util.List;

public class ReportRepository extends GenericRepository<Report> {
    public ReportRepository(EntityManager em) {
        super(em, Report.class);
    }

    public List<Report> findByParticipantId(String participantId) {
        return em.createQuery("SELECT r FROM Report r WHERE r.participant.id = :participantId", Report.class)
                .setParameter("participantId", participantId)
                .getResultList();
    }
}
