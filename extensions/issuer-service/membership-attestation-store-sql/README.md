# Membership Attestation Store SQL

This module provides a SQL-based persistence implementation for the Membership Attestation Store in the Eclipse Dataspace Components (EDC) Issuer Service. It enables storing membership attestation records in a relational database instead of in-memory storage.

## Overview

The Membership Attestation Store SQL extension implements the `MembershipAttestationStore` interface using SQL databases, providing persistent storage for membership attestations that link participants to organizations or dataspaces they are members of. It supports PostgreSQL by default but can be extended to support other SQL databases.

## Architecture

```
Membership Attestation API → SqlMembershipAttestationStore → SQL Database (PostgreSQL)
                                    ↓
                      MembershipAttestationStatements (SQL Templates)
                                    ↓
                      TransactionContext + QueryExecutor
```

## Components

### Extension Layer

#### `SqlMembershipAttestationStoreExtension`
The main extension that bootstraps the SQL store:
- Registers the SQL store implementation as a service provider
- Configures the datasource for membership attestation storage
- Bootstraps the database schema using `membership-attestation-schema.sql`
- Integrates with EDC's transaction and query execution framework

**Dependencies:**
- `DataSourceRegistry` - Manages database connections
- `TransactionContext` - Handles transaction boundaries
- `MembershipAttestationStatements` - SQL statement templates (optional, defaults to PostgreSQL)
- `TypeManager` - JSON serialization/deserialization
- `QueryExecutor` - Executes SQL queries and updates
- `SqlSchemaBootstrapper` - Initializes database schema

**Extension Name:** `"SQL Membership Attestation Store"`

### Storage Implementation

#### `SqlMembershipAttestationStore`
SQL-based implementation of `MembershipAttestationStore`:
- Extends `AbstractSqlStore` for common SQL operations
- Uses `TransactionContext` for ACID transaction management
- Implements all CRUD operations
- Maps SQL `ResultSet` rows to `MembershipAttestation` records

### SQL Statement Layer

#### `MembershipAttestationStatements`
Interface defining SQL statement templates and table/column names:
- **Table**: `membership_attestation`
- **Columns**:
  - `id` - Primary key, unique identifier for the attestation
  - `holder_id` - The DID of the participant who holds the membership
  - `name` - The name of the organization or dataspace
  - `membership_type` - The type/level of membership
  - `membership_start_date` - Timestamp when the membership started

#### `BaseSqlDialectStatements`
Base implementation of SQL statements with operator translation:
- Provides default implementations for all statement templates
- Uses `SqlOperatorTranslator` for query translation
- Generates parameterized SQL statements to prevent SQL injection
- Implements the statement templates using standard SQL syntax
- Creates dynamic queries using `SqlQueryStatement` with `MembershipAttestationMapping`

#### `PostgresDialectStatements`
PostgreSQL-specific implementation:
- Extends `BaseSqlDialectStatements`
- Uses `PostgresqlOperatorTranslator` for PostgreSQL-specific query translation
- Handles PostgreSQL-specific operators and functions

### Query Translation

#### `MembershipAttestationMapping`
Maps JSON-LD field names to database columns for query translation:
- `id` → `id`
- `holderId` → `holder_id`
- `membershipType` → `membership_type`
- `membershipStartDate` → `membership_start_date`
- `name` → `name`

## Database Schema

### Schema Bootstrap

The schema is automatically created by the `membership-attestation-schema.sql` file during extension initialization.

**Schema File Location:**
```
src/main/resources/membership-attestation-schema.sql
```

## Transaction Management

All operations are wrapped in transactions via `TransactionContext`:
- **Automatic rollback** on errors
- **Consistent read/write operations**
- **Thread-safe concurrent access**
- **ACID compliance** (Atomicity, Consistency, Isolation, Durability)

## Security Considerations

### SQL Injection Prevention

- All queries use **parameterized statements**
- No string concatenation for query construction
- `QueryExecutor` handles parameter binding safely

### Data Validation

- Null checks on all input parameters
- Existence checks before update/delete operations
- Proper exception handling for constraint violations

## Dependencies

- **EDC SQL modules** - Query execution and transaction support
- **PostgreSQL JDBC driver** - For database connectivity (or your preferred driver)
- **Membership Attestation SPI** - Store interface and data model
- **EDC SPI** - Core interfaces and result types
- **Jackson** - JSON serialization (via TypeManager)
