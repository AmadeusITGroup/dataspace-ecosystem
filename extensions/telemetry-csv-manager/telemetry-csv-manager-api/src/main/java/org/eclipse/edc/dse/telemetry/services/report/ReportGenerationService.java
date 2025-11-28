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
import java.util.ArrayList;
import java.util.List;

import static org.eclipse.edc.dse.telemetry.services.ReportUtil.getObjectPath;
import static org.eclipse.edc.dse.telemetry.services.ReportUtil.getValue;

public class ReportGenerationService {

    private final ParticipantRepository participantRepository;
    private final ReportRepository reportRepository;
    private final TelemetryEventRepository telemetryEventRepository;
    private final AzureStorageService azureStorageService;
    private final Monitor monitor;

    // Error report generation was disabled for now since the report already contains the same information
    //
    // I'm using thread safe data structure here because there can be concurrent accesses to this list.
    // Current edge case: We generate a report on the demand at the same time the cron task is generating the monthly report on the background for the same or a different month
    // Future cases: If we allow the generation of reports via UI in the future this edge case will be a common use case with concurrent accesses of the users
    //
    // Warning: There is a design flaw here, if we trigger an on-demand generation and the monthly generation task is running with the exact same month and year and both of them fail the validation,
    // only one error report will be generated with errors from both. However, the frequency of this use case is almost inexistent, and our main goal is to have generation in-place, validation is secondary.
    // To tackle this issue and having a kubernetes cronjob, the reports will be tackled in another US -> FDPT-84292
    // static final Queue<ReportGenerationError> ERRORS = new ConcurrentLinkedQueue<>();

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

    //    public static Queue<ReportGenerationError> getErrors() {
    //        return ERRORS;
    //    }

    //    void generateErrorReport(LocalDateTime targetDateTime, Queue<ReportGenerationError> errors) {
    //        this.monitor.info("Generating error report for " + targetDateTime);
    //        int targetYear = targetDateTime.getYear();
    //        int targetMonth = targetDateTime.getMonthValue();
    //        List<ReportGenerationError> relevantErrors = errors.stream().filter(e -> e.generationTimespanTarget().getMonthValue() == targetMonth && e.generationTimespanTarget().getYear() == targetYear).toList();
    //        if (relevantErrors.isEmpty()) {
    //            return;
    //        }
    //
    //        String reportContent = generateCsvErrorReportContent(relevantErrors);
    //        String fileName = "report_generation_errors_" + targetYear + "_" + targetMonth + ".csv";
    //        try {
    //            String path = getObjectPath(true, targetDateTime, fileName);
    //            this.monitor.info("Uploading error report...");
    //            azureStorageService.upload(path, reportContent.getBytes(StandardCharsets.UTF_8));
    //            this.monitor.info("Error report uploaded");
    //        } catch (Exception e) {
    //            monitor.severe("Error uploading error report: " + e.getMessage(), e);
    //        } finally {
    //            relevantErrors.forEach(errors::remove);
    //        }
    //    }

    void generatePreviousMonthReportForAllParticipants() {
        List<ParticipantId> participants = participantRepository.findAll();
        LocalDateTime oneMonthBeforeDate = LocalDateTime.now().minusMonths(1);
        for (ParticipantId participant : participants) {
            // By default the cron task should generate the simplified report and the extended report with counterparty info
            generateReport(participant, oneMonthBeforeDate, false);
            generateReport(participant, oneMonthBeforeDate, true);
        }
    }

    public void generateParticipantReport(String participantName, LocalDateTime targetDateTime, boolean generateCounterpartyReport) {
        ParticipantId participant = findParticipant(participantName);
        if (participant == null) {
            this.monitor.severe("Participant not found: " + participantName);
            throw new RuntimeException("Participant not found: " + participantName);
        }

        generateReport(participant, targetDateTime, false);
        if (generateCounterpartyReport) {
            generateReport(participant, targetDateTime, true);
        }
    }

    public ParticipantId findParticipant(String participantName) {
        return participantRepository.findByParticipantName(participantName);
    }

    /*
     * This implementation assumes that the report generation is triggered some days after the beginning of the next month,
     * so that all events for the previous month are already ingested. If this is not the case and telemetry events are still being stored while
     * the report generation is happening, we might need to start a transaction before doing all the reads and commit it only after saving all the reports,
     * to ensure that no new events are added in the meantime. As this is not the case for now, I only wrapped the report saving inside a transaction.
     * */
    public void generateReport(ParticipantId participant, LocalDateTime targetDateTime, boolean includeCounterpartyInfo) {
        try {
            this.monitor.debug("Generating report for participant " + participant.getName());
            generateCsv(participant, targetDateTime, includeCounterpartyInfo);
            this.monitor.debug("Report generated");
        } catch (Exception e) {
            monitor.severe("Error generating report for participant " + participant.getName() + ": " + e.getMessage(), e);
            throw e;
        }
    }

