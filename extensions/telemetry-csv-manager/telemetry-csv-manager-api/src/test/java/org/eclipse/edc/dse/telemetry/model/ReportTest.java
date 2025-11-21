package org.eclipse.edc.dse.telemetry.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportTest {

    private ParticipantId participant;

    @BeforeEach
    void setUp() {
        participant = Mockito.mock(ParticipantId.class);
    }

    @Test
    @DisplayName("Should create Report when valid arguments are provided")
    void shouldCreateReport_WhenValidArgumentsProvided() {
        Report report = new Report("report.csv", "http://link.to/report.csv", participant);

        assertEquals("report.csv", report.getCsvName());
        assertEquals("http://link.to/report.csv", report.getCsvLink());
        assertEquals(participant, report.getParticipant());
        assertNotNull(report.getTimestamp());
    }

    @Test
    @DisplayName("Should throw Exception when CsvName is blank")
    void shouldThrowException_WhenCsvNameIsBlank() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new Report(" ", "http://link", participant));
        assertEquals("csvName and csvLink cannot be null or empty.", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw Exception when CsvLink is blank")
    void shouldThrowException_WhenCsvLinkIsBlank() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new Report("report.csv", "", participant));
        assertEquals("csvName and csvLink cannot be null or empty.", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw Exception when Participant is null")
    void shouldThrowException_WhenParticipantIsNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new Report("report.csv", "http://link", null));
        assertEquals("participant cannot be null.", exception.getMessage());
    }

    @Test
    @DisplayName("Should set and get fields when using setters")
    void shouldSetAndGetFields_WhenUsingSetters() {
        Report report = new Report();
        LocalDateTime now = LocalDateTime.now();
        List<TelemetryEvent> events = List.of(Mockito.mock(TelemetryEvent.class));

        report.setId(42);
        report.setCsvName("data.csv");
        report.setCsvLink("http://link.to/data.csv");
        report.setParticipant(participant);
        report.setTimestamp(now);
        report.setTelemetryEvents(events);

        assertEquals(42, report.getId());
        assertEquals("data.csv", report.getCsvName());
        assertEquals("http://link.to/data.csv", report.getCsvLink());
        assertEquals(participant, report.getParticipant());
        assertEquals(now, report.getTimestamp());
        assertEquals(events, report.getTelemetryEvents());
    }

    @Test
    @DisplayName("Should be equal when fields match")
    void shouldBeEqual_WhenFieldsMatch() {
        LocalDateTime timestamp = LocalDateTime.now();
        List<TelemetryEvent> events = List.of(Mockito.mock(TelemetryEvent.class));

        Report r1 = new Report();
        r1.setId(1);
        r1.setCsvName("file.csv");
        r1.setCsvLink("http://link");
        r1.setParticipant(participant);
        r1.setTimestamp(timestamp);
        r1.setTelemetryEvents(events);

        Report r2 = new Report();
        r2.setId(1);
        r2.setCsvName("file.csv");
        r2.setCsvLink("http://link");
        r2.setParticipant(participant);
        r2.setTimestamp(timestamp);
        r2.setTelemetryEvents(events);

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal when compared with null or different class")
    void shouldNotBeEqual_WhenComparedWithNullOrDifferentClass() {
        Report report = new Report();
        assertNotEquals(null, report);
        assertNotEquals("not a report", report);
    }

    @Test
    @DisplayName("Should not be equal when fields differ")
    void shouldNotBeEqual_WhenFieldsDiffer() {
        Report r1 = new Report();
        r1.setId(1);

        Report r2 = new Report();
        r2.setId(2);

        assertNotEquals(r1, r2);
    }
}