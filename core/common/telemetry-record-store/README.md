# Telemetry Record Store

This module provides an in-memory storage implementation for telemetry records for the Telemetry Agent.

## Overview

The Telemetry Record Store extension provides a default, thread-safe in-memory implementation for storing telemetry records. These records capture metrics about data consumption, transfers, and other operational events. The store supports state management, leasing, querying with complex filters, and CRUD operations.

## Architecture

```
Telemetry Agent → TelemetryRecordStore → InMemoryTelemetryRecordStore → ConcurrentHashMap
                         ↓
                  State Management + Leasing
                         ↓
                  Query Resolution (Reflection-based)
```

## Components

### Storage Implementation

#### `InMemoryTelemetryRecordStore`
In-memory, implementation of `TelemetryRecordStore`:
- Extends `InMemoryStatefulEntityStore` for state management and leasing capabilities
- Supports stateful entity lifecycle (states defined by `TelemetryRecordStates`)
- Enables complex querying with filtering, sorting, and pagination

**Constructor Parameters:**
- `leaseHolder` - Identifier for the entity holding leases (defaults to random UUID)
- `clock` - Clock for timestamp generation and lease expiration
- `criterionOperatorRegistry` - Registry for query operators and property lookups

### Query Resolution

#### `TelemetryRecordPropertyLookup`
Custom property lookup implementation for telemetry records:
- Implements `PropertyLookup` interface for query property resolution
- Enables querying nested properties within `TelemetryRecord.properties` map
- Supports multiple property access patterns for flexibility
- Falls back to reflection-based lookup for standard fields

**Property Access Patterns:**

1. **Direct Map Access**: `properties.key` → looks up `key` in properties map
2. **Quoted Access**: `properties.'key'` → alternative quoted format
3. **Standard Fields**: `id`, `state`, etc. → uses reflection

### Extension Layer

#### `TelemetryRecordStoreDefaultServicesExtension`
Service extension that provides the default in-memory store:
- Implements `ServiceExtension` to integrate with EDC runtime
- Provides `InMemoryTelemetryRecordStore` as default implementation
- Registers custom property lookup for enhanced query capabilities

**Extension Name:** `"Telemetry Record Store Default Services"`

## Data Model

### TelemetryRecord

The telemetry record entity (defined in SPI) typically includes:

```java
public class TelemetryRecord {
    String id;                          // Unique identifier
    int state;                          // Current state (see TelemetryRecordStates)
    String stateTimestamp;              // When state was last updated
    Map<String, Object> properties;     // Custom properties
    // Additional fields...
}
```

## Property Lookup Mechanism

### How It Works

1. **Query Parsing**: QuerySpec filter references property (e.g., "contractId")
2. **Property Lookup**: `TelemetryRecordPropertyLookup` invoked
3. **Map Search**: Checks `TelemetryRecord.properties` map for key
4. **Fallback**: If not in map, uses reflection on TelemetryRecord class
5. **Comparison**: Retrieved value compared against filter operand

### Supported Property Patterns

| Pattern | Example | Description |
|---------|---------|-------------|
| Direct | `contractId` | Looks up in properties map or field |
| Quoted | `'contractId'` | Alternative quoted format |
| Nested | `properties.contractId` | Explicit map access |
| Standard | `id`, `state` | Uses reflection for class fields |

## Dependencies

- **EDC Store Module** - Provides `InMemoryStatefulEntityStore` base class
- **EDC Query Module** - Query specification and resolution
- **EDC SPI** - Core interfaces and result types
- **Telemetry Agent SPI** - TelemetryRecord model and store interface

## Related Components

- **Telemetry Agent** - Uses this store for telemetry record management
- **Data Plane Metrics** - Generates telemetry records
- **Telemetry Service** - Coordinates telemetry collection and publishing

