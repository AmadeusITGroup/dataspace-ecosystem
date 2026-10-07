/*
 *  Copyright (c) 2026 Amadeus IT Group
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.edc.dse.transform;

import org.eclipse.edc.connector.controlplane.catalog.spi.DataService;
import org.eclipse.edc.connector.controlplane.catalog.spi.Distribution;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class EnrichedDistribution extends Distribution {

    private final String format;
    private final DataService dataService;
    private final Map<String, Object> properties;

    public EnrichedDistribution(@Nullable String format, @Nullable DataService dataService,
                                @Nullable Map<String, Object> properties) {
        super();
        this.format = format;
        this.dataService = dataService;
        this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
    }

    public EnrichedDistribution(@NotNull Distribution distribution,
                                @Nullable Map<String, Object> properties) {
        super();
        this.format = distribution.getFormat();
        this.dataService = distribution.getDataService();
        this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
    }

    @Override
    public String getFormat() {
        return format;
    }

    @Override
    public DataService getDataService() {
        return dataService;
    }

    public Map<String, Object> getProperties() {
        return java.util.Collections.unmodifiableMap(properties);
    }

    public Map<String, Object> getMutableProperties() {
        return new HashMap<>(properties);
    }

    public EnrichedDistribution withProperty(@NotNull String key, @Nullable Object value) {
        properties.put(key, value);
        return this;
    }

    public EnrichedDistribution withProperties(@Nullable Map<String, Object> properties) {
        if (properties != null) {
            this.properties.putAll(properties);
        }
        return this;
    }

    public boolean hasProperties() {
        return !properties.isEmpty();
    }

    @Override
    public String toString() {
        return "EnrichedDistribution{" +
                "format='" + getFormat() + '\'' +
                ", dataService=" + getDataService() +
                ", properties=" + properties +
                '}';
    }

    public static final class Builder {
        private String format;
        private DataService dataService;
        private Map<String, Object> properties = new HashMap<>();

        private Builder() { }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder format(@Nullable String format) {
            this.format = format;
            return this;
        }

        public Builder dataService(@NotNull DataService dataService) {
            this.dataService = Objects.requireNonNull(dataService, "dataService cannot be null");
            return this;
        }

        public Builder properties(@Nullable Map<String, Object> properties) {
            this.properties = properties == null ? new HashMap<>() : new HashMap<>(properties);
            return this;
        }

        public Builder property(@NotNull String key, @Nullable Object value) {
            properties.put(key, value);
            return this;
        }

        public EnrichedDistribution build() {
            return new EnrichedDistribution(format, Objects.requireNonNull(dataService, "dataService is required"), properties);
        }
    }
}