    void generateCsv(ParticipantId participant, LocalDateTime targetDateTime, boolean includeCounterpartyInfo) {
        monitor.info("Generating csv for participant " + participant.getName());
        int month = targetDateTime.getMonthValue();
        int year = targetDateTime.getYear();

        List<ContractStats> contractStats = telemetryEventRepository.findStatsGroupedByContractIdAndStatusCode(participant.getId(), month, year);
        List<String> csvLines = collectCsvEntryInfo(participant, contractStats, month, year, includeCounterpartyInfo);

        List<TelemetryEvent> events = telemetryEventRepository.findByParticipantIdForMonth(participant.getId(), targetDateTime.getMonthValue(), targetDateTime.getYear());
        String csvContent = ReportUtil.generateCsvReportContent(csvLines, includeCounterpartyInfo);
        String fileName = ReportUtil.generateReportFileName(participant.getName(), targetDateTime, includeCounterpartyInfo);
        String path = getObjectPath(targetDateTime, fileName, includeCounterpartyInfo);
        monitor.debug("Uploading report to path " + path);
        String objectUrl = azureStorageService.upload(path, csvContent.getBytes(StandardCharsets.UTF_8));
        monitor.debug("Report uploaded to " + objectUrl);
        // We should implement a retry mechanism here FDPT-84156
        if (objectUrl != null) {
            // I save the report only at the end to avoid rollback if the upload failed
            Report report = new Report(fileName, objectUrl, participant);
            report.setTelemetryEvents(events);
            reportRepository.saveTransactional(report);
        }
    }

    private List<String> collectCsvEntryInfo(ParticipantId participant, List<ContractStats> contractStats, int month, int year, boolean includeCounterpartyInfo) {
        List<String> csvLines;
        if (!includeCounterpartyInfo) {
            this.monitor.debug("Building report for participant " + participant.getName() + " without counterparty info");
            csvLines = buildCsvWithoutCounterpartyInfo(contractStats);
        } else {
            this.monitor.debug("Building report for participant " + participant.getName() + " with counterparty info");
            csvLines = buildCsvWithCounterpartyInfo(participant, contractStats, month, year);
        }
        return csvLines;
    }

    private List<String> buildCsvWithCounterpartyInfo(ParticipantId participant, List<ContractStats> contractStats, int month, int year) {
        List<String> csvLines = new ArrayList<>();
        ContractStats counterPartyContractStats;
        for (ContractStats contractStat : contractStats) {
            String contractId = contractStat.contractId();
            List<ParticipantId> contractParties = telemetryEventRepository.findContractParties(contractId);
            String counterpartyId;
            String counterpartyName = "N/A";
            if (contractParties.size() == 2) {
                counterpartyId = participant.getId().equals(contractParties.get(0).getId()) ? contractParties.get(1).getId() : contractParties.get(0).getId();
                counterpartyName = participant.getId().equals(contractParties.get(0).getId()) ? contractParties.get(1).getName() : contractParties.get(0).getName();
                counterPartyContractStats = telemetryEventRepository.findStatsForContractIdAndStatusCodeGroupedByContractIdAndStatusCode(counterpartyId, month, year, contractId, contractStat.responseStatus());
                if (counterPartyContractStats == null) {
                    // Covers edge case in which no data is found on the counterparty side
                    monitor.warning("No data found for counterparty " + counterpartyId + " contract " + contractId + " month " + month + " year " + year);
                    counterPartyContractStats = new ContractStats(contractId, 0, null, null);
                }
            } else {
                monitor.warning("Contract " + contractId + " does not have exactly 2 parties, found parties: " + contractParties.size());
                counterPartyContractStats = new ContractStats(contractId, 0, null, null);
            }

            csvLines.add(buildCsvEntryRowWithCounterpartyInfo(contractStat, participant.getName(), counterpartyName, counterPartyContractStats));
        }
        return csvLines;
    }

    private static List<String> buildCsvWithoutCounterpartyInfo(List<ContractStats> contractStats) {
        List<String> csvLines = new ArrayList<>();
        for (ContractStats contractStat : contractStats) {
            csvLines.add(buildCsvEntryRowWithoutCounterpartyInfo(contractStat));
        }
        return csvLines;
    }

