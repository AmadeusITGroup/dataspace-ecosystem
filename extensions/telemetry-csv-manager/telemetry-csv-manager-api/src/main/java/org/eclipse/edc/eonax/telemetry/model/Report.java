package org.eclipse.edc.eonax.telemetry.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "report")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "csv_name", nullable = false)
    private String csvName;

    @Column(name = "csv_link", nullable = false)
    private String csvLink;

    @ManyToOne(optional = false)
    @JoinColumn(name = "participant_did", referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "report_participant_did_fk"))
    private ParticipantId participant;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @OneToMany(mappedBy = "csvReport")
    private List<TelemetryEvent> telemetryEvents;

    public Report() {
    }

    public Report(String csvName, String csvLink, ParticipantId participant) {
        if (StringUtils.isBlank(csvName) || StringUtils.isBlank(csvLink)) {
            throw new IllegalArgumentException("csvName and csvLink cannot be null or empty.");
        }

        if (participant == null) {
            throw new IllegalArgumentException("participant cannot be null.");
        }

        this.csvName = csvName;
        this.csvLink = csvLink;
        this.participant = participant;
        this.timestamp = LocalDateTime.now();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCsvName() {
        return csvName;
    }

    public void setCsvName(String csvName) {
        this.csvName = csvName;
    }

    public String getCsvLink() {
        return csvLink;
    }

    public void setCsvLink(String csvLink) {
        this.csvLink = csvLink;
    }

    public ParticipantId getParticipant() {
        return participant;
    }

    public void setParticipant(ParticipantId participant) {
        this.participant = participant;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public List<TelemetryEvent> getTelemetryEvents() {
        return telemetryEvents;
    }

    public void setTelemetryEvents(List<TelemetryEvent> telemetryEvents) {
        this.telemetryEvents = telemetryEvents;
    }
}
