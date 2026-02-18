# Agreements Retirement Evaluation API Extension

## Overview

This extension provides a REST API for managing the lifecycle of retired contract agreements in the Eclipse Dataspace Components (EDC) framework. It allows participants to retire active contract agreements, reactivate retired agreements, and query the list of all retired agreements.

This API provides programmatic control over agreement retirement without permanently deleting the agreement data.

## Components

### Extension

#### `AgreementsRetirementApiExtension`
- **Type**: Service Extension
- **Extension Name**: "Contract Agreement Retirement API"
- **Purpose**: Bootstraps and configures the retirement API
- **Configuration**:
  - Registers JSON-LD transformers for retirement entries
  - Configures the Management API context
  - Registers the REST controller
- **Transformers Registered**:
  - `JsonObjectFromAgreementRetirementTransformer`: Converts retirement entries to JSON
  - `JsonObjectToAgreementsRetirementEntryTransformer`: Converts JSON to retirement entries

### API Layer

#### `AgreementsRetirementApiV3`
- **Type**: Interface
- **Purpose**: Defines the REST API contract for agreement retirement management
- **Base Path**: `/v3/contractagreements/retirements`
- **Security**: Supports both Bearer token (JWT) and API key authentication
- **Operations**:
  1. **Query Retired Agreements**: POST `/request` - Returns filtered list of retired agreements
  2. **Reactivate Agreement**: DELETE `/{agreementId}` - Removes agreement from retired list
  3. **Retire Agreement**: POST `/` - Retires an active agreement

#### `AgreementsRetirementApiV3Controller`
- **Type**: REST Controller
- **Purpose**: Implements the retirement API endpoints
- **Path**: `/v3/contractagreements/retirements`
- **Content Type**: `application/json`

##### Endpoints

**1. Get All Retired Agreements**
- **Method**: POST
- **Path**: `/request`
- **Body**: Optional `QuerySpec` for filtering/pagination
- **Response**: `JsonArray` of retired agreement entries
- **Status Codes**:
  - `200 OK`: Successfully retrieved retired agreements
  - `400 Bad Request`: Malformed query specification

**2. Reactivate Retired Agreement**
- **Method**: DELETE
- **Path**: `/{agreementId}`
- **Parameter**: Agreement ID to reactivate
- **Response**: No content on success
- **Status Codes**:
  - `204 No Content`: Agreement successfully reactivated
  - `404 Not Found`: Agreement ID not found in retired list
  - `400 Bad Request`: Invalid agreement ID

**3. Retire Agreement**
- **Method**: POST
- **Path**: `/`
- **Body**: `AgreementsRetirementEntry` with agreement ID and reason
- **Response**: No content on success
- **Status Codes**:
  - `204 No Content`: Agreement successfully retired
  - `409 Conflict`: Agreement is already retired
  - `400 Bad Request`: Malformed request body

### Transformers

#### `JsonObjectFromAgreementRetirementTransformer`
- **Type**: JSON-LD Transformer
- **Direction**: `AgreementsRetirementEntry` → `JsonObject`
- **Purpose**: Serializes retirement entries to JSON for API responses
- **Fields Mapped**:
  - `agreementId`: The contract agreement identifier
  - `reason`: Human-readable reason for retirement
  - `agreementRetirementDate`: Timestamp when the agreement was retired

#### `JsonObjectToAgreementsRetirementEntryTransformer`
- **Type**: JSON-LD Transformer
- **Direction**: `JsonObject` → `AgreementsRetirementEntry`
- **Purpose**: Deserializes JSON requests to retirement entry objects
- **Fields Mapped**:
  - `agreementId`: The contract agreement identifier
  - `reason`: Human-readable reason for retirement

## Architecture

```
┌─────────────────────┐
│  REST Client        │
└──────────┬──────────┘
           │
           ↓
┌───────────────────────────────────┐
│ AgreementsRetirementApiV3         │
│ Controller                        │
│  - Validates requests             │
│  - Transforms JSON ↔ Objects      │
│  - Delegates to service           │
└──────────┬────────────────────────┘
           │
           ↓
┌───────────────────────────────────┐
│ AgreementsRetirementService       │
│  - Business logic                 │
│  - Validation                     │
│  - State management               │
└───────────────────────────────────┘
```

## Validation

The API validates all incoming requests using the `JsonObjectValidatorRegistry`:
- **QuerySpec validation**: Ensures valid offset, limit, and sort parameters
- **Retirement entry validation**: Ensures agreement ID and reason are provided
- **JSON-LD structure**: Validates proper JSON-LD context and structure

Failed validations result in `400 Bad Request` with detailed error information.

## Dependencies

- **EDC Core**: ServiceExtension, Monitor, TypeTransformerRegistry
- **EDC Web SPI**: WebService, ApiContext
- **EDC Validator SPI**: JsonObjectValidatorRegistry
- **EDC JSON-LD**: JSON-LD transformation
- **EDC Agreements**: AgreementsRetirementService (SPI)
- **JAX-RS**: REST endpoint implementation
- **Jakarta JSON**: JSON processing

