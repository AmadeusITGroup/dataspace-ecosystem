package org.eclipse.dse.core.telemetry;

import org.eclipse.dse.edc.spi.telemetryagent.TelemetryRecord;
import org.eclipse.dse.edc.spi.telemetryagent.TelemetryRecordPublisher;
import org.eclipse.dse.edc.spi.telemetryagent.TelemetryRecordPublisherFactory;
import org.eclipse.dse.edc.spi.telemetryagent.TelemetryRecordStore;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.telemetry.Telemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelemetryAgentTest {

    private final TelemetryRecordStore store = mock();
    private final TelemetryRecordPublisherFactory publisherFactory = mock();
    private final TelemetryRecordPublisher publisher = mock();
    private final TokenCache cache = mock();
    private final Monitor monitor = mock();
    private final Telemetry telemetry = new Telemetry();

    private TelemetryAgent agent;

    @BeforeEach
    void setUp() {
        agent = TelemetryAgent.Builder.newInstance()
                .publisherFactory(publisherFactory)
                .credentialsCache(cache)
                .store(store)
                .monitor(monitor)
                .telemetry(telemetry)
                .clock(Clock.systemUTC())
                .batchSize(10)
                .build();
    }

    @Test
    void createPublisher_cacheReturnsNull_shouldReturnEmpty() throws Exception {
        when(cache.get()).thenReturn(null);

        var result = invokeCreatePublisher();

        assertThat(result).isEmpty();
        verify(publisherFactory, never()).createClient(any());
    }

    @Test
    void createPublisher_cacheReturnsCredential_shouldCreatePublisher() throws Exception {
        var token = TokenRepresentation.Builder.newInstance().token("test-token").build();
        when(cache.get()).thenReturn(token);
        when(publisherFactory.createClient(token)).thenReturn(publisher);

        var result = invokeCreatePublisher();

        assertThat(result).isPresent().contains(publisher);
        verify(publisherFactory).createClient(token);
    }

    @Test
    void createPublisher_calledTwice_shouldReuseExistingPublisher() throws Exception {
        var token = TokenRepresentation.Builder.newInstance().token("test-token").build();
        when(cache.get()).thenReturn(token);
        when(publisherFactory.createClient(token)).thenReturn(publisher);

        invokeCreatePublisher();
        var result = invokeCreatePublisher();

        assertThat(result).isPresent().contains(publisher);
        verify(publisherFactory).createClient(token);
    }

    @Test
    void sendRecords_allSucceed_shouldReturnCount() throws Exception {
        var record1 = TelemetryRecord.Builder.newInstance().type("test").build();
        var record2 = TelemetryRecord.Builder.newInstance().type("test").build();
        when(publisher.sendRecord(any())).thenReturn(true);
        when(store.save(any())).thenReturn(org.eclipse.edc.spi.result.StoreResult.success());

        var result = invokeSendRecords(publisher, List.of(record1, record2));

        assertThat(result).isEqualTo(2L);
    }

    @Test
    void sendRecords_someFail_shouldReturnSuccessCount() throws Exception {
        var record1 = TelemetryRecord.Builder.newInstance().type("test").build();
        var record2 = TelemetryRecord.Builder.newInstance().type("test").build();
        when(publisher.sendRecord(record1)).thenReturn(true);
        when(publisher.sendRecord(record2)).thenReturn(false);
        when(store.save(any())).thenReturn(org.eclipse.edc.spi.result.StoreResult.success());

        var result = invokeSendRecords(publisher, List.of(record1, record2));

        assertThat(result).isEqualTo(1L);
    }

    @Test
    void sendRecords_emptyCollection_shouldReturnZero() throws Exception {
        var result = invokeSendRecords(publisher, List.of());

        assertThat(result).isEqualTo(0L);
    }

    @SuppressWarnings("unchecked")
    private Optional<TelemetryRecordPublisher> invokeCreatePublisher() throws Exception {
        Method method = TelemetryAgent.class.getDeclaredMethod("createPublisher");
        method.setAccessible(true);
        return (Optional<TelemetryRecordPublisher>) method.invoke(agent);
    }

    private Long invokeSendRecords(TelemetryRecordPublisher pub, Collection<TelemetryRecord> records) throws Exception {
        Method method = TelemetryAgent.class.getDeclaredMethod("sendRecords", TelemetryRecordPublisher.class, Collection.class);
        method.setAccessible(true);
        return (Long) method.invoke(agent, pub, records);
    }
}
