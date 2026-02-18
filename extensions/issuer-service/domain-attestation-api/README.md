# Domain Attestation API

This module provides a REST API for managing domain attestations in the Eclipse Dataspace Components (EDC) Issuer Service. It enables authorized administrators to create, update, delete, and query domain attestations that link participants to specific domains they control.

## Overview

Domain attestations are verifiable claims that assert a participant's participation in a specific domain. This is crucial for establishing trust in decentralized identity systems, as it provides proof that a participant is part of a particular domain (e.g., `example.com`).

The Domain Attestation API provides administrative operations to manage these attestations within the Identity Hub's Issuer Service context.

## Architecture

```
Admin Client → Domain Attestation API → DomainAttestationStore → Storage
                       ↓
              Authorization Check (authorized domain)
```

## Components

### REST API Layer

#### `DomainAttestationAdminApi`
OpenAPI interface defining the administrative API contract:
- Documents all CRUD operations for domain attestations
- Defines security schemes (Bearer token and API key authentication)
- Provides operation descriptions and response codes
- Categorized under "Domain Attestation Admin API" tag

#### `DomainAttestationApiController`
REST controller implementing the administrative API:
- **Path**: `/identity/unstable/participants/{participantContextId}/attestation-domain`
- Enforces domain authorization checks
- Delegates storage operations to `DomainAttestationStore`

**Authorization Logic:**
- Validates that the domain in the attestation matches the configured `authorized.domain.issuance`
- Prevents unauthorized domain attestations
- Throws `NotAuthorizedException` for unauthorized domains

### Data Transfer Object

#### `DomainAttestationDto`
Lightweight DTO for API request/response serialization:
- **id**: Unique identifier for the attestation (auto-generated on creation)
- **holderId**: The DID of the participant who controls the domain
- **domain**: The domain being attested (e.g., `route`)

### Storage Layer

#### `DomainAttestationStore` (Interface)
Defines the storage contract for domain attestations (from SPI module).

#### `InMemoryDomainAttestationStore`
Default in-memory implementation of the store:
- Thread-safe using `ConcurrentHashMap`
- Supports querying with `QuerySpec` and `ReflectionBasedQueryResolver`
- Auto-generates UUIDs for new attestations
- Provides CRUD operations with proper error handling

### Extension Layer

#### `DomainIssuerServiceCoreExtension`
Main extension that bootstraps the domain attestation API:
- Registers the `DomainAttestationApiController` with the web service
- Configures the authorized domain for issuance
- Integrates with the `ISSUERADMIN` API context

#### `DomainIssuerServiceDefaultServicesExtension`
Provides default service implementations:
- Registers `InMemoryDomainAttestationStore` as default store
- Can be replaced with SQL or other persistent implementations

## Domain Authorization

### Purpose

The domain authorization mechanism ensures that an issuer service only issues attestations for domains it controls or is authorized to represent.

### How It Works

1. **Configuration**: Set `authorized.domain.issuance=route`
2. **Validation**: Each create/update request checks if the attestation's domain matches
3. **Rejection**: If domains don't match, returns 401 Unauthorized


## Dependencies

- **EDC Identity Hub SPI** - API context and versioning
- **EDC Web SPI** - Web service registration and error handling
- **EDC SPI** - Query specification and store result types
- **Domain Issuer Service SPI** - Domain attestation model and store interface
- **Jackson** - JSON serialization/deserialization
- **Jakarta REST** - JAX-RS annotations

