# Data Plane Data Consumption Metrics

This module provides automatic tracking and recording of data consumption metrics for data transfers through the EDC data plane. It captures telemetry information about each data request including response size, status codes, and trace context for billing and auditing purposes.

## Overview

The data consumption metrics extension monitors all outbound data responses from the data plane and records detailed consumption information. This enables accurate billing, usage tracking, auditing, and observability for data transfers. The metrics are correlated with contract agreements and distributed trace contexts.

## Architecture

The system uses JAX-RS container response filters to intercept and measure data plane responses:

```
Data Request → Data Plane Processing → Response Filter → Metrics Recording → Telemetry Store
                                            ↓
                                   Contract ID + Size + Status
```

## Components

### Extension Layer

#### `BillingConsumptionMetricsExtension`
The main extension that bootstraps the consumption metrics tracking:
- Registers the metrics publisher as a response filter on data APIs
- Integrates with both `PUBLIC` and `data` API contexts
- Injects required services (WebService, TelemetryRecordStore, Monitor, Telemetry)
- Provides the participant ID for metrics attribution

**Extension Name:** `"Billing Consumption Metrics"`

**API Contexts:**
- **PUBLIC** - For public-facing data endpoints
- **data** - For internal data plane endpoints

### Response Filter

#### `DataConsumptionMetricsPublisher`
A JAX-RS response filter that captures and records data consumption metrics:
- Implements `ContainerResponseFilter` to intercept all HTTP responses
- Extracts contract ID from request headers
- Calculates response payload size
- Captures HTTP status code
- Records distributed trace context
- Persists metrics to the telemetry record store

**Key Operations:**
1. Extract contract ID from `Contract-Id` header
2. Calculate response size (byte length of serialized entity)
3. Capture current trace context from telemetry
4. Create `DataConsumptionRecord` with all metrics
5. Save record to `TelemetryRecordStore`

### Required Headers

#### `Contract-Id` Header
**Required**: Yes  
**Format**: String  
**Purpose**: Links the data consumption to a specific contract agreement

## Data Model

### DataConsumptionRecord

The recorded metrics include:

| Field | Type | Description |
|-------|------|-------------|
| `contractId` | String | The contract agreement ID associated with the transfer |
| `responseSize` | Long | Size of the response payload in bytes |
| `responseStatusCode` | Integer | HTTP status code of the response (200, 404, etc.) |
| `participantId` | String | DID of the data provider (participant serving the data) |
| `traceContext` | Map | Distributed trace context for correlation |

## How It Works

### Request Flow

1. **Data Request Received**:
   ```
   GET /public/data/asset-123
   Headers:
     Contract-Id: contract-456
     traceparent: 00-...
   ```

2. **Data Processing**:
   ```
   Data plane processes request
   → Validates contract
   → Fetches data from backend
   → Prepares response
   ```

3. **Response Interception**:
   ```
   DataConsumptionMetricsPublisher.filter() called
   → Extracts Contract-Id header
   → Calculates response entity size
   → Captures HTTP status code
   → Gets trace context from telemetry
   ```

4. **Metrics Recording**:
   ```
   DataConsumptionRecord created with:
     - contractId: "contract-456"
     - responseSize: 1024 bytes
     - responseStatusCode: 200
     - participantId: "did:web:provider.example"
     - traceContext: {...}
   
   Record saved to TelemetryRecordStore
   ```

5. **Response Sent**:
   ```
   Original response sent to consumer
   (metrics recording is transparent)
   ```

## Dependencies

- **EDC Web SPI** - Web service and API context management
- **EDC Telemetry SPI** - Distributed tracing support
- **EDC Monitor** - Debug logging
- **Telemetry Agent SPI** - Data consumption record model and store interface
- **Jakarta REST** - JAX-RS container response filter
