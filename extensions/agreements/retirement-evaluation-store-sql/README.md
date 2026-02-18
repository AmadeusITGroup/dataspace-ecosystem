# Agreement Retirement Evaluation SQL Store

This module provides a SQL-based persistence implementation for the Contract Agreement Retirement feature in the Eclipse Dataspace Components (EDC) framework. It enables storing retired agreement entries in a relational database instead of in-memory storage.

## Overview

This extension implements the `AgreementsRetirementStore` interface using SQL databases, providing persistent storage for retired contract agreements. It supports PostgreSQL by default but can be extended to support other SQL databases.

## Components

### Extension Layer

#### `SqlAgreementsRetirementStoreExtension`
The main extension that:
- Bootstraps the SQL schema using `schema.sql`
- Configures the datasource for agreement retirement storage
- Provides the SQL store implementation as a service
- Injects or defaults to PostgreSQL statements

### Storage Implementation

#### `SqlAgreementsRetirementStore`
SQL-based implementation of `AgreementsRetirementStore` that:
- Extends `AbstractSqlStore` for common SQL operations
- Uses `TransactionContext` for transaction management
- Implements thread-safe CRUD operations
- Maps SQL `ResultSet` rows to `AgreementsRetirementEntry` objects

### SQL Statement Layer

#### `SqlAgreementsRetirementStatements`
Interface defining SQL statement templates and table/column names:
- **Table**: `edc_agreement_retirement`
- **Columns**:
  - `contract_agreement_id` - Primary key, the agreement ID
  - `reason` - The reason for retirement
  - `agreement_retirement_date` - Timestamp of retirement

#### `PostgresAgreementRetirementStatements`
PostgreSQL-specific implementation that:
- Generates parameterized SQL statements
- Implements the statement templates using PostgreSQL syntax
- Creates dynamic queries using `SqlQueryStatement` with `AgreementRetirementMapping`

### Query Translation

#### `AgreementRetirementMapping`
Maps JSON-LD field names to database columns for query translation:
- `agreementId` → `contract_agreement_id`
- `reason` → `reason`
- `agreementRetirementDate` → `agreement_retirement_date`

### Runtime Behavior

Once loaded, this extension automatically replaces the default in-memory store:
1. The SQL schema is bootstrapped on startup
2. All retirement operations are persisted to the database
3. Queries support filtering, sorting, and pagination
4. Transaction management ensures data consistency

## Dependencies

- **retirement-evaluation-spi** - Defines the store interface and data model
- **EDC SQL modules** - Provides query execution and transaction support
- **PostgreSQL JDBC driver** - For database connectivity (or your preferred database driver)

## Transaction Management

All operations are wrapped in transactions via `TransactionContext`:
- Automatic rollback on errors
- Consistent read/write operations
- Thread-safe concurrent access

