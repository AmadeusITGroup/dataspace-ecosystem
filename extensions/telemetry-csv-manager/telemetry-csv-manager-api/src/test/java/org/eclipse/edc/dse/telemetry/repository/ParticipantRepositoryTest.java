package org.eclipse.edc.dse.telemetry.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.eclipse.edc.dse.telemetry.model.ParticipantId;
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

import static org.eclipse.edc.dse.telemetry.TestUtils.P1_DID;
import static org.eclipse.edc.dse.telemetry.TestUtils.PARTICIPANT_NAME;
import static org.eclipse.edc.dse.telemetry.TestUtils.TEST_PERSISTENCE_UNIT;
import static org.eclipse.edc.dse.telemetry.TestUtils.USER_EMAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ParticipantRepositoryTest {

    private ParticipantRepository repo;
    private static EntityManager em;
    private static EntityManagerFactory emf;

    @BeforeAll
    void setup() {
        emf = Persistence.createEntityManagerFactory(TEST_PERSISTENCE_UNIT);
        em = emf.createEntityManager();

        repo = new ParticipantRepository(em);
    }

    @AfterEach
    void tearDown() {
        repo.findAll().forEach(repo::deleteTransactional);
    }

    @Test
    @DisplayName("Should save and find participant successfully")
    void shouldSucceed_WhenSaveAndFindParticipant() {
        ParticipantId participant = new ParticipantId(P1_DID, USER_EMAIL, "p1");
        repo.saveTransactional(participant);

        ParticipantId found = repo.find(P1_DID);
        assertNotNull(found);
        assertEquals("user@example.com", found.getEmail());
    }

    @Test
    @DisplayName("Should find all participants successfully")
    void shouldSucceed_WhenFindAllParticipants() {
        repo.saveTransactional(new ParticipantId("did:web:p2-identityhub%3A8383:api:did", "u2@example.com", "p2"));
        repo.saveTransactional(new ParticipantId("did:web:p3-identityhub%3A8383:api:did", "u3@example.com", "p3"));
        List<ParticipantId> all = repo.findAll();
        assertEquals(2, all.size());
    }

    @ParameterizedTest(name = "Should fail to save participant with invalid arguments: did=''{0}'', email=''{1}'', name=''{2}''")
    @MethodSource("invalidParticipantArguments")
    void testSaveParticipantWithInvalidArgumentsFails(String did, String email, String participantName) {
        assertThrows(IllegalArgumentException.class, () -> new ParticipantId(did, email, participantName));
    }

    public Stream<Arguments> invalidParticipantArguments() {
        return Stream.of(Arguments.of(null, USER_EMAIL, PARTICIPANT_NAME),
                Arguments.of("", USER_EMAIL, PARTICIPANT_NAME),
                Arguments.of(" ", USER_EMAIL, PARTICIPANT_NAME),
                Arguments.of(P1_DID, null, PARTICIPANT_NAME),
                Arguments.of(P1_DID, "", PARTICIPANT_NAME),
                Arguments.of(P1_DID, " ", PARTICIPANT_NAME),
                Arguments.of(P1_DID, USER_EMAIL, null),
                Arguments.of(P1_DID, USER_EMAIL, ""),
                Arguments.of(P1_DID, USER_EMAIL, " ")
        );
    }

    @Test
    @DisplayName("Should return participant when it exists")
    void shouldSucceed_WhenFindByParticipant() {
        ParticipantId participant = new ParticipantId(P1_DID, USER_EMAIL, PARTICIPANT_NAME);
        repo.saveTransactional(participant);

        ParticipantId found = repo.findByParticipantName(PARTICIPANT_NAME);
        assertNotNull(found);
        assertEquals(USER_EMAIL, found.getEmail());
        assertEquals(PARTICIPANT_NAME, found.getName());
    }

    @AfterAll
    static void teardown() {
        if (em != null && em.isOpen()) em.close();
        if (emf != null && emf.isOpen()) emf.close();
    }

}
