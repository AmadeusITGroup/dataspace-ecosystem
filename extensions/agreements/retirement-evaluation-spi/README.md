# Agreement Retirement Evaluation SPI

This module defines the Service Provider Interface (SPI) for the Contract Agreement Retirement feature. This feature is not present on EDC itself and it provides the contracts and data models that allow extensions to implement and interact with agreement retirement functionality.

## Overview

The retirement evaluation SPI enables the retirement of contract agreements, preventing them from being used in transfer processes or policy evaluations. This module defines the interfaces and types needed to implement custom storage and service layers for agreement retirement.

## Components

### Service Interface

#### `AgreementsRetirementService`
The main service interface defining operations for agreement retirement lifecycle management:

- **`isRetired(String agreementId)`** - Checks if a contract agreement is currently retired
- **`findAll(QuerySpec querySpec)`** - Queries retired agreements with filtering and pagination support
- **`retireAgreement(AgreementsRetirementEntry entry)`** - Retires a contract agreement by storing a retirement entry
- **`reactivate(String contractAgreementId)`** - Reactivates a retired agreement by removing its retirement entry

### Storage Interface

#### `AgreementsRetirementStore`
An extension point interface defining the persistence layer for retirement entries:

- **`save(AgreementsRetirementEntry entry)`** - Persists a retirement entry, returning conflict error if already exists
- **`delete(String contractAgreementId)`** - Removes a retirement entry, returning not found error if missing
- **`findRetiredAgreements(QuerySpec querySpec)`** - Streams retirement entries matching the query specification

#### Error Templates
The store defines standardized error message templates:
- `NOT_FOUND_IN_RETIREMENT_TEMPLATE` - Agreement not found in retirement store
- `NOT_FOUND_IN_CONTRACT_AGREEMENT_TEMPLATE` - Agreement doesn't exist in the system
- `ALREADY_EXISTS_TEMPLATE` - Agreement is already retired

### Data Model

#### `AgreementsRetirementEntry`
The entity representing a retired contract agreement with the following properties:

- **`agreementId`** - The unique identifier of the retired contract agreement
- **`reason`** - The reason for retiring the agreement (required)
- **`agreementRetirementDate`** - Timestamp when the agreement was retired (auto-generated if not provided)

The entry extends `Entity` for EDC entity management features (versioning, timestamps, etc.).

#### JSON-LD Context
The entry uses standardized JSON-LD properties:
- `AR_ENTRY_TYPE` - `edc:AgreementsRetirementEntry`
- `AR_ENTRY_AGREEMENT_ID` - `edc:agreementId`
- `AR_ENTRY_REASON` - `dse:reason`
- `AR_ENTRY_RETIREMENT_DATE` - `dse:agreementRetirementDate`

## Dependencies

This SPI module depends on:
- EDC SPI modules for core interfaces (`ServiceResult`, `StoreResult`, `QuerySpec`, `Entity`)
- Jackson for JSON serialization

## Usage in Other Modules

This SPI is implemented by:
- **retirement-evaluation-core** - Provides default in-memory store and service implementation
- **retirement-evaluation-store-sql** - Provides SQL-based persistence

It can be consumed by:
- Policy validators
- Management API extensions
- Custom monitoring tools

