package org.eclipse.eonax.edc.spi.telemetryagent;

import java.util.Arrays;

public enum TelemetryRecordStates {
    RECEIVED(100),
    SENT(200);

    private final int code;

    TelemetryRecordStates(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static TelemetryRecordStates from(int code) {
        return Arrays.stream(values()).filter(tps -> tps.code == code).findFirst().orElse(null);
    }
}
