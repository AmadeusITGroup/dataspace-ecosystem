# Telemetry Service Core

This module provides the core implementation of the Telemetry Service based on the Eclipse Dataspace Components (EDC) framework. It acts as a credential issuing authority that validates incoming token requests and issues SAS (Shared Access Signature) tokens or connection strings for telemetry agents to publish telemetry data to external systems (e.g., Azure Event Hub).

## Overview

The Telemetry Service Core is responsible for:
- **Token Validation**: Verifying JWT tokens from telemetry agents
- **Policy Evaluation**: Ensuring requests comply with telemetry access policies
- **Credential Issuance**: Generating SAS tokens or connection strings for authorized agents
- **Authorization**: Performing participant identity verification and policy enforcement

## Architecture

```
Telemetry Agent → JWT Token → TelemetryService → Token Validation → Policy Evaluation → Credential Factory → SAS Token
                                      ↓                    ↓                   ↓
                          TelemetryServiceTokenValidator  PolicyEngine  TelemetryServiceCredentialFactory
```

## Components

### Service Layer

#### `TelemetryService` (Interface - from SPI)
Defines the contract for the telemetry service.

#### `TelemetryServiceImpl`
Core implementation of the telemetry service that orchestrates the credential issuance flow:
- Validates incoming JWT tokens using `TelemetryServiceTokenValidator`
- Evaluates access policies using `PolicyEngine` with `TelemetryPolicyContext`
- Issues credentials via `TelemetryServiceCredentialFactory` upon successful authorization

**Process Flow:**
1. Receive JWT token from telemetry agent
2. Validate token and extract participant identity
3. Evaluate telemetry policy against participant
4. Generate and return SAS token/connection string

### Token Validation Layer

#### `TelemetryServiceTokenValidator` (Interface - from SPI)
Defines the contract for token validation.

#### `TelemetryServiceTokenValidatorImpl`
Implements JWT token validation and participant identity extraction:
- Evaluates request policy to determine required credential scopes
- Verifies JWT token signature and claims using `IdentityService`
- Extracts participant identity using dataspace profile context
- Creates `ParticipantAgent` with verified claims and identity

**Validation Steps:**
1. **Policy Evaluation**: Create request policy context and evaluate policy to extract required scopes
2. **Scope Extraction**: Build request scope from policy evaluation
3. **Token Verification**: Verify JWT signature, expiration, and claims using `IdentityService`
4. **Identity Extraction**: Extract participant DID from token claims using protocol-specific extraction function
5. **Agent Creation**: Create `ParticipantAgent` with verified identity and claims

### Policy Management

#### `TelemetryPolicy` (Interface - from SPI)
Provides the telemetry access policy.

#### `TelemetryServiceDefaultExtension`
Provides default implementations for optional dependencies:
- **Default Policy**: Empty policy (allows all requests)
- **Default DataspaceProfileContextRegistry**: Basic `DataspaceProfileContextRegistryImpl` without protocol mappings

### Extension Layer

#### `TelemetryServiceCoreExtension`
Main service extension that bootstraps the telemetry service:
- Registers policy scopes (`TELEMETRY_REQUEST_SCOPE`, `TELEMETRY_SCOPE`)
- Provides `TelemetryService` implementation
- Provides `TelemetryServiceTokenValidator` with fallback to default implementation

**Extension Name:** `"Telemetry Service Core"`

## Policy Contexts

### RequestTelemetryPolicyContext
Used during token validation to determine required credential scopes:
- **Scope**: `request.telemetry`
- **Purpose**: Extract credential scopes needed for the request
- **Context Type**: `RequestPolicyContext` for outbound scope requirements
- **Usage**: Instantiated with `RequestContext` containing the telemetry request message

### TelemetryPolicyContext
Used during access control to validate participant permissions:
- **Scope**: `telemetry`
- **Purpose**: Validate participant's access to telemetry credentials
- **Context Type**: `ParticipantAgentPolicyContext` for agent-based evaluation
- **Input**: `ParticipantAgent` with verified identity and claims

## How It Works

### End-to-End Request Flow

#### 1. Token Request

```
Telemetry Agent creates JWT token
→ Includes: iss, sub, aud, exp, jti, scope
→ Sends GET /v1alpha/sas-token with Bearer token
→ Token wrapped in TokenRepresentation
```

#### 2. Token Validation

```
TelemetryServiceImpl.createSasToken() called
→ TelemetryServiceTokenValidatorImpl.verify() invoked
→ Creates RequestTelemetryPolicyContext with request message
→ Evaluates request policy (extracts required scopes)
→ Builds RequestScope with extracted scopes
→ Creates VerificationContext with policy and scopes
→ Calls identityService.verifyJwtToken()
→ Validates token signature and claims
→ Extracts participant DID using protocol extraction function
→ Creates ParticipantAgent with identity and claims
→ Returns ServiceResult<ParticipantAgent>
```

#### 3. Policy Evaluation

```
TelemetryServiceImpl receives ParticipantAgent
→ Creates TelemetryPolicyContext with ParticipantAgent
→ PolicyEngine evaluates telemetry policy
→ Checks constraints (e.g., membership, roles)
→ Returns PolicyEvaluationResult (success/failure)
```

#### 4. Credential Issuance

```
TelemetryServiceCredentialFactory.get()
→ Generates SAS token or retrieves connection string
→ Returns Result<TokenRepresentation> with:
   - token: SAS token or connection string
   - expiresIn: Validity period in seconds
   - additional: type (SAS or CONNECTION_STRING)
```

#### 5. Response

```
TelemetryService returns ServiceResult<TokenRepresentation>
→ Agent caches credential
→ Agent uses credential to publish telemetry
→ Agent refreshes before expiration
```

## Dependencies

- **EDC Identity Service** - JWT token verification
- **EDC Policy Engine** - Policy evaluation framework
- **EDC Participant Agent Service** - Agent creation and management
- **EDC DataspaceProfileContextRegistry** - Protocol-based identity extraction
- **Telemetry Service SPI** - Interfaces and data models
- **Telemetry Service Credential Factory** - Credential generation (e.g., Event Hub)

## Related Components

- **Telemetry Agent Core** - Client that requests credentials from this service
- **Telemetry Service Credential API** - REST API exposing this service
- **Event Hub Credential Factory** - Generates SAS tokens for Azure Event Hub
- **Policy Evaluation Extension** - Provides policy constraint functions
- **DataspaceProfileContextRegistry** - Protocol mapping implementations
