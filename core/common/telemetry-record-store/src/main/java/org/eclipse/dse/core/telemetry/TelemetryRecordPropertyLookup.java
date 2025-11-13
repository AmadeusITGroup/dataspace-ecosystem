package org.eclipse.dse.core.telemetry;

import org.eclipse.dse.edc.spi.telemetryagent.TelemetryRecord;
import org.eclipse.edc.query.ReflectionPropertyLookup;
import org.eclipse.edc.spi.query.PropertyLookup;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Map.entry;

public class TelemetryRecordPropertyLookup implements PropertyLookup {

    private final PropertyLookup fallbackPropertyLookup = new ReflectionPropertyLookup();

    @Override
    public Object getProperty(String key, Object object) {
        if (object instanceof TelemetryRecord record) {
            Stream<Map.Entry<String, Function<TelemetryRecord, Map<String, Object>>>> mappings = Stream.of(
                    entry("%s", TelemetryRecord::getProperties),
                    entry("'%s'", TelemetryRecord::getProperties));

            return mappings
                    .map(entry -> fallbackPropertyLookup.getProperty(entry.getKey().formatted(key), entry.getValue().apply(record)))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseGet(() -> fallbackPropertyLookup.getProperty(key, record));
        }

        return null;
    }
}
