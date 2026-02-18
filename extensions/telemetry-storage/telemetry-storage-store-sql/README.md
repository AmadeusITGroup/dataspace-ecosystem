# Telemetry Event Store SQL

This module provides a SQL-based persistence implementation for the Telemetry Event Store. It enables storing telemetry event records in a relational database instead of in-memory storage.

## Overview

The Telemetry Event Store SQL extension implements the `TelemetryEventStore` interface using SQL databases, providing persistent storage for telemetry events that capture data consumption metrics, transfer information, and audit trails. It supports PostgreSQL by default but can be extended to support other SQL databases.

## Architecture

```
Telemetry Storage API → SqlTelemetryEventStore → SQL Database (PostgreSQL)
                              ↓
                TelemetryEventStatements (SQL Templates)
                              ↓
                TransactionContext + QueryExecutor
```

## Components

### Extension Layer

#### `SqlTelemetryEventExtension`
The main extension that bootstraps the SQL store:
- Registers the SQL store implementation as a service provider
- Configures the datasource for telemetry event storage
- Bootstraps the database schema using `telemetry-event-schema.sql`
- Injects or defaults to PostgreSQL-specific SQL statements
- Integrates with EDC's transaction and query execution framework

**Dependencies:**
- `DataSourceRegistry` - Manages database connections
- `TransactionContext` - Handles transaction boundaries
- `TelemetryEventStatements` - SQL statement templates (optional, defaults to PostgreSQL)
- `TypeManager` - JSON serialization/deserialization
- `QueryExecutor` - Executes SQL queries and updates
- `SqlSchemaBootstrapper` - Initializes database schema

**Extension Name:** `"SQL Telemetry Event Store"`

### Storage Implementation

#### `SqlTelemetryEventStore`
SQL-based implementation of `TelemetryEventStore`:
- Extends `AbstractSqlStore` for common SQL operations
- Uses `TransactionContext` for ACID transaction management
- Implements all CRUD operations
- Maps SQL `ResultSet` rows to `TelemetryEvent` records
- Provides proper error handling with `EdcPersistenceException`

### SQL Statement Layer

#### `TelemetryEventStatements`
Interface defining SQL statement templates and table/column names:
- **Table**: `telemetry_event`
- **Columns**:
  - `id` - Primary key, unique identifier for the event
  - `contract_id` - The contract agreement ID associated with the transfer
  - `participant_did` - The DID of the participant (data provider)
  - `response_status_code` - HTTP response status code
  - `msg_size` - Size of the message/response in bytes
  - `csv_id` - Optional CSV file identifier for batch processing
  - `timestamp` - When the telemetry event occurred

#### `BaseSqlDialectStatements`
Base implementation of SQL statements with operator translation:
- Provides default implementations for all statement templates
- Uses `SqlOperatorTranslator` for query translation
- Generates parameterized SQL statements to prevent SQL injection
- Implements the statement templates using standard SQL syntax
- Creates dynamic queries using `SqlQueryStatement` with `TelemetryEventMapping`

#### `PostgresDialectStatements`
PostgreSQL-specific implementation:
- Extends `BaseSqlDialectStatements`
- Uses `PostgresqlOperatorTranslator` for PostgreSQL-specific query translation
- Handles PostgreSQL-specific operators and functions

### Query Translation

#### `TelemetryEventMapping`
Maps JSON-LD field names to database columns for query translation:
- `id` → `id`
- `contractId` → `contract_id`
- `participantId` → `participant_did`
- `responseStatusCode` → `response_status_code`
- `msgSize` → `msg_size`
- `csvId` → `csv_id`
- `timestamp` → `timestamp`

## Database Schema

### Schema Bootstrap

The schema is automatically created by the `telemetry-event-schema.sql` file during extension initialization.

**Schema File Location:**
```
src/main/resources/telemetry-event-schema.sql
```

## Transaction Management

All operations are wrapped in transactions via `TransactionContext`:
- **Automatic rollback** on errors
- **Consistent read/write operations**
- **Thread-safe concurrent access**
- **ACID compliance** (Atomicity, Consistency, Isolation, Durability)

## Dependencies

- **EDC SQL modules** - Query execution and transaction support
- **PostgreSQL JDBC driver** - For database connectivity (or your preferred driver)
- **Telemetry Storage SPI** - Store interface and data model
- **EDC SPI** - Core interfaces and result types
- **Jackson** - JSON serialization (via TypeManager)
