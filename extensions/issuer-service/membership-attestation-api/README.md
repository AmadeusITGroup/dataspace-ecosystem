# Membership Attestation API

This module provides a REST API for managing membership attestations in the Eclipse Dataspace Components (EDC) Issuer Service. It enables authorized administrators to create, update, delete, and query membership attestations that assert a participant's membership status in a dataspace or organization.

## Overview

Membership attestations are verifiable claims that assert a participant's membership in a specific organization, consortium, or dataspace. These attestations are fundamental for establishing trust and access control in federated identity systems, as they provide proof of membership status and type.

The Membership Attestation API provides administrative operations to manage these attestations within the Identity Hub's Issuer Service context.

## Architecture

```
Admin Client → Membership Attestation API → MembershipAttestationStore → Storage
                           ↓
                  Credential Store Filter 
```

## Components

### REST API Layer

#### `MembershipAttestationAdminApi`
OpenAPI interface defining the administrative API contract:
- Documents all CRUD operations for membership attestations
- Defines security schemes (Bearer token and API key authentication)
- Provides operation descriptions and response codes
- Categorized under "Membership Attestation Admin API" tag

#### `MembershipAttestationApiController`
REST controller implementing the administrative API:
- **Path**: `/identity/unstable/participants/{participantContextId}/attestation-membership`
- **Content Type**: `application/json`
- Delegates storage operations to `MembershipAttestationStore`
- Automatically sets attestation timestamp to current time on create/update

### Data Transfer Object

#### `MembershipAttestationDto`
Lightweight DTO for API request/response serialization:
- **id**: Unique identifier for the attestation (auto-generated on creation if not provided)
- **holderId**: The DID of the participant who holds the membership
- **name**: The name of the organization or dataspace
- **membershipType**: The type/level of membership (e.g., "full", "associate", "partner")

### Storage Layer

#### `MembershipAttestationStore` (Interface)
Defines the storage contract for membership attestations (from SPI module).

#### `InMemoryMembershipAttestationStore`
Default in-memory implementation of the store:
- Supports querying with `QuerySpec` and `ReflectionBasedQueryResolver`
- Auto-generates unique IDs for new attestations
- Provides CRUD operations with proper error handling

### Extension Layer

#### `IssuerServiceCoreExtension`
Main extension that bootstraps the membership attestation API:
- Registers the `MembershipAttestationApiController` with the web service
- Integrates with the `ISSUERADMIN` API context
- Wires up the attestation store dependency

**Extension Name:** `"Issuer Service Core"`

#### `IssuerServiceDefaultServicesExtension`
Provides default service implementations:
- Registers `InMemoryMembershipAttestationStore` as default store

**Extension Name:** `"Issuer Service Default Services"`

### Credential Store Filter

#### `FilteringCredentialStore`
A decorator/wrapper for the credential store that filters out credentials with empty raw VC data:
- Implements `CredentialStore` interface
- Delegates to underlying credential store
- Filters credentials on create operation
- Prevents storage of credentials without raw VC content
- Workaround introduced to deal with EDC 0.13.2 revocation vcs.

**Purpose:**
- Avoids storing incomplete credential resources
- Returns success for filtered credentials without actual storage
- Does not store revocation credentials to avoid breaking the federated catalog crawler

#### `FilteredCredentialStoreExtension`
Extension that provides the filtering credential store:
- Wraps the injected credential store with filtering logic
- Marked with `isDefault = false` to override the default implementation
- Integrates with EDC's service provider mechanism

**Extension Name:** `"FilteredCredentialStore"`

## Data Model

### MembershipAttestation Entity

```java
public record MembershipAttestation(
    String id,                    // Unique identifier
    String holderId,              // DID of the member
    String name,                  // Organization/dataspace name
    String membershipType,        // Type of membership
    Instant attestationTime       // When the attestation was created
) {}
```

## Integration with Credential Issuance

Membership attestations are typically used in conjunction with verifiable credential issuance:

```
1. Create membership attestation
   ↓
2. Issue MembershipCredential based on attestation
   ↓
3. Credential includes attestation data in claims
   ↓
4. Consumer presents credential for access
   ↓
5. Policy validates credential against attestation
```

## Dependencies

- **EDC Identity Hub SPI** - API context and versioning
- **EDC Web SPI** - Web service registration and error handling
- **EDC SPI** - Query specification and store result types
- **Membership Attestation SPI** - Domain model and store interface
- **Jackson** - JSON serialization/deserialization
- **Jakarta REST** - JAX-RS annotations

## Related Components

- **Issuer Service** - Uses membership attestations for credential issuance
- **Identity Hub** - Manages participant contexts and credentials
- **Policy Evaluation** - Uses membership attestations in constraints
- **Domain Attestation API** - Parallel functionality for domain attestations


