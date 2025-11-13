package org.eclipse.dse.edc.spi.telemetryagent;

import org.eclipse.edc.runtime.metamodel.annotation.ExtensionPoint;

/**
 * Publisher interface for sending telemetry records to a remote service.
 * <p>
 * Implementations of this interface extend {@link AutoCloseable} and must properly
 * manage underlying connection resources.
 * 
 * <h3>Lifecycle Management:</h3>
 * <ul>
 *   <li>The {@link #close()} method should be called when the publisher is no longer needed
 *       to release underlying resources (e.g., network connections, client instances).</li>
 *   <li>Implementations should be idempotent - calling {@link #close()} multiple times
 *       must be safe and should not throw exceptions after the first invocation.</li>
 *   <li>After {@link #close()} is called, subsequent calls to {@link #sendRecord(TelemetryRecord)}
 *       may fail and should handle the closed state gracefully.</li>
 * </ul>
 * 
 * <h3>Thread Safety:</h3>
 * Implementations are not required to be thread-safe. Callers must ensure proper
 * synchronization when accessing the same publisher instance from multiple threads.
 */
@ExtensionPoint
public interface TelemetryRecordPublisher extends AutoCloseable {

    /**
     * Sends a telemetry record to the remote service.
     *
     * @param telemetryRecord the record to send
     * @return {@code true} if the record was sent successfully, {@code false} otherwise
     */
    Boolean sendRecord(TelemetryRecord telemetryRecord);
    
    /**
     * Closes this publisher and releases any underlying resources.
     * <p>
     * This method should be idempotent - calling it multiple times must be safe.
     * After this method is called, the publisher should not be used for sending records.
     *
     * @throws Exception if an error occurs while closing the publisher
     */
    @Override
    void close() throws Exception;
}
