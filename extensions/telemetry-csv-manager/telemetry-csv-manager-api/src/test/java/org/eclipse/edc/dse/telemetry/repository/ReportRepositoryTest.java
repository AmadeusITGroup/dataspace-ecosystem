package org.eclipse.edc.dse.telemetry.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.eclipse.edc.dse.telemetry.model.ParticipantId;
import org.eclipse.edc.dse.telemetry.model.Report;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.eclipse.edc.dse.telemetry.TestUtils.CSV_LINK;
import static org.eclipse.edc.dse.telemetry.TestUtils.CSV_LINK_2;
import static org.eclipse.edc.dse.telemetry.TestUtils.CSV_NAME;
import static org.eclipse.edc.dse.telemetry.TestUtils.CSV_NAME_2;
import static org.eclipse.edc.dse.telemetry.TestUtils.P1_DID;
import static org.eclipse.edc.dse.telemetry.TestUtils.P2_DID;
import static org.eclipse.edc.dse.telemetry.TestUtils.PARTICIPANT_NAME;
import static org.eclipse.edc.dse.telemetry.TestUtils.TEST_PERSISTENCE_UNIT;
import static org.eclipse.edc.dse.telemetry.TestUtils.USER_EMAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ReportRepositoryTest {

    private ReportRepository reportRepository;
    private ParticipantRepository participantRepo;
    private static EntityManager em;
    private static EntityManagerFactory emf;


    @BeforeAll
    void setup() {
        emf = Persistence.createEntityManagerFactory(TEST_PERSISTENCE_UNIT);
        em = emf.createEntityManager();

        reportRepository = new ReportRepository(em);
        participantRepo = new ParticipantRepository(em);
    }

    @AfterEach
    void tearDown() {
        // Reports depend on participants so reports should be deleted first to break the dependencies
        em.getTransaction().begin();
        reportRepository.findAll().forEach(reportRepository::delete);
        participantRepo.findAll().forEach(participantRepo::delete);
        em.getTransaction().commit();
    }


    @Test
    @DisplayName("Should save and find Report when valid arguments are provided")
    void shouldSucceed_WhenSaveAndFindReport() {
        em.getTransaction().begin();
        ParticipantId participant = new ParticipantId(P1_DID, USER_EMAIL, PARTICIPANT_NAME);
        participantRepo.save(participant);

        Report report = new Report(CSV_NAME, CSV_LINK, participant);
        reportRepository.save(report);
        em.getTransaction().commit();

        Report reportFound = reportRepository.findAll().get(0);
        assertNotNull(reportFound);
        assertEquals(CSV_NAME, reportFound.getCsvName());
        assertEquals(CSV_LINK, reportFound.getCsvLink());

        ParticipantId reportParticipant = reportFound.getParticipant();
        assertNotNull(reportParticipant);
        assertEquals(USER_EMAIL, reportParticipant.getEmail());
        assertEquals(P1_DID, reportParticipant.getId());
        assertEquals(PARTICIPANT_NAME, reportParticipant.getName());
    }

    @Test
    @DisplayName("Should find all reports")
    void shouldFindAllReports_WhenFindAll() {
        participantRepo.saveTransactional(new ParticipantId(P2_DID, "u2@example.com", "p2"));
        reportRepository.saveTransactional(new Report(CSV_NAME, CSV_LINK, participantRepo.findAll().get(0)));
        reportRepository.saveTransactional(new Report(CSV_NAME_2, CSV_LINK_2, participantRepo.findAll().get(0)));

        List<Report> all = reportRepository.findAll();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("Should find report by participant ID")
    void shouldFindReport_WhenFindByParticipant() {
        participantRepo.saveTransactional(new ParticipantId(P2_DID, "u2@example.com", "p2"));
        participantRepo.saveTransactional(new ParticipantId(P1_DID, USER_EMAIL, PARTICIPANT_NAME));
        reportRepository.saveTransactional(new Report(CSV_NAME, CSV_LINK, participantRepo.findAll().get(0)));
        reportRepository.saveTransactional(new Report(CSV_NAME_2, CSV_LINK_2, participantRepo.find(P1_DID)));

        List<Report> reports = reportRepository.findByParticipantId(P1_DID);
        assertEquals(1, reports.size());
        Report foundReport = reports.get(0);
        assertEquals(USER_EMAIL, foundReport.getParticipant().getEmail());
        assertEquals(PARTICIPANT_NAME, foundReport.getParticipant().getName());
        assertEquals(CSV_NAME_2, foundReport.getCsvName());
        assertEquals(CSV_LINK_2, foundReport.getCsvLink());
    }

    @ParameterizedTest(name = "{index} => Report saving should fail when csvName={0}, csvLink={1}")
    @MethodSource("invalidParticipantArguments")
    void testSaveReportWithInvalidArgumentsFails(String csvName, String csvLink) {
        participantRepo.saveTransactional(new ParticipantId(P1_DID, USER_EMAIL, PARTICIPANT_NAME));
        assertThrows(IllegalArgumentException.class, () -> new Report(csvName, csvLink, participantRepo.find(PARTICIPANT_NAME)));
    }

    public Stream<Arguments> invalidParticipantArguments() {
        return Stream.of(Arguments.of(null, CSV_LINK, participantRepo.find(PARTICIPANT_NAME)),
                Arguments.of("", CSV_LINK, participantRepo.find(PARTICIPANT_NAME)),
                Arguments.of(" ", CSV_LINK, participantRepo.find(PARTICIPANT_NAME)),
                Arguments.of(CSV_NAME, null, participantRepo.find(PARTICIPANT_NAME)),
                Arguments.of(CSV_NAME, "", participantRepo.find(PARTICIPANT_NAME)),
                Arguments.of(CSV_NAME, " ", participantRepo.find(PARTICIPANT_NAME)),
                Arguments.of(CSV_NAME, CSV_LINK, null)
        );
    }

    @AfterAll
    static void teardown() {
        if (em != null && em.isOpen()) em.close();
        if (emf != null && emf.isOpen()) emf.close();
    }
}
