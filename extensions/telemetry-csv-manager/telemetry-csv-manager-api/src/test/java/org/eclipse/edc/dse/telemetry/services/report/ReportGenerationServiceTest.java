package org.eclipse.edc.dse.telemetry.services.report;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.eclipse.edc.dse.telemetry.model.ParticipantId;
import org.eclipse.edc.dse.telemetry.model.Report;
import org.eclipse.edc.dse.telemetry.model.TelemetryEvent;
import org.eclipse.edc.dse.telemetry.repository.ParticipantRepository;
import org.eclipse.edc.dse.telemetry.repository.ReportRepository;
import org.eclipse.edc.dse.telemetry.repository.TelemetryEventRepository;
import org.eclipse.edc.dse.telemetry.services.ReportUtil;
import org.eclipse.edc.dse.telemetry.services.storage.AzureStorageService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.eclipse.edc.dse.telemetry.TestUtils.P1_DID;
import static org.eclipse.edc.dse.telemetry.TestUtils.P2_DID;
import static org.eclipse.edc.dse.telemetry.TestUtils.PARTICIPANT_NAME;
import static org.eclipse.edc.dse.telemetry.TestUtils.PARTICIPANT_NAME_2;
import static org.eclipse.edc.dse.telemetry.TestUtils.TEST_PERSISTENCE_UNIT;
import static org.eclipse.edc.dse.telemetry.TestUtils.USER_EMAIL;
import static org.eclipse.edc.dse.telemetry.TestUtils.USER_EMAIL_2;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportGenerationServiceTest {

    public static final String CONTRACT_1 = "contract1";
    private static final String CONTRACT_2 = "contract2";
    private static final String CONTRACT_3 = "contract3";
    private ReportRepository reportRepository;
    private ParticipantRepository participantRepo;
    private TelemetryEventRepository telemetryEventRepo;
    private static EntityManager em;
    private static EntityManagerFactory emf;


    @BeforeAll
    void setup() {
        emf = Persistence.createEntityManagerFactory(TEST_PERSISTENCE_UNIT);
        em = emf.createEntityManager();

        reportRepository = new ReportRepository(em);
        participantRepo = new ParticipantRepository(em);
        telemetryEventRepo = new TelemetryEventRepository(em);

    }

    @AfterEach
    void tearDown() {
        // Reports depend on participants so reports should be deleted first to break the dependencies
        em.getTransaction().begin();
        reportRepository.findAll().forEach(reportRepository::delete);
        telemetryEventRepo.findAll().forEach(telemetryEventRepo::delete);
        participantRepo.findAll().forEach(participantRepo::delete);
        em.getTransaction().commit();

        ReportGenerationService.getErrors().clear();
    }

    @Test
    void reportGenerationSucceeds() {
        em.getTransaction().begin();
        ParticipantId participant1 = new ParticipantId(P1_DID, USER_EMAIL, PARTICIPANT_NAME);
        ParticipantId participant2 = new ParticipantId(P2_DID, USER_EMAIL_2, PARTICIPANT_NAME_2);
        participantRepo.save(participant1);
        participantRepo.save(participant2);

        telemetryEventRepo.save(createTelemetryEvent(CONTRACT_1, participant1, LocalDateTime.of(2025, Month.AUGUST, 23, 12, 0), 159));
        telemetryEventRepo.save(createTelemetryEvent(CONTRACT_1, participant2, LocalDateTime.of(2025, Month.AUGUST, 23, 12, 2), 159));
        em.getTransaction().commit();

        Monitor mockedMonitor = mock(Monitor.class);
        doNothing().when(mockedMonitor).severe(any(String.class));
        doNothing().when(mockedMonitor).info(any(String.class));

        AzureStorageService mockedAzureStorageService = mock(AzureStorageService.class);
        doAnswer(c -> "objectUrl").when(mockedAzureStorageService).upload(any(), any());

        ReportGenerationService reportGenerationService = new ReportGenerationService(mockedMonitor, participantRepo, reportRepository, telemetryEventRepo, mockedAzureStorageService);
        try (MockedStatic<ReportUtil> mockedStatic = Mockito.mockStatic(ReportUtil.class)) {
            AtomicReference<String> capturedContent = new AtomicReference<>();
            mockedStatic.when(() -> ReportUtil.generateCsvReportContent(any()))
                    .thenAnswer(invocation -> {
                        String result = (String) invocation.callRealMethod();
                        capturedContent.set(result);
                        return result;
                    });
            mockedStatic.when(() -> ReportUtil.generateReportFileName(any())).then(InvocationOnMock::callRealMethod);
            mockedStatic.when(() -> ReportUtil.getValue(any(Integer.class))).then(InvocationOnMock::callRealMethod);
            mockedStatic.when(() -> ReportUtil.getValue(any(Long.class))).then(InvocationOnMock::callRealMethod);
            mockedStatic.when(() -> ReportUtil.getValue(any(String.class))).then(InvocationOnMock::callRealMethod);

            reportGenerationService.generateReport(participant1, LocalDateTime.of(2025, Month.AUGUST, 23, 12, 0));

            assertEquals(1, reportRepository.findAll().size());
            Report report = reportRepository.findAll().get(0);
            validateReport(report, participant1, 1);
            List<String> expectedCsv = List.of("contractId,sum,count", "contract1,159,1");
            assertLinesMatch(expectedCsv, capturedContent.get().lines().toList());
        }
    }

    @Test
    void reportGenerationWithMultipleContractsSucceeds() {
        em.getTransaction().begin();
        ParticipantId participant1 = new ParticipantId(P1_DID, USER_EMAIL, PARTICIPANT_NAME);
        ParticipantId participant2 = new ParticipantId(P2_DID, USER_EMAIL_2, PARTICIPANT_NAME_2);
        participantRepo.save(participant1);
        participantRepo.save(participant2);

        telemetryEventRepo.save(createTelemetryEvent(CONTRACT_1, participant1, LocalDateTime.of(2025, Month.AUGUST, 23, 12, 0), 159));
        telemetryEventRepo.save(createTelemetryEvent(CONTRACT_1, participant2, LocalDateTime.of(2025, Month.AUGUST, 23, 12, 2), 159));
        telemetryEventRepo.save(createTelemetryEvent(CONTRACT_1, participant1, LocalDateTime.of(2025, Month.AUGUST, 23, 21, 2), 40));
        telemetryEventRepo.save(createTelemetryEvent(CONTRACT_1, participant2, LocalDateTime.of(2025, Month.AUGUST, 23, 21, 6), 40));
        telemetryEventRepo.save(createTelemetryEvent(CONTRACT_2, participant1, LocalDateTime.of(2025, Month.AUGUST, 15, 13, 2), 159));
        telemetryEventRepo.save(createTelemetryEvent(CONTRACT_2, participant2, LocalDateTime.of(2025, Month.AUGUST, 14, 13, 2), 159));
        telemetryEventRepo.save(createTelemetryEvent(CONTRACT_3, participant1, LocalDateTime.of(2025, Month.AUGUST, 2, 18, 7), 500));
        telemetryEventRepo.save(createTelemetryEvent(CONTRACT_3, participant2, LocalDateTime.of(2025, Month.AUGUST, 2, 18, 2), 500));
        em.getTransaction().commit();

        Monitor mockedMonitor = mock(Monitor.class);
        doNothing().when(mockedMonitor).severe(any(String.class));
        doNothing().when(mockedMonitor).info(any(String.class));

        AzureStorageService mockedAzureStorageService = mock(AzureStorageService.class);
        doAnswer(c -> "objectUrl").when(mockedAzureStorageService).upload(any(), any());

        ReportGenerationService reportGenerationService = new ReportGenerationService(mockedMonitor, participantRepo, reportRepository, telemetryEventRepo, mockedAzureStorageService);
        try (MockedStatic<ReportUtil> mockedStatic = Mockito.mockStatic(ReportUtil.class)) {
            AtomicReference<String> capturedContent = new AtomicReference<>();
            mockedStatic.when(() -> ReportUtil.generateCsvReportContent(any()))
                    .thenAnswer(invocation -> {
                        String result = (String) invocation.callRealMethod();
                        capturedContent.set(result);
                        return result;
                    });
            mockedStatic.when(() -> ReportUtil.generateReportFileName(any())).then(InvocationOnMock::callRealMethod);
            mockedStatic.when(() -> ReportUtil.getValue(any(Integer.class))).then(InvocationOnMock::callRealMethod);
            mockedStatic.when(() -> ReportUtil.getValue(any(Long.class))).then(InvocationOnMock::callRealMethod);
            mockedStatic.when(() -> ReportUtil.getValue(any(String.class))).then(InvocationOnMock::callRealMethod);

            reportGenerationService.generateReport(participant1, LocalDateTime.of(2025, Month.AUGUST, 23, 12, 0));

            assertEquals(1, reportRepository.findAll().size());
            Report report = reportRepository.findAll().get(0);
            validateReport(report, participant1, 4);
            List<String> expectedCsv = List.of("contractId,sum,count", "contract1,199,2", "contract2,159,1", "contract3,500,1");
            assertLinesMatch(expectedCsv, capturedContent.get().lines().toList());
        }
    }

    @Test
    void reportGenerationValidationFailsDueToInconsistentMsgSizeButGenerationSucceeds() {
        em.getTransaction().begin();
        ParticipantId participant1 = new ParticipantId(P1_DID, USER_EMAIL, PARTICIPANT_NAME);
        ParticipantId participant2 = new ParticipantId(P2_DID, USER_EMAIL_2, PARTICIPANT_NAME_2);
        participantRepo.save(participant1);
        participantRepo.save(participant2);

        telemetryEventRepo.save(createTelemetryEvent(CONTRACT_1, participant1, LocalDateTime.of(2025, Month.AUGUST, 23, 12, 0), 160));
        telemetryEventRepo.save(createTelemetryEvent(CONTRACT_1, participant2, LocalDateTime.of(2025, Month.AUGUST, 23, 12, 2), 159));
        em.getTransaction().commit();

        AzureStorageService mockedAzureStorageService = mock(AzureStorageService.class);
        doAnswer(c -> "objectUrl").when(mockedAzureStorageService).upload(any(), any());

        Monitor mockedMonitor = mock(Monitor.class);
        ReportGenerationService reportGenerationService = new ReportGenerationService(mockedMonitor, participantRepo, reportRepository, telemetryEventRepo, mockedAzureStorageService);

        assertDoesNotThrow(() -> reportGenerationService.generateReport(participant1, LocalDateTime.of(2025, Month.AUGUST, 23, 12, 0)));

        assertEquals(1, reportRepository.findAll().size());
        Report report = reportRepository.findAll().get(0);
        validateReport(report, participant1, 1);
        Queue<ReportGenerationError> errors = ReportGenerationService.getErrors();
        assertEquals(1, errors.size());
        ReportGenerationError reportGenerationError = errors.peek();
        assertNotNull(reportGenerationError);
        assertEquals(159, reportGenerationError.counterpartyMsgSize());
        assertEquals(160, reportGenerationError.participantMsgSize());
        assertEquals(participant1.getId(), reportGenerationError.participantId());
        assertEquals(participant2.getId(), reportGenerationError.counterpartyId());
        assertEquals(1, reportGenerationError.participantEventCount());
        assertEquals(1, reportGenerationError.counterpartyEventCount());
        assertEquals(CONTRACT_1, reportGenerationError.contractId());
        assertEquals("Message size mismatch", reportGenerationError.errorMessage());
    }

    @Test
    void reportGenerationValidationFailsDueToInconsistentMsgSizeAndInconsistentEventCountButGenerationSucceeds() {
        em.getTransaction().begin();
        ParticipantId participant1 = new ParticipantId(P1_DID, USER_EMAIL, PARTICIPANT_NAME);
        ParticipantId participant2 = new ParticipantId(P2_DID, USER_EMAIL_2, PARTICIPANT_NAME_2);
        participantRepo.save(participant1);
        participantRepo.save(participant2);

        LocalDateTime targetDateTime = LocalDateTime.of(2025, Month.AUGUST, 23, 12, 0);
        telemetryEventRepo.save(createTelemetryEvent(CONTRACT_1, participant1, targetDateTime, 160));
        telemetryEventRepo.save(createTelemetryEvent(CONTRACT_1, participant2, LocalDateTime.of(2025, Month.AUGUST, 23, 12, 2), 160));
        telemetryEventRepo.save(createTelemetryEvent(CONTRACT_1, participant2, LocalDateTime.of(2025, Month.AUGUST, 23, 12, 6), 160));
        em.getTransaction().commit();

        AzureStorageService mockedAzureStorageService = mock(AzureStorageService.class);
        doAnswer(c -> "objectUrl").when(mockedAzureStorageService).upload(any(), any());

        Monitor mockedMonitor = mock(Monitor.class);
        ReportGenerationService reportGenerationService = new ReportGenerationService(mockedMonitor, participantRepo, reportRepository, telemetryEventRepo, mockedAzureStorageService);
        assertDoesNotThrow(() -> reportGenerationService.generateReport(participant1, targetDateTime));

        assertEquals(1, reportRepository.findAll().size());
        Report report = reportRepository.findAll().get(0);
        validateReport(report, participant1, 1);
        Queue<ReportGenerationError> errors = ReportGenerationService.getErrors();
        assertEquals(1, errors.size());
        ReportGenerationError reportGenerationError = errors.peek();
        assertNotNull(reportGenerationError);
        assertEquals(320, reportGenerationError.counterpartyMsgSize());
        assertEquals(160, reportGenerationError.participantMsgSize());
        assertEquals(participant1.getId(), reportGenerationError.participantId());
        assertEquals(participant2.getId(), reportGenerationError.counterpartyId());
        assertEquals(1, reportGenerationError.participantEventCount());
        assertEquals(2, reportGenerationError.counterpartyEventCount());
        assertEquals(CONTRACT_1, reportGenerationError.contractId());
        assertEquals("Both message size and event count mismatch", reportGenerationError.errorMessage());

        // Validates content of error report
        try (MockedStatic<ReportUtil> mockedStatic = Mockito.mockStatic(ReportUtil.class)) {
            AtomicReference<String> capturedContent = new AtomicReference<>();
            mockedStatic.when(() -> ReportUtil.generateCsvErrorReportContent(any()))
                    .thenAnswer(invocation -> {
                        String result = (String) invocation.callRealMethod();
                        capturedContent.set(result);
                        return result;
                    });
            mockedStatic.when(() -> ReportUtil.getValue(any(Integer.class))).then(InvocationOnMock::callRealMethod);
            mockedStatic.when(() -> ReportUtil.getValue(any(Long.class))).then(InvocationOnMock::callRealMethod);
            mockedStatic.when(() -> ReportUtil.getValue(any(String.class))).then(InvocationOnMock::callRealMethod);

            reportGenerationService.generateErrorReport(targetDateTime, ReportGenerationService.getErrors());


            List<String> expectedCsv = List.of("contractId,participantId,counterpartyId,participantMsgSize,counterpartyMsgSize,participantEventCount,counterpartyEventCount,errorMessage",
                    "contract1,did:web:p1-identityhub%3A8383:api:did,did:web:p2-identityhub%3A8383:api:did,160,320,1,2,Both message size and event count mismatch");
            assertLinesMatch(expectedCsv, capturedContent.get().lines().toList());
        }
    }

    @Test
    void reportGenerationValidationFailsDueToInconsistentEventCountButGenerationSucceeds() {
        em.getTransaction().begin();
        ParticipantId participant1 = new ParticipantId(P1_DID, USER_EMAIL, PARTICIPANT_NAME);
        ParticipantId participant2 = new ParticipantId(P2_DID, USER_EMAIL_2, PARTICIPANT_NAME_2);
        participantRepo.save(participant1);
        participantRepo.save(participant2);

        LocalDateTime targetDateTime = LocalDateTime.of(2025, Month.AUGUST, 23, 12, 0);
        telemetryEventRepo.save(createTelemetryEvent(CONTRACT_1, participant1, targetDateTime, 160));
        telemetryEventRepo.save(createTelemetryEvent(CONTRACT_1, participant2, LocalDateTime.of(2025, Month.AUGUST, 23, 12, 2), 80));
        telemetryEventRepo.save(createTelemetryEvent(CONTRACT_1, participant2, LocalDateTime.of(2025, Month.AUGUST, 23, 12, 6), 80));
        em.getTransaction().commit();

        AzureStorageService mockedAzureStorageService = mock(AzureStorageService.class);
        doAnswer(c -> "objectUrl").when(mockedAzureStorageService).upload(any(), any());
        Monitor mockedMonitor = mock(Monitor.class);
        ReportGenerationService reportGenerationService = new ReportGenerationService(mockedMonitor, participantRepo, reportRepository, telemetryEventRepo, mockedAzureStorageService);
        assertDoesNotThrow(() -> reportGenerationService.generateReport(participant1, targetDateTime));

        assertEquals(1, reportRepository.findAll().size());
        Report report = reportRepository.findAll().get(0);
        validateReport(report, participant1, 1);

        Queue<ReportGenerationError> errors = ReportGenerationService.getErrors();
        assertEquals(1, errors.size());
        ReportGenerationError reportGenerationError = errors.peek();
        assertNotNull(reportGenerationError);
        assertEquals(160, reportGenerationError.counterpartyMsgSize());
        assertEquals(160, reportGenerationError.participantMsgSize());
        assertEquals(participant1.getId(), reportGenerationError.participantId());
        assertEquals(participant2.getId(), reportGenerationError.counterpartyId());
        assertEquals(1, reportGenerationError.participantEventCount());
        assertEquals(2, reportGenerationError.counterpartyEventCount());
        assertEquals(CONTRACT_1, reportGenerationError.contractId());
        assertEquals("Event count mismatch", reportGenerationError.errorMessage());

        // Validates content of error report
        try (MockedStatic<ReportUtil> mockedStatic = Mockito.mockStatic(ReportUtil.class)) {
            AtomicReference<String> capturedContent = new AtomicReference<>();
            mockedStatic.when(() -> ReportUtil.generateCsvErrorReportContent(any()))
                    .thenAnswer(invocation -> {
                        String result = (String) invocation.callRealMethod();
                        capturedContent.set(result);
                        return result;
                    });
            mockedStatic.when(() -> ReportUtil.getValue(any(Integer.class))).then(InvocationOnMock::callRealMethod);
            mockedStatic.when(() -> ReportUtil.getValue(any(Long.class))).then(InvocationOnMock::callRealMethod);
            mockedStatic.when(() -> ReportUtil.getValue(any(String.class))).then(InvocationOnMock::callRealMethod);

            reportGenerationService.generateErrorReport(targetDateTime, ReportGenerationService.getErrors());

            List<String> expectedCsv = List.of("contractId,participantId,counterpartyId,participantMsgSize,counterpartyMsgSize,participantEventCount,counterpartyEventCount,errorMessage",
                    "contract1,did:web:p1-identityhub%3A8383:api:did,did:web:p2-identityhub%3A8383:api:did,160,160,1,2,Event count mismatch");

            assertLinesMatch(expectedCsv, capturedContent.get().lines().toList());
        }
    }

    @Test
    void reportGenerationValidationForParticipantFailsDueToInvalidContractPartyNumber() {
        em.getTransaction().begin();
        ParticipantId participant1 = new ParticipantId(P1_DID, USER_EMAIL, PARTICIPANT_NAME);
        ParticipantId participant2 = new ParticipantId(P2_DID, USER_EMAIL_2, PARTICIPANT_NAME_2);
        participantRepo.save(participant1);
        participantRepo.save(participant2);

        // Only creates one telemetry event for one of the parties so the validation of consumer and provider will fail
        LocalDateTime targetDateTime = LocalDateTime.of(2025, Month.AUGUST, 23, 12, 0);
        TelemetryEvent telemetryEvent1 = createTelemetryEvent(CONTRACT_1, participant1, targetDateTime, 159);
        telemetryEventRepo.save(telemetryEvent1);
        em.getTransaction().commit();

        AzureStorageService mockedAzureStorageService = mock(AzureStorageService.class);
        doAnswer(c -> "objectUrl").when(mockedAzureStorageService).upload(any(), any());
        Monitor mockedMonitor = mock(Monitor.class);
        ReportGenerationService reportGenerationService = new ReportGenerationService(mockedMonitor, participantRepo, reportRepository, telemetryEventRepo, mockedAzureStorageService);
        assertDoesNotThrow(() -> reportGenerationService.generateReport(participant1, targetDateTime));

        // Report generation failed
        assertEquals(1, reportRepository.findAll().size());
        Report report = reportRepository.findAll().get(0);
        validateReport(report, participant1, 1);

        Queue<ReportGenerationError> errors = ReportGenerationService.getErrors();
        assertEquals(1, errors.size());
        ReportGenerationError reportGenerationError = errors.peek();
        validateGenerationError(reportGenerationError, participant1);

        ArgumentCaptor<String> warningCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockedMonitor, atLeastOnce()).warning(warningCaptor.capture());

        List<String> allWarnings = warningCaptor.getAllValues();
        assertTrue(allWarnings.stream().anyMatch(msg -> msg.contains("Contract " + CONTRACT_1 + " does not have exactly 2 parties, found: 1")));

        // Validates content of error report
        try (MockedStatic<ReportUtil> mockedStatic = Mockito.mockStatic(ReportUtil.class)) {
            AtomicReference<String> capturedContent = new AtomicReference<>();
            mockedStatic.when(() -> ReportUtil.generateCsvErrorReportContent(any()))
                    .thenAnswer(invocation -> {
                        String result = (String) invocation.callRealMethod();
                        capturedContent.set(result);
                        return result;
                    });
            mockedStatic.when(() -> ReportUtil.getValue(any(Integer.class))).then(InvocationOnMock::callRealMethod);
            mockedStatic.when(() -> ReportUtil.getValue(any(Long.class))).then(InvocationOnMock::callRealMethod);
            mockedStatic.when(() -> ReportUtil.getValue(any(String.class))).then(InvocationOnMock::callRealMethod);

            reportGenerationService.generateErrorReport(targetDateTime, ReportGenerationService.getErrors());

            List<String> expectedCsv = List.of("contractId,participantId,counterpartyId,participantMsgSize,counterpartyMsgSize,participantEventCount,counterpartyEventCount,errorMessage",
                    "contract1,did:web:p1-identityhub%3A8383:api:did,N/A,159,null,1,null,Invalid number of parties: 1");
            assertLinesMatch(expectedCsv,
                    capturedContent.get().lines().toList());
        }
    }

    private static void validateGenerationError(ReportGenerationError reportGenerationError, ParticipantId participant1) {
        assertNotNull(reportGenerationError);
        assertNull(reportGenerationError.counterpartyMsgSize());
        assertEquals(159, reportGenerationError.participantMsgSize());
        assertEquals(participant1.getId(), reportGenerationError.participantId());
        assertEquals("N/A", reportGenerationError.counterpartyId());
        assertEquals(1, reportGenerationError.participantEventCount());
        assertNull(reportGenerationError.counterpartyEventCount());
        assertEquals(CONTRACT_1, reportGenerationError.contractId());
        assertEquals("Invalid number of parties: 1", reportGenerationError.errorMessage());
    }

    private static void validateReport(Report report, ParticipantId participant1, int expected) {
        assertEquals("objectUrl", report.getCsvLink());
        assertEquals(participant1, report.getParticipant());
        assertEquals(expected, report.getTelemetryEvents().size());
    }

    @ParameterizedTest
    @MethodSource("provideErrorMessageInputs")
    void errorMessageGenerationValidation(Integer partiesNumber, boolean msgSizeMatches, boolean eventCountMatches, String errorMessage) {
        assertEquals(errorMessage, ReportUtil.generateErrorMessage(partiesNumber, msgSizeMatches, eventCountMatches));
    }

    public Stream<Arguments> provideErrorMessageInputs() {
        return Stream.of(Arguments.of(3, true, false, "Invalid number of parties: 3"),
                Arguments.of(4, true, false, "Invalid number of parties: 4"),
                Arguments.of(3, true, true, "Invalid number of parties: 3"),
                Arguments.of(3, false, true, "Invalid number of parties: 3"),
                Arguments.of(3, true, false, "Invalid number of parties: 3"),
                Arguments.of(3, false, false, "Invalid number of parties: 3"),
                Arguments.of(2, false, false, "Both message size and event count mismatch"),
                Arguments.of(2, false, true, "Message size mismatch"),
                Arguments.of(2, true, false, "Event count mismatch"),
                Arguments.of(2, true, true, "Event count mismatch"),
                Arguments.of(2, false, false, "Both message size and event count mismatch")
        );
    }

    private static @NotNull TelemetryEvent createTelemetryEvent(String contractId, ParticipantId participant, LocalDateTime timestamp, Integer msgSize) {
        TelemetryEvent telemetryEvent1 = new TelemetryEvent();
        telemetryEvent1.setId(UUID.randomUUID().toString());
        telemetryEvent1.setContractId(contractId);
        telemetryEvent1.setParticipant(participant);
        telemetryEvent1.setResponseStatusCode(200);
        telemetryEvent1.setMsgSize(msgSize);
        telemetryEvent1.setCsvReport(null);
        telemetryEvent1.setTimestamp(timestamp);
        return telemetryEvent1;
    }

    @AfterAll
    static void teardown() {
        if (em != null && em.isOpen()) em.close();
        if (emf != null && emf.isOpen()) emf.close();
    }
}