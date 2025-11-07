package org.eclipse.edc.eonax.telemetry.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.List;


@Entity
@Table(name = "participant_id")
public class ParticipantId {

    @Id
    @Column(nullable = false, length = 255)
    private String id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column
    private String name;

    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Report> reports;

    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TelemetryEvent> telemetryEvents;

    public ParticipantId() {
    }

    public ParticipantId(String id, String email, String name) {
        if (id == null || StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("id cannot be null or blank");
        }

        if (email == null || StringUtils.isBlank(email)) {
            throw new IllegalArgumentException("email cannot be null or blank");
        }

        if (name == null || StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("participantName cannot be null or blank");
        }

        this.id = id;
        this.email = email;
        this.timestamp = LocalDateTime.now();
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public List<Report> getReports() {
        return reports;
    }

    public void setReports(List<Report> reports) {
        this.reports = reports;
    }

    public List<TelemetryEvent> getTelemetryEvents() {
        return telemetryEvents;
    }

    public void setTelemetryEvents(List<TelemetryEvent> telemetryEvents) {
        this.telemetryEvents = telemetryEvents;
    }

    public String getName() {
        return name;
    }

    public void setName(String participantName) {
        this.name = participantName;
    }
}
