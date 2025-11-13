package org.eclipse.edc.dse.telemetry.services.report;

import org.eclipse.edc.dse.telemetry.model.ParticipantId;
import org.eclipse.edc.dse.telemetry.model.Report;
import org.eclipse.edc.dse.telemetry.model.TelemetryEvent;
import org.eclipse.edc.dse.telemetry.repository.ContractStats;
import org.eclipse.edc.dse.telemetry.repository.ParticipantRepository;
import org.eclipse.edc.dse.telemetry.repository.ReportRepository;
import org.eclipse.edc.dse.telemetry.repository.TelemetryEventRepository;
import org.eclipse.edc.dse.telemetry.services.ReportUtil;
import org.eclipse.edc.dse.telemetry.services.storage.AzureStorageService;
import org.eclipse.edc.spi.monitor.Monitor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.eclipse.edc.dse.telemetry.services.ReportUtil.generateCsvErrorReportContent;
import static org.eclipse.edc.dse.telemetry.services.ReportUtil.getObjectPath;

public class ReportGenerationService {

    private final ParticipantRepository participantRepository;
    private final ReportRepository reportRepository;
    private final TelemetryEventRepository telemetryEventRepository;
    private final AzureStorageService azureStorageService;
    private final Monitor monitor;

    // I'm using thread safe data structure here because there can be concurrent accesses to this list.
    // Current edge case: We generate a report on the demand at the same time the cron task is generating the monthly report on the background for the same or a different month
    // Future cases: If we allow the generation of reports via UI in the future this edge case will be a common use case with concurrent accesses of the users
    //
    // Warning: There is a design flaw here, if we trigger an on-demand generation and the monthly generation task is running with the exact same month and year and both of them fail the validation,
    // only one error report will be generated with errors from both. However, the frequency of this use case is almost inexistent, and our main goal is to have generation in-place, validation is secondary.
    // To tackle this issue and having a kubernetes cronjob, the reports will be tackled in another US -> FDPT-84292
    static final Queue<ReportGenerationError> ERRORS = new ConcurrentLinkedQueue<>();

    public ReportGenerationService(Monitor monitor,
                                   ParticipantRepository participantRepository,
                                   ReportRepository reportRepository,
                                   TelemetryEventRepository telemetryEventRepository,
                                   AzureStorageService azureStorageService) {
        this.monitor = monitor;
        this.participantRepository = participantRepository;
        this.reportRepository = reportRepository;
        this.telemetryEventRepository = telemetryEventRepository;
        this.azureStorageService = azureStorageService;
    }

    public static Queue<ReportGenerationError> getErrors() {
        return ERRORS;
    }

