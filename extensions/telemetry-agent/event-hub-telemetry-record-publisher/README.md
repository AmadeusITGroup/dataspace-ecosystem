# Event Hub Telemetry Record Publisher

This module provides an Azure Event Hub integration for publishing telemetry records from the Eclipse Dataspace Components (EDC) Telemetry Agent. It enables streaming of telemetry data (such as data consumption metrics, audit logs, and analytics) to Azure Event Hubs for real-time processing, storage, and analysis.

## Overview

The Event Hub Telemetry Record Publisher extension implements the `TelemetryRecordPublisher` interface using Azure Event Hubs as the messaging backbone. It provides a scalable, cloud-based solution for collecting and processing telemetry data from EDC deployments.

## Architecture

```
Telemetry Agent → TelemetryRecordPublisherFactory → EventHubTelemetryRecordPublisher → Azure Event Hub
                           ↓
                  Credential Management (SAS or Connection String)
                           ↓
                  EventHubProducerAsyncClient (Azure SDK)
```

## Components

### Extension Layer

#### `EventHubTelemetryRecordPublisherExtension`
The main service extension that bootstraps the Event Hub integration:
- Implements `ServiceExtension` to integrate with EDC runtime
- Provides `TelemetryRecordPublisherFactory` as a service
- Configures Event Hub connection parameters from settings
- Injects required services (TypeManager, Monitor)

**Extension Name:** `"EventHub Telemetry Extension"`

### Factory Layer

#### `EventHubTelemetryRecordPublisherFactory`
Factory implementation that creates Event Hub publisher clients:
- Implements `TelemetryRecordPublisherFactory` interface
- Creates `EventHubClientBuilder` with configured namespace and Event Hub name
- Creates `EventHubTelemetryRecordPublisher` instances per credential

### Publisher Layer

#### `EventHubTelemetryRecordPublisher`
The actual publisher that sends telemetry records to Azure Event Hub:
- Implements `TelemetryRecordPublisher` interface
- Uses `EventHubProducerAsyncClient` for asynchronous message publishing
- Serializes telemetry records to JSON using `TypeManager`

## How It Works

### Initialization Flow

1. **Extension Startup**:
   ```
   EventHubTelemetryRecordPublisherExtension loads
   → Reads configuration (namespace, Event Hub name)
   → Creates EventHubTelemetryRecordPublisherFactory
   → Registers factory as service
   ```

2. **Client Creation**:
   ```
   Telemetry Agent requests publisher
   → Provides credential (TokenRepresentation)
   → Factory extracts token and type
   → Creates EventHubProducerAsyncClient
   → Returns EventHubTelemetryRecordPublisher
   ```

3. **Publishing Flow**:
   ```
   TelemetryRecord → sendRecord()
   → Serialize to JSON
   → Create EventData
   → Send to Event Hub (blocking)
   → Return success/failure
   ```

4. **Shutdown**:
   ```
   close() called
   → Check if already closed (AtomicBoolean)
   → Close EventHubProducerAsyncClient
   → Log successful closure
   ```

## Telemetry Record Format

### JSON Serialization

Telemetry records are serialized to JSON before publishing:

```json
{
  "@type": "DataConsumptionRecord",
  "contractId": "contract-123",
  "responseSize": 1024,
  "responseStatusCode": 200,
  "participantId": "did:web:provider.example",
  "traceContext": {
    "traceparent": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
  },
  "timestamp": 1704067200000
}
```

### Event Data Structure

The JSON is wrapped in an Azure `EventData` [Object](https://learn.microsoft.com/en-us/java/api/com.azure.messaging.eventhubs.eventdata?view=azure-java-stable):

```java
new EventData(jsonString)
```

## Dependencies

- **Azure Event Hubs SDK** - `com.azure:azure-messaging-eventhubs`
- **EDC Telemetry Agent SPI** - Telemetry record interfaces
- **EDC SPI** - Core interfaces (TypeManager, Monitor, TokenRepresentation)
- **Jackson** - JSON serialization (via TypeManager)
