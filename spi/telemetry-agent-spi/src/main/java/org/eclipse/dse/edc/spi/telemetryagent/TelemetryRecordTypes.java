package org.eclipse.dse.edc.spi.telemetryagent;

import java.util.Arrays;

public enum TelemetryRecordTypes {
    DATA_CONSUMPTION("DataConsumption");

    private final String type;

    TelemetryRecordTypes(String type) {
        this.type = type;
    }

    public String type() {
        return type;
    }

    public static TelemetryRecordTypes from(String type) {
        return Arrays.stream(values()).filter(tps -> tps.type == type).findFirst().orElse(null);
    }
}
