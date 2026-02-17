# Telemetry Agent SPI

This module defines the Service Provider Interface (SPI) for the Telemetry Agent in the Eclipse Dataspace Components (EDC) ecosystem. It provides the contracts and data models for collecting, storing, and publishing telemetry records to external services.

## Overview

The Telemetry Agent SPI enables:
- **Telemetry Record Management**: State-based lifecycle management of telemetry records
- **Data Collection**: Capturing data consumption metrics and other telemetry data
- **External Publishing**: Publishing telemetry records to remote services (e.g., Azure Event Hub)
- **Credential Management**: Automatic credential acquisition for authenticated publishing

## Components

### Core Interfaces

#### `TelemetryRecordStore`
Central storage interface for telemetry records with state management:
- Extends `StateEntityStore<TelemetryRecord>` for state-based processing
- Provides CRUD operations with proper error handling
- Supports querying with `QuerySpec` for filtering and pagination
- **Extension Point**: Can be implemented by different storage backends

#### `TelemetryServiceClient`
Interface for fetching credentials from the Telemetry Service:
- **Functional interface** for credential acquisition
- Returns `Result<TokenRepresentation>` containing SAS tokens or connection strings
- Used by the agent to obtain time-limited publishing credentials
- **Extension Point**: Can be implemented for different telemetry service backends

#### `TelemetryRecordPublisher`
Publisher interface for sending telemetry records to remote services:
- Extends `AutoCloseable` for proper resource management

#### `TelemetryRecordPublisherFactory`
Factory interface for creating publisher instances:
- **Functional interface** for creating publishers
- Creates publishers with specific credentials (`TokenRepresentation`)
- Enables credential rotation by creating new publishers as needed
- **Extension Point**: Can be implemented for different publisher types

### Data Models

#### `TelemetryRecord`
Base class for all telemetry records:
- Extends `StatefulEntity<TelemetryRecord>` for state machine support
- Contains flexible properties map for extensible data storage
- Supports distributed tracing via trace context

#### `DataConsumptionRecord`
Specialized telemetry record for data consumption tracking:
- Extends `TelemetryRecord` with specific properties for data transfer metrics

### Enumerations

#### `TelemetryRecordStates`
Defines the state machine for telemetry record processing:
- **RECEIVED (100)**: New record, ready to be published
- **SENT (200)**: Successfully published to external service

**Utility Methods:**
- `code()` - Returns numeric state code
- `from(int code)` - Converts numeric code back to enum, returns null if not found

#### `TelemetryRecordTypes`
Defines available telemetry record types:
- **DATA_CONSUMPTION**: "DataConsumption" - For tracking data transfer metrics

**Utility Methods:**
- `type()` - Returns string type identifier
- `from(String type)` - Converts string type to enum, returns null if not found

## State Machine Flow

```
RECEIVED (100) → Publishing Process → SENT (200)
     ↓                    ↓
   Ready to           Published
   Publish            Successfully
```

**State Transitions:**
1. Records start in `RECEIVED` state (auto-assigned by builder if state is 0)
2. Telemetry agent processes records in `RECEIVED` state
3. After successful publishing via publisher, records transition to `SENT` via `transitionToCompleted()`

## Dependencies

- **EDC SPI Core** - Entity framework (`StatefulEntity`), query specifications (`QuerySpec`), result types (`StoreResult`, `Result`)
- **Jackson** - JSON serialization/deserialization annotations
- **JetBrains Annotations** - Nullability annotations (`@Nullable`, `@NotNull`)

## Related Components

- **Telemetry Agent Core** - Implements these interfaces with state machine orchestration
- **Telemetry Record Store** - Provides default in-memory store implementation
- **Event Hub Telemetry Publisher** - Azure Event Hub publishing implementation
- **Data Plane Metrics** - Generates telemetry records from data transfers
- **Telemetry Service** - Provides credential generation and issuing
