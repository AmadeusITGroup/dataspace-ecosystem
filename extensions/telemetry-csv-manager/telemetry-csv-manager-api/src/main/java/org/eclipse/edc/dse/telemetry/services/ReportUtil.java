package org.eclipse.edc.dse.telemetry.services;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

public class ReportUtil {

    public static final String REPORT_HEADER = "contract_id,data_transfer_response_status,participant_id,counterparty_id," +
            "participant_total_transfer_size_in_kB,counterparty_total_transfer_size_in_kB,participant_total_number_of_events," +
            "counterparty_total_number_of_events";

    public static String generateReportFileName(String participantName, LocalDateTime targetDateTime) {
        int year = targetDateTime.getYear();
        int month = targetDateTime.getMonthValue();
        return "report-" + participantName + "-" + year + "-" + month + ".csv";
    }

    public static String getObjectPath(boolean isErrorReport, LocalDateTime dateTime, String fileName) {
        return "reports/" + dateTime.getYear() + "/" + dateTime.getMonthValue() + "/" + (isErrorReport ? "errors/" + fileName : fileName);
    }

    //    public static String generateCsvErrorReportContent(List<ReportGenerationError> errors) {
    //        ByteArrayOutputStream baos = new ByteArrayOutputStream();
    //        PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8));
    //        writer.println("contractId,participantId,counterpartyId,participantMsgSize,counterpartyMsgSize,participantEventCount,counterpartyEventCount,errorMessage");
    //        for (ReportGenerationError reportGenerationError : errors) {
    //            writer.println(getValue(reportGenerationError.contractId()) + "," + getValue(reportGenerationError.participantId()) + "," + getValue(reportGenerationError.counterpartyId()) + "," +
    //                    getValue(reportGenerationError.participantMsgSize()) + "," + getValue(reportGenerationError.counterpartyMsgSize()) + "," +
    //                    getValue(reportGenerationError.participantEventCount()) + "," + getValue(reportGenerationError.counterpartyEventCount()) + "," +
    //                    getValue(reportGenerationError.errorMessage()));
    //        }
    //        writer.flush();
    //        return baos.toString(StandardCharsets.UTF_8);
    //    }

    public static String generateCsvReportContent(List<String> csvLines) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8));
        writer.println(REPORT_HEADER);
        if (csvLines.isEmpty()) {
            writer.println("N/A,N/A,N/A,N/A,N/A,N/A,N/A,N/A");
        } else {
            csvLines.forEach(writer::println);
        }
        writer.flush();
        return baos.toString(StandardCharsets.UTF_8);
    }

    //    public static String generateErrorMessage(Integer partiesNumber, boolean msgSizeMatches, boolean eventCountMatches) {
    //        String errorMessage;
    //        if (partiesNumber != 2) {
    //            errorMessage = "Invalid number of parties: " + partiesNumber;
    //        } else if (!msgSizeMatches && eventCountMatches) {
    //            errorMessage = "Message size mismatch";
    //        } else if (msgSizeMatches) {
    //            errorMessage = "Event count mismatch";
    //        } else {
    //            errorMessage = "Both message size and event count mismatch";
    //        }
    //        return errorMessage;
    //    }

    public static String getValue(Integer value) {
        return value == null ? "0" : String.valueOf(value);
    }

    public static String getValue(Long value) {
        return value == null ? "0" : String.valueOf(value);
    }

    public static String getValue(String value) {
        return value == null ? "N/A" : value;
    }
}
