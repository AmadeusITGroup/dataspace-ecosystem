package org.eclipse.edc.telemetrystorage.api;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.eclipse.dse.spi.telemetrystorage.TelemetryEvent;
import org.eclipse.dse.spi.telemetrystorage.TelemetryEventStore;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.UUID;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.edc.spi.result.ServiceResult.from;
import static org.eclipse.edc.web.spi.exception.ServiceResultHandler.exceptionMapper;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path("/telemetry-events")
public class TelemetryStorageApiController implements TelemetryStorageAdminApi {

    private final TelemetryEventStore store;
    private final Monitor monitor;

    public TelemetryStorageApiController(TelemetryEventStore store, Monitor monitor) {
        this.store = store;
        this.monitor = monitor;
    }

    @POST
    @Override
    public Response processTelemetryEvent(TelemetryEventDto dto) {
        monitor.debug("Adding " + dto.toString());
        var telemetryEvent = toEvent(dto);
        from(store.save(telemetryEvent)).orElseThrow(exceptionMapper(TelemetryEventDto.class));
        return Response.status(Response.Status.CREATED).build();
    }

    private TelemetryEvent toEvent(TelemetryEventDto dto) {
        return new TelemetryEvent(
             UUID.randomUUID().toString(),
             dto.contractId(),
             dto.participantDid(),
             dto.responseStatusCode(),
             dto.msgSize(),
             dto.csvId(),
             dto.timestamp()
        );
    }
}