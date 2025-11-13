package org.eclipse.edc.telemetrystorage.api;

import io.restassured.specification.RequestSpecification;
import org.eclipse.dse.spi.telemetrystorage.TelemetryEvent;
import org.eclipse.dse.spi.telemetrystorage.TelemetryEventStore;
import org.eclipse.edc.junit.annotations.ApiTest;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.web.jersey.testfixtures.RestControllerTestBase;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ApiTest
class TelemetryStorageApiControllerTest extends RestControllerTestBase {

    private final TelemetryEventStore store = mock();
    private final Monitor monitor = mock();

    @Override
    protected Object controller() {
        return new TelemetryStorageApiController(store, monitor);
    }

    private RequestSpecification baseRequest() {
        return given()
                .baseUri("http://localhost:" + port + "/telemetry-events")
                .contentType(JSON)
                .when();
    }

    private static TelemetryEventDto createTelemetryEventDto() {
        return new TelemetryEventDto(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                200,
                1024,
                null,
                Timestamp.from(Instant.now())
        );
    }

    @Nested
    class ProcessTelemetryEvent {

        @Test
        void success() {
            var dto = createTelemetryEventDto();
            var telemetryEvent = new TelemetryEvent(
                    dto.id(),
                    dto.contractId(),
                    dto.participantDid(),
                    dto.responseStatusCode(),
                    dto.msgSize(),
                    dto.csvId(),
                    dto.timestamp()
            );

            when(store.save(assertArg(a -> a.id().equals(telemetryEvent.id()))))
                    .thenReturn(StoreResult.success());

            baseRequest()
                    .body(dto)
                    .post()
                    .then()
                    .log().ifError()
                    .statusCode(201);

            verify(store).save(assertArg(a -> a.id().equals(telemetryEvent.id())));
        }

        @Test
        void failure() {
            var dto = createTelemetryEventDto();
            var telemetryEvent = new TelemetryEvent(
                    dto.id(),
                    dto.contractId(),
                    dto.participantDid(),
                    dto.responseStatusCode(),
                    dto.msgSize(),
                    dto.csvId(),
                    dto.timestamp()
            );

            when(store.save(assertArg(a -> a.id().equals(telemetryEvent.id()))))
                    .thenReturn(StoreResult.generalError("Error saving telemetry event"));

            baseRequest()
                    .body(dto)
                    .post()
                    .then()
                    .log().ifError()
                    .statusCode(400);

            verify(store).save(assertArg(a -> a.id().equals(telemetryEvent.id())));
        }
    }
}