    void generateErrorReport(LocalDateTime targetDateTime, Queue<ReportGenerationError> errors) {
        int targetYear = targetDateTime.getYear();
        int targetMonth = targetDateTime.getMonthValue();
        List<ReportGenerationError> relevantErrors = errors.stream().filter(e -> e.generationTimespanTarget().getMonthValue() == targetMonth && e.generationTimespanTarget().getYear() == targetYear).toList();
        if (relevantErrors.isEmpty()) {
            return;
        }

        String reportContent = generateCsvErrorReportContent(relevantErrors);
        String fileName = "report_generation_errors_" + targetYear + "_" + targetMonth + ".csv";
        try {
            String path = getObjectPath(true, targetDateTime, fileName);
            azureStorageService.upload(path, reportContent.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            monitor.severe("Error uploading error report: " + e.getMessage(), e);
        } finally {
            relevantErrors.forEach(errors::remove);
        }
    }

    void generatePreviousMonthReportForAllParticipants() {
        List<ParticipantId> participants = participantRepository.findAll();
        LocalDateTime oneMonthBeforeDate = LocalDateTime.now().minusMonths(1);
        for (ParticipantId participant : participants) {
            generateReport(participant, oneMonthBeforeDate);
        }
    }

    public void validateParticipantAndGenerateReport(String participantName, LocalDateTime targetDateTime) {
        ParticipantId participant = participantRepository.findByParticipantName(participantName);
        if (participant == null) {
            this.monitor.severe("Participant not found: " + participantName);
            throw new RuntimeException("Participant not found: " + participantName);
        }

        generateReport(participant, targetDateTime);
    }

    /*
     * This implementation assumes that the report generation is triggered some days after the beginning of the next month,
     * so that all events for the previous month are already ingested. If this is not the case and telemetry events are still being stored while
     * the report generation is happening, we might need to start a transaction before doing all the reads and commit it only after saving all the reports,
     * to ensure that no new events are added in the meantime. As this is not the case for now, I only wrapped the report saving inside a transaction.
     * */
    public void generateReport(ParticipantId participant, LocalDateTime targetDateTime) {
        try {
            generateCsv(participant, targetDateTime);
        } catch (Exception e) {
            monitor.severe("Error generating report for participant " + participant.getName() + ": " + e.getMessage(), e);
            throw e;
        }
    }

    void generateCsv(ParticipantId participant, LocalDateTime targetDateTime) {
        monitor.info("Generating report for participant " + participant.getName());

        List<ContractStats> contractStats = telemetryEventRepository.findContractStatsForMonth(participant.getId(), targetDateTime.getMonthValue(), targetDateTime.getYear());

        validateProducerConsumerData(participant, contractStats, targetDateTime);

        List<TelemetryEvent> events = telemetryEventRepository.findByParticipantIdForMonth(participant.getId(), targetDateTime.getMonthValue(), targetDateTime.getYear());
        String csvContent = ReportUtil.generateCsvReportContent(contractStats);
        String fileName = ReportUtil.generateReportFileName(participant.getName());
        String path = getObjectPath(false, targetDateTime, fileName);
        String objectUrl = azureStorageService.upload(path, csvContent.getBytes(StandardCharsets.UTF_8));
        // We should implement a retry mechanism here FDPT-84156
        if (objectUrl != null) {
            // I save the report only at the end to avoid rollback if the upload failed
            Report report = new Report(fileName, objectUrl, participant);
            report.setTelemetryEvents(events);
            reportRepository.saveTransactional(report);
        }
    }

    Set<String> validateProducerConsumerData(ParticipantId participant, List<ContractStats> contractStats, LocalDateTime targetDateTime) {
        int month = targetDateTime.getMonthValue();
        int year = targetDateTime.getYear();
        Set<String> discrepancies = new HashSet<>();
        for (ContractStats participantContractStat : contractStats) {
            List<ParticipantId> contractParties = telemetryEventRepository.findContractParties(participantContractStat.contractId());
            if (contractParties.size() == 2) {
                ParticipantId partyId = contractParties.get(0);
                ParticipantId counterPartyId = contractParties.get(1);
                String counterpartyId = participant.getId().equals(partyId.getId()) ? counterPartyId.getId() : partyId.getId();
                ContractStats counterpartyStat = telemetryEventRepository.findContractStatsForContractIdForMonth(counterpartyId, month, year, participantContractStat.contractId());
                boolean msgSizeMatches = Objects.equals(counterpartyStat.msgSize(), participantContractStat.msgSize());
                boolean eventCountMatches = Objects.equals(counterpartyStat.eventCount(), participantContractStat.eventCount());
                if (!(msgSizeMatches && eventCountMatches)) {
                    String errorMessage = ReportUtil.generateErrorMessage(2, msgSizeMatches, eventCountMatches);
                    String counterpartyName = participant.getId().equals(partyId.getId()) ? counterPartyId.getName() : partyId.getName();
                    monitor.severe("Discrepancy found for contract " + participantContractStat.contractId() + " between participant " + participant.getName() + " and counterparty " + counterpartyName + " : " + errorMessage);
                    ERRORS.add(new ReportGenerationError(targetDateTime, participantContractStat.contractId(), participant.getId(), counterpartyId, participantContractStat.msgSize(), counterpartyStat.msgSize(), participantContractStat.eventCount(),
                            counterpartyStat.eventCount(), errorMessage));
                    discrepancies.add(participantContractStat.contractId());
                }
            } else {
                monitor.warning("Contract " + participantContractStat.contractId() + " does not have exactly 2 parties, found: " + contractParties.size());
                String errorMessage = ReportUtil.generateErrorMessage(contractParties.size(), false, false);
                ERRORS.add(new ReportGenerationError(targetDateTime, participantContractStat.contractId(), participant.getId(), "N/A", participantContractStat.msgSize(), null, participantContractStat.eventCount(), null, errorMessage));
                discrepancies.add(participantContractStat.contractId());
            }
        }
        return discrepancies;
    }


}
