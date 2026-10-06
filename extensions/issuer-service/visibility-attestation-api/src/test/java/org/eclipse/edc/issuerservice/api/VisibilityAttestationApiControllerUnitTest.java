package org.eclipse.edc.issuerservice.api;

import org.eclipse.dse.spi.issuerservice.VisibilityAttestation;
import org.eclipse.dse.spi.issuerservice.VisibilityAttestationStore;
import org.eclipse.dse.spi.issuerservice.VisibilityScope;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisibilityAttestationApiControllerUnitTest {

    private final VisibilityAttestationStore store = mock();
    private VisibilityAttestationApiController controller;

    @BeforeEach
    void setUp() {
        controller = new VisibilityAttestationApiController(store);
    }

    @Test
    void shouldPreserveVisibilityScopeOnCreate() {
        var dto = new VisibilityAttestationDto("attestation-id", "holder-id", VisibilityScope.SUBSET);
        var attestation = new VisibilityAttestation("attestation-id", "holder-id", VisibilityScope.SUBSET);
        when(store.save(attestation)).thenReturn(StoreResult.success());

        controller.createVisibilityAttestation("participant-context-id", dto);

        verify(store).save(attestation);
    }

    @Test
    void shouldPreserveVisibilityScopeOnUpdate() {
        var dto = new VisibilityAttestationDto("attestation-id", "holder-id", VisibilityScope.NONE);
        var attestation = new VisibilityAttestation("attestation-id", "holder-id", VisibilityScope.NONE);
        when(store.update(attestation)).thenReturn(StoreResult.success());

        controller.updateVisibilityAttestation("participant-context-id", dto);

        verify(store).update(attestation);
    }

    @Test
    void shouldDeleteVisibilityAttestation() {
        var attestation = new VisibilityAttestation("attestation-id", "holder-id", VisibilityScope.ALL);
        when(store.deleteById("attestation-id")).thenReturn(StoreResult.success(attestation));

        controller.deleteVisibilityAttestation("participant-context-id", "attestation-id");

        verify(store).deleteById("attestation-id");
    }

    @Test
    void shouldUseHolderIdWhenIdIsNull() {
        var attestation = new VisibilityAttestation("holder-id", "holder-id", VisibilityScope.ALL);
        when(store.save(attestation)).thenReturn(StoreResult.success());

        controller.createVisibilityAttestation("participant-context-id",
                new VisibilityAttestationDto(null, "holder-id", VisibilityScope.ALL));

        verify(store).save(attestation);
    }

    @Test
    void shouldUseHolderIdWhenIdIsBlank() {
        var attestation = new VisibilityAttestation("holder-id", "holder-id", VisibilityScope.ALL);
        when(store.save(attestation)).thenReturn(StoreResult.success());

        controller.createVisibilityAttestation("participant-context-id",
                new VisibilityAttestationDto(" ", "holder-id", VisibilityScope.ALL));

        verify(store).save(attestation);
    }

    @Test
    void shouldRejectMissingHolderId() {
        assertThatThrownBy(() -> controller.createVisibilityAttestation("participant-context-id",
                new VisibilityAttestationDto("attestation-id", " ", VisibilityScope.ALL)))
                .isInstanceOf(jakarta.ws.rs.BadRequestException.class)
                .hasMessage("holderId is required");
    }

    @Test
    void shouldRejectMissingVisibilityScope() {
        assertThatThrownBy(() -> controller.createVisibilityAttestation("participant-context-id",
                new VisibilityAttestationDto("attestation-id", "holder-id", null)))
                .isInstanceOf(jakarta.ws.rs.BadRequestException.class)
                .hasMessage("visibility_scope is required");
    }

    @Test
    void shouldRejectMissingRequestBody() {
        assertThatThrownBy(() -> controller.createVisibilityAttestation("participant-context-id", null))
                .isInstanceOf(jakarta.ws.rs.BadRequestException.class)
                .hasMessage("Request body is required");
    }

    @Test
    void shouldCloseQueryStream() {
        var closed = new AtomicBoolean();
        var attestation = new VisibilityAttestation("attestation-id", "holder-id", VisibilityScope.ALL);
        when(store.query(any(QuerySpec.class))).thenReturn(Stream.of(attestation).onClose(() -> closed.set(true)));

        var result = controller.queryVisibilityAttestations("participant-context-id", null);

        assertThat(result).containsExactly(new VisibilityAttestationDto("attestation-id", "holder-id", VisibilityScope.ALL));
        assertThat(closed).isTrue();
    }

    @Test
    void shouldUseProvidedQuerySpec() {
        var querySpec = QuerySpec.Builder.newInstance().build();
        when(store.query(querySpec)).thenReturn(Stream.empty());

        assertThat(controller.queryVisibilityAttestations("participant-context-id", querySpec)).isEmpty();

        verify(store).query(querySpec);
    }
}