package org.eclipse.edc.dse.dataplane.billing;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import org.eclipse.dse.edc.spi.telemetryagent.DataConsumptionRecord;
import org.eclipse.dse.edc.spi.telemetryagent.TelemetryRecordStore;
import org.eclipse.edc.spi.telemetry.Telemetry;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.dse.dataplane.billing.DataConsumptionMetricsPublisher.CONTRACT_ID_HEADER;
import static org.eclipse.edc.dse.dataplane.billing.DataConsumptionMetricsPublisher.TRACE_PARENT_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataConsumptionMetricsPublisherTest {

    private static final String PARTICIPANT_ID = "did:web:" + UUID.randomUUID();

    private final ContainerRequestContext requestContext = mock();
    private final TelemetryRecordStore telemetryRecordStore = mock();
    private final Telemetry telemetry = mock();
    private final ContainerResponseContext responseContext = mock();

    private final DataConsumptionMetricsPublisher publisher = new DataConsumptionMetricsPublisher(telemetryRecordStore, mock(), telemetry, PARTICIPANT_ID);


    @Test
    void filter_withEntity() {
        var contractId = "contract-123";
        var responseEntity = "response-entity";
        var traceContext = new HashMap<String, String>();
        traceContext.put("traceparent", "12345");
        when(responseContext.getEntity()).thenReturn(responseEntity);
        when(responseContext.getStatus()).thenReturn(200);
        when(requestContext.getHeaderString(CONTRACT_ID_HEADER)).thenReturn(contractId);
        when(telemetry.getCurrentTraceContext()).thenReturn(traceContext);

        publisher.filter(requestContext, responseContext);

        var recordCaptor = ArgumentCaptor.forClass(DataConsumptionRecord.class);
        verify(telemetryRecordStore, times(1)).save(recordCaptor.capture());
        var record = recordCaptor.getValue();

        assertThat(record.getContractId()).isEqualTo(contractId);
        assertThat(record.getResponseSize()).isEqualTo(responseEntity.getBytes().length);
        assertThat(record.getTraceContext()).isEqualTo(traceContext);
        assertThat(record.getParticipantId()).isEqualTo(PARTICIPANT_ID);
        assertThat(record.getResponseStatusCode()).isEqualTo(200);
    }

    @Test
    void filter_noEntity() {
        var contractId = "contract-123";
        var traceContext = new HashMap<String, String>();
        traceContext.put(TRACE_PARENT_HEADER, "12345");
        when(responseContext.getEntity()).thenReturn(null);
        when(requestContext.getHeaderString(CONTRACT_ID_HEADER)).thenReturn(contractId);
        when(responseContext.getStatus()).thenReturn(400);
        when(telemetry.getCurrentTraceContext()).thenReturn(traceContext);

        publisher.filter(requestContext, responseContext);

        var recordCaptor = ArgumentCaptor.forClass(DataConsumptionRecord.class);
        verify(telemetryRecordStore, times(1)).save(recordCaptor.capture());
        var record = recordCaptor.getValue();

        assertThat(record.getContractId()).isEqualTo(contractId);
        assertThat(record.getResponseSize()).isEqualTo(0);
        assertThat(record.getTraceContext()).isEqualTo(traceContext);
        assertThat(record.getParticipantId()).isEqualTo(PARTICIPANT_ID);
        assertThat(record.getResponseStatusCode()).isEqualTo(400);
    }

    @Test
    void break_no_ContractId() {
        var responseEntity = "response-entity";
        var traceContext = new HashMap<String, String>();
        traceContext.put("traceparent", "12345");
        when(responseContext.getEntity()).thenReturn(responseEntity);
        when(responseContext.getStatus()).thenReturn(200);
        when(telemetry.getCurrentTraceContext()).thenReturn(traceContext);
        String errorMessage = "Missing 'Contract-Id' header in request";
        InvalidRequestException exception = assertThrows(InvalidRequestException.class, () -> {
            publisher.filter(requestContext, responseContext);
        });
        assertEquals(errorMessage, exception.getMessage());
    }
}