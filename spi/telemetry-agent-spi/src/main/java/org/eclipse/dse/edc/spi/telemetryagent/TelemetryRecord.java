package org.eclipse.dse.edc.spi.telemetryagent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.edc.spi.entity.StatefulEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.unmodifiableMap;

@JsonDeserialize(builder = TelemetryRecord.Builder.class)
public class TelemetryRecord extends StatefulEntity<TelemetryRecord> {

    private final Map<String, Object> properties = new HashMap<>();
    private String type;

    public Map<String, Object> getProperties() {
        return properties;
    }

    public String getType() {
        return type;
    }

    @JsonIgnore
    @Nullable
    public String getPropertyAsString(String key) {
        return getProperty(key);
    }

    @JsonIgnore
    public <T> T getProperty(String key) {
        var val = getPropertyInternal(key);
        return val != null ? (T) val : null;
    }

    @JsonIgnore
    public void transitionToCompleted() {
        transitionTo(TelemetryRecordStates.SENT.code());
    }

    @JsonIgnore
    @Override
    public TelemetryRecord copy() {
        var builder = Builder.newInstance()
                .type(type)
                .properties(getProperties());

        return copy(builder);
    }

    @JsonIgnore
    @Override
    public String stateAsString() {
        return TelemetryRecordStates.from(state).name();
    }

    @JsonIgnore
    @Nullable
    private Object getPropertyInternal(String key) {
        return properties.get(key);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder extends StatefulEntity.Builder<TelemetryRecord, Builder> {

        protected Builder(TelemetryRecord record) {
            super(record);
        }

        public Builder type(String type) {
            entity.type = type;
            property("type", type);
            return self();
        }

        public Builder id(String id) {
            entity.id = id;
            return self();
        }

        public Builder property(String key, Object value) {
            entity.properties.put(key, value);
            return self();
        }

        public Map<String, Object> getProperties() {
            return entity.properties;
        }

        public Builder properties(Map<String, Object> properties) {
            Objects.requireNonNull(properties);
            entity.properties.putAll(properties);
            return self();
        }

        @Override
        public Builder traceContext(Map<String, String> traceContext) {
            if (traceContext instanceof Map) {
                entity.traceContext = unmodifiableMap(traceContext);
            }
            return self();
        }

        @Override
        public Builder self() {
            return this;
        }

        @Override
        public TelemetryRecord build() {
            if (entity.state == 0) { // Cannot utilize equals()
                entity.state = TelemetryRecordStates.RECEIVED.code();
            }
            super.build();
            Objects.requireNonNull(entity.type, "Missing mandatory 'type'");
            return entity;
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder(new TelemetryRecord());
        }
    }
}
