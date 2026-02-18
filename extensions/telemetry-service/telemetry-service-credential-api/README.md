# Telemetry Service Credential API

This module provides a REST API for generating SAS (Shared Access Signature) tokens for the Telemetry Service. It enables authorized clients to obtain time-limited credentials for publishing telemetry records to Azure Event Hubs or other telemetry backends.

## Overview

The Telemetry Service Credential API extension exposes an HTTP endpoint that allows authenticated clients to exchange their authorization tokens for SAS tokens. These SAS tokens can then be used to authenticate when publishing telemetry data, providing a secure and time-limited credential mechanism.

## Architecture

```
Client → Credential API (port 8181) → Authorization Check → TelemetryService → SAS Token Generation
                ↓
         Bearer Token / API Key
```

## Components

### Extension Layer

#### `TelemetryServiceCredentialApiExtension`
The main service extension that bootstraps the credential API:
- Registers the credential API REST controller with the web service
- Configures the API endpoint on a dedicated port and path
- Integrates with the `credential` API context
- Registers port mapping for the credential API

**Extension Name:** `"Credential API"`

**API Context:** `credential`

### REST API Layer

#### `TelemetryServiceCredentialApiController`
REST controller that handles SAS token generation requests:
- **Path**: `/v1alpha/sas-token`
- **Method**: GET
- **Authentication**: Requires Bearer token or API key in `Authorization` header
- **Response**: Returns `TokenRepresentation` containing the SAS token

**Process Flow:**
1. Extracts authorization token from `Authorization` header
2. Validates that the token is present
3. Calls `TelemetryService.createSasToken()` with the authorization token
4. Returns the generated SAS token to the client

#### `TelemetryServiceCredentialApi`
OpenAPI interface defining the credential API contract:
- Documents the SAS token generation endpoint
- Defines security schemes (Bearer token and API key authentication)
- Provides operation descriptions and response codes
- Categorized under "Credential API" tag


## How It Works

### End-to-End Flow

1. **Client Authentication**:
   ```
   Client has JWT token or API key
   → Makes request to credential API
   → Includes token in Authorization header
   ```

2. **Token Validation**:
   ```
   TelemetryServiceCredentialApiController receives request
   → Validates Authorization header is present
   → Wraps token in TokenRepresentation
   ```

3. **SAS Token Generation**:
   ```
   TelemetryService.createSasToken() called
   → Validates authorization token
   → Generates SAS token using credential factory
   → Returns TokenRepresentation with SAS token
   ```

4. **Response**:
   ```
   Controller returns TokenRepresentation
   → Client receives SAS token
   → Client can use SAS token for telemetry publishing
   ```

## Dependencies

- **EDC Telemetry Service SPI** - Telemetry service interface
- **EDC Web SPI** - Web service registration and error handling
- **EDC Runtime** - Service extension framework
- **Jakarta REST** - JAX-RS annotations
- **Swagger/OpenAPI** - API documentation

## Related Components

- **Event Hub Credential Factory** - Generates SAS tokens
- **Telemetry Service** - Orchestrates credential generation
- **Event Hub Telemetry Record Publisher** - Uses generated tokens to publish telemetry
- **Telemetry Agent** - Consumes this API for credential refresh

