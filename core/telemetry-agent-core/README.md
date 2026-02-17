# Telemetry Agent Core

This module provides the core implementation of the Telemetry Agent based on the Eclipse Dataspace Components (EDC) framework. It orchestrates the collection, credential management, and publishing of telemetry records to external telemetry services (such as Azure Event Hub).

## Overview

The Telemetry Agent Core is responsible for:
- Managing the lifecycle of telemetry records through a state machine
- Automatically obtaining and refreshing credentials from a Telemetry Service
- Publishing telemetry records to external systems using time-limited credentials

## Architecture

```
TelemetryRecord → TelemetryAgent (State Machine) → TelemetryRecordPublisher → External Service (Event Hub)
                         ↓                                    ↑
                  TelemetryRecordStore              TelemetryServiceCredentialManager
                                                                ↓
                                                        TokenCache ← TelemetryServiceClient
                                                                           ↓
                                                                  Telemetry Service API
```

## Components

### State Machine

#### `TelemetryAgent`
The main orchestrator that manages telemetry record processing using a state machine:
- Extends `AbstractStateEntityManager` for state-based processing
- Processes telemetry records 
- Manages transitions between states (RECEIVED → SENT → optionally deleted)
- Creates and manages `TelemetryRecordPublisher` instances
- Integrates with distributed tracing via `Telemetry`

**State Processing:**
```
RECEIVED → Send to publisher → SENT (→ DELETE)
              ↓ (if failed)
         Break lease (retry later)
```

### Credential Management

#### `TelemetryServiceCredentialManager`
Manages automatic credential acquisition and refresh from the Telemetry Service:
- Runs as a background scheduled task
- Fetches credentials from `TelemetryServiceClient`
- Stores credentials in `TokenCache` for the agent to use
- Automatically refreshes credentials at half their TTL (Time To Live)
- Implements retry logic with configurable delays

**Lifecycle:**
```
Start → Fetch Credential → Cache → Schedule Next Refresh (at TTL/2) → Repeat
           ↓ (if failed)
      Retry after delay
```

#### `TokenCache`
Thread-safe cache for storing credential tokens:
- Uses `LockManager` with `ReentrantReadWriteLock` for concurrent access

**Operations:**
- `save(TokenRepresentation)` - Write lock, updates cached credential
- `get()` - Read lock, retrieves cached credential

### Client Implementation

#### `TelemetryServiceClient`
Interface for interacting with the Telemetry Service.

#### `TelemetryServiceClientImpl`
Concrete implementation that fetches credentials from the Telemetry Service:
- Resolves Telemetry Service endpoint via DID resolution
- Generates JWT access tokens for authentication
- Evaluates telemetry policy to determine required scopes
- Makes HTTP GET request to `/v1alpha/sas-token` endpoint
- Parses and returns `TokenRepresentation` (SAS token or connection string)

**Authentication Flow:**
1. Create JWT with claims (issuer, subject, expiration, etc.)
2. Evaluate telemetry policy to get required scopes
3. Obtain client credentials via `IdentityService`
4. Send GET request to Telemetry Service with Bearer token
5. Receive SAS token in response

**JWT Claims:**
- `iss` (issuer): Own DID
- `sub` (subject): Own DID
- `jti` (JWT ID): Random UUID
- `iat` (issued at): Current timestamp
- `exp` (expiration): Current time + 1 hour
- `aud` (audience): Authority DID
- `scope`: Scopes from policy evaluation (optional)

**DID Resolution:**
- Resolves authority DID to get DID document
- Finds service endpoint with type `CredentialApiService`
- Constructs full URL: `{serviceEndpoint}/v1alpha/sas-token`

### Policy Management

#### `TelemetryPolicy`
Interface for providing telemetry access policies (defined in SPI).

#### `TelemetryDefaultPolicy`
Default implementation that provides a permissive policy:
- Allows `telemetry:use` action

#### `TelemetryAgentDefaultExtension`
Provides a minimal default policy (empty policy, always allows):
- Marked with `@Provider(isDefault = true)`
- Returns empty policy (no restrictions)

### Policy Context

#### `RequestTelemetryPolicyContext`
Policy context for telemetry request scope evaluation:
- Extends `RequestPolicyContext` for outbound requests
- Scope: `TELEMETRY_REQUEST_SCOPE` = `"request.telemetry"`
- Carries `RequestScope.Builder` for dynamic scope extraction
- Used by policy engine to evaluate telemetry policies

**Purpose:**
- Enables policy-based scope determination
- Supports dynamic credential scope requirements
- Integrates with IATP (Identity and Trust Protocol)

### Extension Layer

#### `TelemetryAgentCoreExtension`
Main service extension that bootstraps the telemetry agent:
- Registers policy scope for telemetry requests
- Creates and configures `TelemetryAgent` state machine
- Creates and starts `TelemetryServiceCredentialManager`
- Provides `TelemetryServiceClient` as a service

**Extension Name:** `"Telemetry Agent Core"`

## How It Works

### End-to-End Flow

#### 1. Startup

```
Extension starts
→ Initialize TokenCache
→ Create TelemetryAgent
→ Create TelemetryServiceCredentialManager
→ Register policy scope
→ Start credential manager
→ Start telemetry agent
```

#### 2. Credential Acquisition

```
CredentialManager scheduled task runs
→ TelemetryServiceClient.fetchCredential()
→ Resolve authority DID
→ Create JWT token
→ Evaluate policy for scopes
→ Request credentials from authority
→ Cache received SAS token
→ Schedule next refresh (at TTL/2)
```

#### 3. Telemetry Record Processing

```
TelemetryAgent state machine runs
→ Query records in RECEIVED state
→ Get credentials from cache
→ Create TelemetryRecordPublisher
→ Send records to publisher
→ On success: transition to SENT
→ On failure: break lease for retry
```

#### 4. Credential Refresh

```
Half of token TTL elapses
→ CredentialManager wakes up
→ Fetches new credential
→ Updates cache
→ TelemetryAgent uses new credential
→ Previous publisher closed
→ New publisher created
```

### State Machine Details

#### States

- **RECEIVED**: New telemetry record, ready to be published
- **SENT**: Successfully published to external service

#### Processors

**`receivedRecordsProcessor`:**
- Queries records in RECEIVED state
- Creates publisher with cached credentials
- Sends records via publisher
- Transitions successful records to SENT
- Breaks lease on failed records for retry

## Dependencies

- **EDC State Machine** - State entity management and processing
- **EDC HTTP Client** - HTTP communication with authority
- **EDC Identity Service** - JWT token generation
- **EDC Policy Engine** - Policy evaluation for scopes
- **EDC DID Resolution** - Authority DID resolution
- **Telemetry Agent SPI** - Interfaces and data models

## Related Components

- **Telemetry Record Store** - Persistent storage for telemetry records
- **Event Hub Telemetry Publisher** - Azure Event Hub publishing implementation
- **Telemetry Service Credential API** - Authority service for credential issuance
- **Data Plane Metrics** - Generates telemetry records from data transfers