    private static String buildCsvEntryRowWithCounterpartyInfo(ContractStats contractStat, String participantName, String counterpartyName, ContractStats counterPartyContractStats) {
        StringBuilder builder = new StringBuilder();
        builder.append(getValue(contractStat.contractId())).append(",");
        builder.append(getValue(contractStat.responseStatus())).append(",");
        builder.append(getValue(participantName)).append(",");
        builder.append(getValue(counterpartyName)).append(",");
        builder.append(getMsgSizeValue(contractStat)).append(",");
        builder.append(getMsgSizeValue(counterPartyContractStats)).append(",");
        builder.append(contractStat.eventCount() == null ? 0 : contractStat.eventCount()).append(",");
        builder.append(counterPartyContractStats.eventCount() == null ? 0 : counterPartyContractStats.eventCount());
        return builder.toString();
    }

    private static String buildCsvEntryRowWithoutCounterpartyInfo(ContractStats contractStat) {
        StringBuilder builder = new StringBuilder();
        builder.append(getValue(contractStat.contractId())).append(",");
        builder.append(getValue(contractStat.responseStatus())).append(",");
        builder.append(getMsgSizeValue(contractStat)).append(",");
        builder.append(contractStat.eventCount() == null ? 0 : contractStat.eventCount());
        return builder.toString();
    }

    private static String getMsgSizeValue(ContractStats contractStat) {
        return contractStat.msgSize() != null ? bytesToKilobytesRoundedString(contractStat.msgSize()) : "0";
    }

    /**
     * Converts bytes to kilobytes and returns a string rounded to 2 decimal places.
     *
     * @param bytes the number of bytes to convert
     * @return a string representation of kilobytes rounded to 2 decimal places
     */
    public static String bytesToKilobytesRoundedString(long bytes) {
        double kb = bytes / 1024.0;
        return String.format("%.2f", kb);
    }


    // The validation and error generation was disabled for now since the report already contains the same information
    //
    //    Set<String> validateProducerConsumerData(ParticipantId participant, List<ContractStats> contractStats, LocalDateTime targetDateTime) {
    //        int month = targetDateTime.getMonthValue();
    //        int year = targetDateTime.getYear();
    //        Set<String> discrepancies = new HashSet<>();
    //        for (ContractStats participantContractStat : contractStats) {
    //            String contractId = participantContractStat.contractId();
    //            List<ParticipantId> contractParties = telemetryEventRepository.findContractParties(contractId);
    //            if (contractParties.size() == 2) {
    //                ParticipantId partyId = contractParties.get(0);
    //                ParticipantId counterPartyId = contractParties.get(1);
    //                String counterpartyId = participant.getId().equals(partyId.getId()) ? counterPartyId.getId() : partyId.getId();
    //                ContractStats counterpartyStat = telemetryEventRepository.findContractStatsForContractIdForMonthByContractIdAndStatusCode(counterpartyId, month, year, contractId);
    //                if (counterpartyStat == null) {
    //                    // Covers edge case in which no data is found on the counterparty side
    //                    monitor.warning("No data found for counterparty " + counterpartyId + " contract " + contractId + " month " + month + " year " + year);
    //                    ERRORS.add(new ReportGenerationError(targetDateTime, contractId, participant.getId(), "N/A", participantContractStat.msgSize(), null, participantContractStat.eventCount(),
    //                            null, "No data found for counterparty " + counterpartyId + " contract " + counterpartyId));
    //                    discrepancies.add(contractId);
    //                } else {
    //                    boolean msgSizeMatches = Objects.equals(counterpartyStat.msgSize(), participantContractStat.msgSize());
    //                    boolean eventCountMatches = Objects.equals(counterpartyStat.eventCount(), participantContractStat.eventCount());
    //                    if (!(msgSizeMatches && eventCountMatches)) {
    //                        String errorMessage = ReportUtil.generateErrorMessage(2, msgSizeMatches, eventCountMatches);
    //                        String counterpartyName = participant.getId().equals(partyId.getId()) ? counterPartyId.getName() : partyId.getName();
    //                        monitor.warning("Discrepancy found for contract " + contractId + " between participant " + participant.getName() + " and counterparty " + counterpartyName + " : " + errorMessage);
    //                        ERRORS.add(new ReportGenerationError(targetDateTime, contractId, participant.getId(), counterpartyId, participantContractStat.msgSize(), counterpartyStat.msgSize(), participantContractStat.eventCount(),
    //                                counterpartyStat.eventCount(), errorMessage));
    //                        discrepancies.add(contractId);
    //                    }
    //                }
    //            } else {
    //                monitor.warning("Contract " + contractId + " does not have exactly 2 parties, found: " + contractParties.size());
    //                String errorMessage = ReportUtil.generateErrorMessage(contractParties.size(), false, false);
    //                ERRORS.add(new ReportGenerationError(targetDateTime, contractId, participant.getId(), "N/A", participantContractStat.msgSize(), null, participantContractStat.eventCount(), null, errorMessage));
    //                discrepancies.add(contractId);
    //            }
    //        }
    //        return discrepancies;
    //    }


}
