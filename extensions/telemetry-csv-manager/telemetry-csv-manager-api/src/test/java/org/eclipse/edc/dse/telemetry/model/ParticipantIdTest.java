package org.eclipse.edc.dse.telemetry.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ParticipantIdTest {

    private ParticipantId participant;

    @BeforeEach
    void setUp() {
        participant = new ParticipantId("did:example:123", "user@example.com", "Alice");
    }

    @Test
    @DisplayName("Should create Participant when valid arguments are provided")
    void shouldCreateParticipant_WhenValidArgumentsProvided() {
        assertEquals("did:example:123", participant.getId());
        assertEquals("user@example.com", participant.getEmail());
        assertEquals("Alice", participant.getName());
        assertNotNull(participant.getTimestamp());
    }

    @Test
    @DisplayName("Should throw Exception when Id is blank")
    void shouldThrowException_WhenIdIsBlank() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ParticipantId(" ", "user@example.com", "Alice"));
        assertEquals("id cannot be null or blank", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw Exception when Email is blank")
    void shouldThrowException_WhenEmailIsBlank() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ParticipantId("did:example:123", "", "Alice"));
        assertEquals("email cannot be null or blank", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw Exception when Name is blank")
    void shouldThrowException_WhenNameIsBlank() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ParticipantId("did:example:123", "user@example.com", " "));
        assertEquals("participantName cannot be null or blank", exception.getMessage());
    }

    @Test
    @DisplayName("Should set and get fields when using setters")
    void shouldSetAndGetFields_WhenUsingSetters() {
        ParticipantId p = new ParticipantId();
        LocalDateTime now = LocalDateTime.now();
        List<Report> reports = List.of(mock(Report.class));
        List<TelemetryEvent> events = List.of(mock(TelemetryEvent.class));

        p.setId("did:example:456");
        p.setEmail("new@example.com");
        p.setName("Bob");
        p.setTimestamp(now);
        p.setReports(reports);
        p.setTelemetryEvents(events);

        assertEquals("did:example:456", p.getId());
        assertEquals("new@example.com", p.getEmail());
        assertEquals("Bob", p.getName());
        assertEquals(now, p.getTimestamp());
        assertEquals(reports, p.getReports());
        assertEquals(events, p.getTelemetryEvents());
    }

    @Test
    @DisplayName("Should be equal when fields match")
    void shouldBeEqual_WhenFieldsMatch() {
        LocalDateTime timestamp = LocalDateTime.now();
        List<Report> reports = List.of(mock(Report.class));
        List<TelemetryEvent> events = List.of(mock(TelemetryEvent.class));

        ParticipantId p1 = new ParticipantId();
        p1.setId("did:abc");
        p1.setEmail("a@b.com");
        p1.setName("Name");
        p1.setTimestamp(timestamp);
        p1.setReports(reports);
        p1.setTelemetryEvents(events);

        ParticipantId p2 = new ParticipantId();
        p2.setId("did:abc");
        p2.setEmail("a@b.com");
        p2.setName("Name");
        p2.setTimestamp(timestamp);
        p2.setReports(reports);
        p2.setTelemetryEvents(events);

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal when compared with null or different class")
    void shouldNotBeEqual_WhenComparedWithNullOrDifferentClass() {
        assertNotEquals(null, participant);
        assertNotEquals("not a participant", participant);
    }

    @Test
    @DisplayName("Should not be equal when fields differ")
    void shouldNotBeEqual_WhenFieldsDiffer() {
        ParticipantId p1 = new ParticipantId();
        p1.setId("did:1");

        ParticipantId p2 = new ParticipantId();
        p2.setId("did:2");

        assertNotEquals(p1, p2);
    }
}