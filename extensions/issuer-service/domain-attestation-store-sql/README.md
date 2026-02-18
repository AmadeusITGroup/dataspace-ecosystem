# Domain Attestation Store SQL

This module provides a SQL-based persistence implementation for the Domain Attestation Store in the Eclipse Dataspace Components (EDC) Issuer Service. It enables storing domain attestation records in a relational database instead of in-memory storage.

## Overview

The Domain Attestation Store SQL extension implements the `DomainAttestationStore` interface using SQL databases, providing persistent storage for domain attestations that link participants to the domains they belong to.

## Architecture

```
Domain Attestation API → SqlDomainAttestationStore → SQL Database (PostgreSQL)
                               ↓
                    DomainAttestationStatements (SQL Templates)
                               ↓
                    TransactionContext + QueryExecutor
```

## Components

### Extension Layer

#### `SqlDomainAttestationStoreExtension`
The main extension that bootstraps the SQL store:
- Registers the SQL store implementation as a service provider
- Configures the datasource for domain attestation storage
- Bootstraps the database schema using `domain-attestation-schema.sql`
- Injects or defaults to PostgreSQL-specific SQL statements
- Integrates with EDC's transaction and query execution framework

### Storage Implementation

#### `SqlDomainAttestationStore`
SQL-based implementation of `DomainAttestationStore`:
- Extends `AbstractSqlStore` for common SQL operations
- Uses `TransactionContext` for ACID transaction management
- Implements all CRUD operations
- Maps SQL `ResultSet` rows to `DomainAttestation` records

### SQL Statement Layer

#### `DomainAttestationStatements`
Interface defining SQL statement templates and table/column names:
- **Table**: `domain_attestation`
- **Columns**:
  - `id` - Primary key, unique identifier for the attestation
  - `holder_id` - The DID of the participant who controls the domain
  - `domain` - The domain being attested (e.g., `route`)


#### `DomainAttestationBaseSqlDialectStatements`
Base implementation of SQL statements with operator translation:
- Provides default implementations for all statement templates
- Uses `SqlOperatorTranslator` for query translation
- Generates parameterized SQL statements to prevent SQL injection
- Implements the statement templates using standard SQL syntax
- Creates dynamic queries using `SqlQueryStatement` with `DomainAttestationMapping`

#### `DomainAttestationPostgresDialectStatements`
PostgreSQL-specific implementation:
- Extends `DomainAttestationBaseSqlDialectStatements`
- Uses `PostgresqlOperatorTranslator` for PostgreSQL-specific query translation
- Handles PostgreSQL-specific operators and functions
- Optimized for PostgreSQL performance characteristics

#### `DomainAttestationMapping`
Maps JSON-LD field names to database columns for query translation:
- `id` → `id`
- `holderId` → `holder_id`
- `domain` → `domain`

## Database Schema

### Schema Bootstrap

The schema is automatically created by the `domain-attestation-schema.sql` file during extension initialization.

**Schema File Location:**
```
src/main/resources/domain-attestation-schema.sql
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
- **Domain Attestation SPI** - Store interface and data model
- **EDC SPI** - Core interfaces and result types
- **Jackson** - JSON serialization (via TypeManager)

