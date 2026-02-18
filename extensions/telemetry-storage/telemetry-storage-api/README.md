# Telemetry Storage API

This module provides a REST API for storing and managing telemetry events in the Eclipse Dataspace Components (EDC) ecosystem. It enables external systems to submit telemetry data for data consumption tracking, auditing, billing, and analytics purposes.

## Overview

The Telemetry Storage API extension exposes HTTP endpoints that allow authenticated clients to submit telemetry events. These events capture information about data transfers, including contract IDs, participant identifiers, response codes, message sizes, and timestamps. The API provides a centralized repository for collecting telemetry data from distributed EDC deployments.

## Architecture

```
External System → Telemetry Storage API → TelemetryEventStore → Storage (In-Memory/SQL)
                         ↓
                  Bearer Token / API Key Authentication
```

## Components

### Extension Layer

#### `TelemetryStorageCoreExtension`
The main service extension that bootstraps the telemetry storage API:
- Registers the `TelemetryStorageApiController` with the web service
- Integrates with EDC's web service framework
- Wires up the telemetry event store dependency

**Extension Name:** `"Telemetry Storage Service Core"`

#### `TelemetryServiceDefaultServicesExtension`
Provides default service implementations:
- Registers `InMemoryTelemetryEventStore` as default store

**Extension Name:** `"Telemetry Storage Default Services"`

### REST API Layer

#### `TelemetryStorageApiController`
REST controller that handles telemetry event submissions:
- **Path**: `/telemetry-events`
- **Method**: POST
- **Content Type**: `application/json`
- **Authentication**: Requires Bearer token or API key

**Process Flow:**
1. Receives `TelemetryEventDto` in request body
2. Generates unique ID for the event (UUID)
3. Converts DTO to domain entity (`TelemetryEvent`)
4. Saves event to `TelemetryEventStore`
5. Returns HTTP 201 Created on success

**Error Handling:**
- **400 Bad Request**: Malformed request body
- **409 Conflict**: Event with same ID already exists
- **500 Internal Server Error**: Storage operation failed

#### `TelemetryStorageAdminApi`
OpenAPI interface defining the storage API contract:
- Documents the telemetry event submission endpoint
- Defines security schemes (Bearer token and API key authentication)
- Provides operation descriptions and response codes
- Categorized under "Telemetry Storage API" tag

### Data Transfer Object

#### `TelemetryEventDto`
Lightweight DTO for API request serialization:
- **id**: Unique identifier (ignored on create, auto-generated)
- **contractId**: Contract agreement ID associated with the data transfer
- **participantId**: DID of the participant (data provider)
- **responseStatusCode**: HTTP response status code (e.g., 200, 404)
- **msgSize**: Size of the message/response in bytes
- **csvId**: Optional CSV file identifier
- **timestamp**: When the telemetry event occurred

### Storage Layer

#### `TelemetryEventStore` (Interface)
Defines the storage contract for telemetry events (from SPI module).

**Operations:**
- `query(QuerySpec)` - Query events with filtering and pagination
- `findById(String)` - Retrieve single event by ID
- `save(TelemetryEvent)` - Create new telemetry event
- `deleteById(String)` - Remove event by ID

#### `InMemoryTelemetryEventStore`
Default in-memory implementation of the store:
- Supports querying with `QuerySpec` and `ReflectionBasedQueryResolver`
- Prevents duplicate event IDs
- Provides CRUD operations

## Data Model

### TelemetryEvent Entity

```java
public record TelemetryEvent(
    String id,                    // Auto-generated UUID
    String contractId,            // Contract agreement ID
    String participantId,        // Participant DID
    int responseStatusCode,       // HTTP status code
    int responseSize,                  // Message size in bytes
    Integer csvId,                // Optional CSV identifier
    Timestamp timestamp           // Event timestamp
) {}
```

### Field Descriptions

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `id` | String | Unique event identifier (auto-generated) | `"550e8400-e29b-41d4-a716-446655440000"` |
| `contractId` | String | Associated contract agreement ID | `"contract-123"` |
| `participantDid` | String | DID of the data provider | `"did:web:provider.example.com"` |
| `responseStatusCode` | int | HTTP response status code | `200`, `404`, `500` |
| `msgSize` | int | Size of the data transfer in bytes | `1024`, `2048576` |
| `csvId` | Integer | Optional CSV file identifier | `42`, `null` |
| `timestamp` | Timestamp | When the event occurred | `"2024-01-15T14:30:00Z"` |

## Dependencies

- **EDC Telemetry Storage SPI** - Store interface and data model
- **EDC Web SPI** - Web service registration and error handling
- **EDC SPI** - Query specification and store result types
- **EDC Runtime** - Service extension framework
- **Jackson** - JSON serialization/deserialization
- **Jakarta REST** - JAX-RS annotations

## Related Components

- **Data Plane Data Consumption Metrics** - Generates telemetry events
- **Telemetry Agent** - May consume this API
- **Event Hub Telemetry Publisher** - Alternative telemetry destination
- **Billing Service** - Consumes telemetry data for billing

