/*
 *  Copyright (c) 2024 Amadeus SA
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus SA - initial implementation
 *
 */

package org.eclipse.edc.eonax.dataplane.billing;

import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.telemetry.Telemetry;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.eonax.edc.spi.telemetryagent.TelemetryRecordStore;

public class BillingConsumptionMetricsExtension implements ServiceExtension {

    private static final String DATA_CONTEXT = "data";

    @Inject
    private WebService webService;

    @Inject
    private TelemetryRecordStore telemetryRecordStore;

    @Inject
    private Monitor monitor;

    @Inject
    private Telemetry telemetry;


    @Override
    public void initialize(ServiceExtensionContext context) {
        var publisher = new DataConsumptionMetricsPublisher(telemetryRecordStore, monitor, telemetry, context.getParticipantId());
        webService.registerResource(ApiContext.PUBLIC, publisher);
        webService.registerResource(DATA_CONTEXT, publisher);
    }

    @Override
    public String name() {
        return "Billing Consumption Metrics";
    }

}

