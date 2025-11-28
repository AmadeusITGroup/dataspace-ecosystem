package org.eclipse.edc.dse.telemetry.services.report;

import jakarta.persistence.EntityManager;
import org.eclipse.edc.dse.telemetry.repository.JpaUtil;
import org.eclipse.edc.dse.telemetry.repository.ParticipantRepository;
import org.eclipse.edc.dse.telemetry.repository.ReportRepository;
import org.eclipse.edc.dse.telemetry.repository.TelemetryEventRepository;
import org.eclipse.edc.dse.telemetry.services.storage.AzureStorageService;
import org.eclipse.edc.spi.monitor.Monitor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ReportGeneratorScheduler {

    private final Monitor monitor;
    private final AzureStorageService azureStorageService;
    private final ScheduledExecutorService executor;
    private final Clock clock;

    public ReportGeneratorScheduler(Monitor monitor, AzureStorageService azureStorageService, Clock clock) {
        this.monitor = monitor;
        this.azureStorageService = azureStorageService;
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.clock = clock;
    }

    public void start() {
        monitor.info("Starting report generation job at " + Instant.now());
        scheduleNextMonthlyRun();
        monitor.info("Finished report generation job at " + Instant.now());
    }

    void scheduleNextMonthlyRun() {
        long delaySeconds = computeDelayUntilNextMonthlyRun();

        this.executor.schedule(() -> {
            try {
                triggerGeneration();
            } catch (Exception e) {
                this.monitor.severe("[Report Scheduler] Report generation failed!\n" + e.getMessage());
            } finally {
                // schedule next run regardless of success/failure
                scheduleNextMonthlyRun();
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    // Report generation always runs on 2nd day of each month at 02:00 AM
    long computeDelayUntilNextMonthlyRun() {
        ZonedDateTime now = ZonedDateTime.now(this.clock);
        ZonedDateTime nextRun = now.withDayOfMonth(2)
                .withHour(2)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusMonths(1);
        }

        return Duration.between(now, nextRun).getSeconds();
    }

    void triggerGeneration() {
        // The entity manager is not thread-safe, so we create a new one for each execution
        // The manager should not be shared between threads otherwise we could get ConcurrentModificationException for example
        // Also, it holds persistence context (first-level cache of every entity we ever touched), so if we keep it open for too long,
        // we could run out of memory
        this.monitor.info("Triggering report generation job at " + ZonedDateTime.now(this.clock));
        EntityManager em = JpaUtil.createEntityManager();
        ReportGenerationService service = buildGenerationService(em);
        try {
            service.generatePreviousMonthReportForAllParticipants();
        } catch (Exception e) {
            this.monitor.severe("Error running report scheduler", e);
            throw e;
        } finally {
            //service.generateErrorReport(LocalDateTime.now(), ReportGenerationService.getErrors());
            if (em.isOpen()) {
                em.close();
            }
        }
    }

    public boolean checkParticipantExists(String participantName) {
        this.monitor.debug("Checking participant " + participantName + " exists");
        EntityManager em = JpaUtil.createEntityManager();
        try {
            ReportGenerationService service = buildGenerationService(em);
            return service.findParticipant(participantName) != null;
        } catch (Exception e) {
            this.monitor.debug("Error checking if participant " + participantName + " exists", e);
            throw e;
        } finally {
            if (em.isOpen()) {
                em.close();
            }
        }
    }

    public void triggerGenerationForParticipant(String participantName, LocalDateTime reportDateTime, Boolean generateCounterpartyReport) {
        this.monitor.info("Triggering report generation job at " + ZonedDateTime.now(this.clock));
        EntityManager em = JpaUtil.createEntityManager();
        ReportGenerationService service = buildGenerationService(em);
        try {
            service.generateParticipantReport(participantName, reportDateTime, generateCounterpartyReport);
        } catch (Exception e) {
            this.monitor.severe("Error running report scheduler", e);
            throw e;
        } finally {
            //service.generateErrorReport(reportDateTime, ReportGenerationService.getErrors());
            if (em.isOpen()) {
                em.close();
            }
        }
    }

    private ReportGenerationService buildGenerationService(EntityManager em) {
        ParticipantRepository participantRepository = new ParticipantRepository(em);
        ReportRepository reportRepository = new ReportRepository(em);
        TelemetryEventRepository telemetryEventRepository = new TelemetryEventRepository(em);
        return new ReportGenerationService(this.monitor, participantRepository, reportRepository, telemetryEventRepository, this.azureStorageService);
    }

    public void stop() {
        this.monitor.info("Stopping ReportGeneratorScheduler...");
        this.executor.shutdown();
    }
}
