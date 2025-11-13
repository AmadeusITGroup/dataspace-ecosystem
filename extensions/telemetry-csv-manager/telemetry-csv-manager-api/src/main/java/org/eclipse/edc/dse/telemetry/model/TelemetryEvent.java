package org.eclipse.edc.dse.telemetry.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "telemetry_event",
        uniqueConstraints = @UniqueConstraint(
                name = "unique_contract_participant_timestamp",
                columnNames = {"contract_id", "participant_did", "timestamp"}
        )
)
public class TelemetryEvent {

    @Id
    @Column(nullable = false, length = 255)
    private String id;

    @Column(name = "contract_id", nullable = false)
    private String contractId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "participant_did", referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "telemetry_event_participant_did_fk"))
    private ParticipantId participant;

    @Column(name = "response_status_code", nullable = false)
    private int responseStatusCode;

    @Column(name = "msg_size", nullable = false)
    private int msgSize;

    @ManyToOne
    @JoinColumn(name = "csv_id", referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "telemetry_event_csv_id_fk"))
    private Report csvReport;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public TelemetryEvent() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContractId() {
        return contractId;
    }

    public void setContractId(String contractId) {
        this.contractId = contractId;
    }

    public ParticipantId getParticipant() {
        return participant;
    }

    public void setParticipant(ParticipantId participant) {
        this.participant = participant;
    }

    public int getResponseStatusCode() {
        return responseStatusCode;
    }

    public void setResponseStatusCode(int responseStatusCode) {
        this.responseStatusCode = responseStatusCode;
    }

    public int getMsgSize() {
        return msgSize;
    }

    public void setMsgSize(int msgSize) {
        this.msgSize = msgSize;
    }

    public Report getCsvReport() {
        return csvReport;
    }

    public void setCsvReport(Report csvReport) {
        this.csvReport = csvReport;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
