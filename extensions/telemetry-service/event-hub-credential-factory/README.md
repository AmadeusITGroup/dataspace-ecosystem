# Event Hub Credential Factory

This module provides credential generation functionality for Azure Event Hub integration in the Eclipse Dataspace Components (EDC) Telemetry Service. It enables automatic creation and refresh of credentials (SAS tokens or connection strings) for authenticating with Azure Event Hubs.

## Overview

The Event Hub Credential Factory extension implements the `TelemetryServiceCredentialFactory` interface to generate credentials for publishing telemetry data to Azure Event Hubs. It supports two authentication mechanisms:
1. **SAS Token** - Dynamically generated Shared Access Signature tokens with configurable validity
2. **Connection String** - Static connection strings retrieved from the vault

## Architecture

```
TelemetryServiceCredentialFactory
        ↓
EventHubCredentialFactoryExtension
        ↓
    Decision Point (eventHubUri != null?)
        ↓                        ↓
EventHubSasTokenFactory   EventHubConnectionStringFactory
        ↓                        ↓
    SAS Token              Connection String
```

## Components

### Extension Layer

#### `EventHubCredentialFactoryExtension`
The main service extension that provides credential factory selection:
- Implements `ServiceExtension` to integrate with EDC runtime
- Provides `TelemetryServiceCredentialFactory` as a service
- Decides which factory to use based on configuration
- If `eventHubUri` is configured → uses `EventHubSasTokenFactory`
- If `eventHubUri` is null → uses `EventHubConnectionStringFactory`

**Extension Name:** `"Event Hub Credential Factory"`

### Factory Implementations

#### `EventHubSasTokenFactory`
Dynamically generates SAS (Shared Access Signature) tokens for Event Hub authentication:
- Implements `TelemetryServiceCredentialFactory` interface
- Generates time-limited SAS tokens
- Retrieves shared access key from vault
- Calculates token expiration based on current time + validity period
- Encodes token components properly for URL usage

**Signing Process:**
1. Create string to sign: `{url-encoded-resource-uri}\n{expiry}`
2. Sign it
3. Base64 encode the signature
4. URL encode the signature
5. Construct full SAS token string

#### `EventHubConnectionStringFactory`
Retrieves static connection strings from the vault:
- Implements `TelemetryServiceCredentialFactory` interface
- Fetches connection string from vault using configured alias
- Returns pre-configured connection string with validity period

## How It Works

### SAS Token Flow

1. **Configuration Check**:
   ```
   Extension checks if eventHubUri is configured
   → If yes, creates EventHubSasTokenFactory
   ```

2. **Token Generation**:
   ```
   get() called
   → Calculate expiry: current_time + validity
   → Retrieve shared access key from vault
   → Create string to sign: "{encoded_uri}\n{expiry}"
   → Sign it
   → Base64 encode signature
   → URL encode components
   → Construct SAS token
   ```

3. **Token Return**:
   ```
   TokenRepresentation with:
     - token
     - expiresIn: validity (seconds)
     - type: SAS_TOKEN
   ```

### Connection String Flow

1. **Configuration Check**:
   ```
   Extension checks if eventHubUri is null
   → If null, creates EventHubConnectionStringFactory
   ```

2. **Credential Retrieval**:
   ```
   get() called
   → Retrieve connection string from vault
   → Validate it exists
   → Return with validity period
   ```

3. **Token Return**:
   ```
   TokenRepresentation with:
     - token 
     - expiresIn: validity (seconds)
     - type: CONNECTION_STRING
   ```

## Dependencies

- **EDC Telemetry Service SPI** - Credential factory interface
- **EDC Vault** - Secure secret storage
- **Java Crypto** - HMAC-SHA256 signing (javax.crypto)
- **Java Time** - Clock for timestamp generation
- **Java Net** - URL encoding utilities

## Related Components

- **Event Hub Telemetry Record Publisher** - Uses generated credentials to publish telemetry
- **Telemetry Service** - Orchestrates credential refresh and publishing
- **Azure Event Hub** - Destination for telemetry data

