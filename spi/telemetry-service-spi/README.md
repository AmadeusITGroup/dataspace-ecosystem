# Telemetry Service SPI

This module defines the Service Provider Interface (SPI) for the Telemetry Service in the Eclipse Dataspace Components (EDC) ecosystem. It provides the contracts and data models for credential issuance, token validation, and policy evaluation for telemetry operations.

## Overview

The Telemetry Service SPI enables:
- **Credential Issuance**: Generating SAS tokens or connection strings for telemetry agents
- **Token Validation**: Verifying JWT tokens from telemetry agents and extracting participant identity
- **Policy Evaluation**: Enforcing access policies for telemetry credential requests
- **Request Context**: Managing request scopes for policy-based scope extraction

## Components

### Core Interfaces

#### `TelemetryService`
Main service interface for telemetry credential issuance:
- **Extension Point**: Can be implemented by custom telemetry service implementations
- Validates incoming JWT tokens and evaluates telemetry policies
- Issues SAS tokens or connection strings to authorized agents

#### `TelemetryServiceTokenValidator`
Interface for JWT token validation and participant identity extraction:
- Verifies JWT signature and claims
- Evaluates request policies to extract required scopes
- Extracts participant identity using protocol-specific methods

#### `TelemetryServiceCredentialFactory`
Factory interface for generating telemetry service credentials:
- Extends `Supplier<Result<TokenRepresentation>>` for functional use
- Generates SAS tokens or connection strings with validity periods

### Policy Contexts

#### `TelemetryPolicyContext`
Policy context for evaluating telemetry access policies:
- Implements `ParticipantAgentPolicyContext` for agent-based evaluation
- Scope: `"telemetry"` (marked with `@PolicyScope`)
- Contains the verified `ParticipantAgent` with participant identity and claims

#### `RequestTelemetryPolicyContext`
Policy context for evaluating telemetry request policies:
- Extends `RequestPolicyContext` for request validation
- Scope: `"request.telemetry"` (marked with `@PolicyScope`)
- Used during token validation to extract required credential scopes
- Carries `RequestContext` and `RequestScope.Builder` for scope configuration

### Data Models

#### `TelemetryRequestMessage`
Represents a telemetry request message:
- Implements `RemoteMessage` for EDC protocol integration
- Contains protocol information and counter-party details
- Used during token validation for context

#### `TelemetryServiceCredentialType`
Enumeration of supported credential types:
- **SAS_TOKEN**: Shared Access Signature token (e.g., for Azure Event Hub)
- **CONNECTION_STRING**: Full connection string (e.g., for Event Hub or Kafka)

### Policy Interface

#### `TelemetryPolicy`
Interface for providing telemetry access policies:
- Functional interface providing a `Policy` object
- Used to enforce authorization rules for credential issuance

## How It Works

### Token Validation Flow

1. **Token Request**: Telemetry agent sends JWT token to telemetry service
2. **Scope Extraction**: `RequestTelemetryPolicyContext` evaluates request policy to extract required scopes
3. **Token Verification**: `TelemetryServiceTokenValidator.verify()` validates JWT signature and claims
4. **Identity Extraction**: Participant DID extracted from token claims using protocol-specific extraction
5. **Agent Creation**: `ParticipantAgent` created with verified identity and claims
6. **Policy Evaluation**: `TelemetryPolicyContext` evaluates telemetry policy against participant agent
7. **Credential Issuance**: If policy passes, `TelemetryServiceCredentialFactory` generates credentials

## Policy Scopes

### Request Scope: `"request.telemetry"`
- Evaluated during token validation
- Determines which credential scopes the request requires
- Used by policy engine to configure scope extraction

### Access Scope: `"telemetry"`
- Evaluated after token validation
- Validates participant's access rights to telemetry credentials

## Dependencies

- **EDC Policy Engine** - Policy evaluation framework (`PolicyContext`, `PolicyEngine`)
- **EDC IAM SPI** - Token and identity management (`TokenRepresentation`, `ParticipantAgent`)
- **EDC Request Context SPI** - Request policy contexts (`RequestPolicyContext`)
- **EDC Runtime** - Extension point framework (`@ExtensionPoint`)
- **Jackson** - JSON serialization annotations

## Related Components

- **Telemetry Service Core** - Implements these interfaces
- **Event Hub Credential Factory** - Generates Azure Event Hub credentials
- **Telemetry Agent** - Uses this service to obtain publishing credentials
- **Policy Framework** - Provides constraint functions for policy evaluation
