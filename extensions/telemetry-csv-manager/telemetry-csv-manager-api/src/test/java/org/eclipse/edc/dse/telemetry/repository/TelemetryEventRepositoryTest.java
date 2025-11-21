package org.eclipse.edc.dse.telemetry.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.eclipse.edc.dse.telemetry.model.ParticipantId;
import org.eclipse.edc.dse.telemetry.model.TelemetryEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.eclipse.edc.dse.telemetry.TestUtils.TEST_PERSISTENCE_UNIT;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TelemetryEventRepositoryTest {

    private static EntityManager em;
    private static EntityManagerFactory emf;

    private TelemetryEventRepository telemetryEventRepository;
    private ParticipantRepository participantRepository;

    private ParticipantId consumer;
    private ParticipantId provider;

    @BeforeAll
    void setup() {
        emf = Persistence.createEntityManagerFactory(TEST_PERSISTENCE_UNIT);
        em = emf.createEntityManager();

        telemetryEventRepository = new TelemetryEventRepository(em);
        participantRepository = new ParticipantRepository(em);

        consumer = new ParticipantId("p1", "test@example.com", "consumer");
        participantRepository.saveTransactional(consumer);
        provider = new ParticipantId("p2", "test2@example.com", "provider");
        participantRepository.saveTransactional(provider);
    }

    private TelemetryEvent createTelemetryEvent(String id, String contractId, ParticipantId participantId, int statusCode, int msgSize, LocalDateTime timestamp) {
        TelemetryEvent event = new TelemetryEvent();
        event.setId(id);
        event.setContractId(contractId);
        event.setParticipant(participantId);
        event.setResponseStatusCode(statusCode);
        event.setMsgSize(msgSize);
        event.setTimestamp(timestamp);
        return event;
    }

    @AfterEach
    void tearDown() {
        telemetryEventRepository.findAll().forEach(telemetryEventRepository::deleteTransactional);
    }

    @Test
    @DisplayName("Retrieval of all telemetry should return all events")
    void shouldReturnAllEvents_WhenFindAll() {
        TelemetryEvent event1 = createTelemetryEvent("e1", "contract-1", consumer, 500,
                254, LocalDateTime.of(2025, 11, 15, 12, 0));

        TelemetryEvent event2 = createTelemetryEvent("e2", "contract-1", provider, 500,
                254, LocalDateTime.of(2025, 11, 15, 12, 0));

        telemetryEventRepository.saveTransactional(event1);
        telemetryEventRepository.saveTransactional(event2);

        List<TelemetryEvent> telemetryEventList = telemetryEventRepository.findAll();
        assertThat(telemetryEventList.size()).isEqualTo(2);
        assertThat(telemetryEventList.get(0)).isEqualTo(event1);
        assertThat(telemetryEventList.get(1)).isEqualTo(event2);
    }

    @Test
    @DisplayName("Should return both contract parties for telemetry event that has two parties")
    void shouldReturnBothContractParties_WhenEventHasTwoParties() {
        TelemetryEvent event1 = createTelemetryEvent("e1", "contract-1", consumer, 500,
                254, LocalDateTime.of(2025, 11, 15, 12, 0));

        TelemetryEvent event2 = createTelemetryEvent("e2", "contract-1", provider, 500,
                254, LocalDateTime.of(2025, 11, 15, 12, 0));

        telemetryEventRepository.saveTransactional(event1);
        telemetryEventRepository.saveTransactional(event2);

        List<ParticipantId> contractParties = telemetryEventRepository.findContractParties("contract-1");

        assertThat(contractParties.size()).isEqualTo(2);
        assertThat(contractParties.get(0)).isNotEqualTo(contractParties.get(1));
    }

    @Test
    @DisplayName("Should return one contract parties for telemetry event that has one party")
    void shouldReturnBothContractParties_WhenEventHasOneParty() {
        TelemetryEvent event1 = createTelemetryEvent("e1", "contract-1", consumer, 500,
                254, LocalDateTime.of(2025, 11, 15, 12, 0));

        telemetryEventRepository.saveTransactional(event1);

        List<ParticipantId> contractParties = telemetryEventRepository.findContractParties("contract-1");

        assertThat(contractParties.size()).isEqualTo(1);
        assertThat(contractParties.get(0)).isEqualTo(consumer);
    }

    @Test
    @DisplayName("Should return correct events when filtering by month and year")
    void shouldReturnCorrectEvents_WhenFilteringByMonthAndYear() {
        TelemetryEvent targetEvent = createTelemetryEvent("e1", "contract-1", consumer, 500,
                254, LocalDateTime.of(2025, 11, 15, 12, 0));

        TelemetryEvent eventWithSameMonthButDifferentYear = createTelemetryEvent("e2", "contract-1", consumer, 500,
                254, LocalDateTime.of(2024, 11, 15, 12, 0));

        TelemetryEvent eventWithSameYearAndMonthButDifferentParticipant = createTelemetryEvent("e3", "contract-1", provider, 500,
                254, LocalDateTime.of(2025, 11, 15, 12, 0));

        TelemetryEvent eventWithSameYearButDifferentMonth = createTelemetryEvent("e4", "contract-1", consumer, 500,
                254, LocalDateTime.of(2025, 12, 15, 12, 0));

        telemetryEventRepository.saveTransactional(targetEvent);
        telemetryEventRepository.saveTransactional(eventWithSameMonthButDifferentYear);
        telemetryEventRepository.saveTransactional(eventWithSameYearAndMonthButDifferentParticipant);
        telemetryEventRepository.saveTransactional(eventWithSameYearButDifferentMonth);

        List<TelemetryEvent> contractParties = telemetryEventRepository.findByParticipantIdForMonth(consumer.getId(), 11, 2025);

        assertThat(contractParties.size()).isEqualTo(1);
        assertThat(contractParties.get(0)).isEqualTo(targetEvent);
    }

    @Test
    @DisplayName("Should return empty when filtering by month and year and no match exists")
    void shouldReturnEmpty_WhenFilteringByMonthAndYearAndNoMatchExists() {
        TelemetryEvent e1 = createTelemetryEvent("e1", "contract-1", consumer, 500,
                254, LocalDateTime.of(2024, 11, 15, 12, 0));

        TelemetryEvent e2 = createTelemetryEvent("e2", "contract-1", provider, 500,
                254, LocalDateTime.of(2025, 11, 15, 12, 0));

        TelemetryEvent e3 = createTelemetryEvent("e3", "contract-1", consumer, 500,
                254, LocalDateTime.of(2025, 12, 15, 12, 0));

        telemetryEventRepository.saveTransactional(e1);
        telemetryEventRepository.saveTransactional(e2);
        telemetryEventRepository.saveTransactional(e3);

        List<TelemetryEvent> contractParties = telemetryEventRepository.findByParticipantIdForMonth(consumer.getId(), 11, 2026);

        assertThat(contractParties.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Retrieval of all telemetry should return empty list when no events exist")
    void shouldReturnEmptyList_WhenNoEventsExist() {
        List<TelemetryEvent> telemetryEventList = telemetryEventRepository.findAll();
        assertThat(telemetryEventList.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Retrieval of contract stats should find status grouped by contract id and status code when exists")
    void shouldFindStatusGroupedByContractIdAndStatusCode_WhenExists() {
        TelemetryEvent event1 = createTelemetryEvent("e1", "contract-1", consumer, 200,
                254, LocalDateTime.of(2025, 11, 15, 12, 0));
        TelemetryEvent event2 = createTelemetryEvent("e2", "contract-1", provider, 500,
                300, LocalDateTime.of(2025, 11, 15, 12, 0));
        TelemetryEvent event3 = createTelemetryEvent("e3", "contract-2", consumer, 200,
                150, LocalDateTime.of(2025, 11, 19, 12, 0));

        telemetryEventRepository.saveTransactional(event1);
        telemetryEventRepository.saveTransactional(event2);
        telemetryEventRepository.saveTransactional(event3);

        List<ContractStats> result = telemetryEventRepository.findStatsGroupedByContractIdAndStatusCode(consumer.getId(), 11, 2025);

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Retrieval of contract stats should return empty list when no events exist")
    void shouldReturnEmptyStats_WhenNoEventsExist() {
        List<ContractStats> result = telemetryEventRepository.findStatsGroupedByContractIdAndStatusCode(consumer.getId(), 11, 2025);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Retrieval of contract stats should return stats grouped by contract id when exists")
    void shouldReturnStatsGroupedByContractId_WhenExists() {
        TelemetryEvent event1 = createTelemetryEvent("e1", "contract-1", consumer, 200,
                254, LocalDateTime.of(2025, 11, 15, 12, 0));
        TelemetryEvent event2 = createTelemetryEvent("e2", "contract-1", provider, 500,
                300, LocalDateTime.of(2025, 11, 15, 12, 0));
        TelemetryEvent event3 = createTelemetryEvent("e3", "contract-2", consumer, 200,
                150, LocalDateTime.of(2025, 11, 19, 12, 0));
        TelemetryEvent event4 = createTelemetryEvent("e4", "contract-1", consumer, 200,
                150, LocalDateTime.of(2025, 10, 19, 12, 0));
        TelemetryEvent event5 = createTelemetryEvent("e5", "contract-1", consumer, 200,
                150, LocalDateTime.of(2025, 12, 19, 12, 0));

        telemetryEventRepository.saveTransactional(event1);
        telemetryEventRepository.saveTransactional(event2);
        telemetryEventRepository.saveTransactional(event3);
        telemetryEventRepository.saveTransactional(event4);
        telemetryEventRepository.saveTransactional(event5);

        ContractStats result = telemetryEventRepository.findStatsForContractIdGroupedByContractId(consumer.getId(), 11, 2025, "contract-1");

        assertThat(result).isNotNull();
        assertThat(result.contractId()).isEqualTo("contract-1");
        assertThat(result.msgSize()).isEqualTo(254);
        assertThat(result.eventCount()).isEqualTo(1);
        assertThat(result.responseStatus()).isEqualTo(0);
    }


}