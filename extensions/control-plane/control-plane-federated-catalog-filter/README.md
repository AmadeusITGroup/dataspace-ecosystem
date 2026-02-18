# Control Plane Federated Catalog Filter

This module provides a participant-facing API that enables participants to retrieve a filtered view of the federated catalog based on their credentials and access rights. It integrates with an authority service that performs the actual filtering logic.

## Overview

The federated catalog filter extension allows participants to query a centralized authority service to obtain a personalized catalog view containing only the assets and offerings they are authorized to access. The filtering is based on verifiable credentials presented by the participant during the request.

## Architecture

The system follows a distributed authorization model:
1. **Participant** - Requests filtered catalog view
2. **Control Plane** - This extension handles the request
3. **Authority Service (catalog filter)** - Performs credential validation and catalog filtering
4. **Identity Hub** - Provides verifiable credentials for the participant

## Components

### Extension Layer

#### `FederatedCatalogFilteringExtension`
The main extension that bootstraps the catalog filtering capability:
- Registers the catalog filtering REST API controller
- Configures the authority DID for catalog filtering service discovery
- Injects required services (IdentityService, DidResolverRegistry, WebService)
- Exposes the API on the Management API context

**Configuration:**
- `dse.authority.did` - The DID of the authority service (required)

### REST API Layer

#### `CatalogFilteringController`
REST controller that exposes the catalog filtering endpoint:
- **Path**: `/catalog/participantCatalog`
- **Method**: GET
- **Authentication**: Supports Bearer token and API key authentication
- **Response**: Filtered catalog in JSON format

**Process Flow:**
1. Resolves the authority's catalog filter service URL from its DID document
2. Generates a verifiable credential claim token using the participant's identity
3. Sends a filter request to the authority service
4. Returns the filtered catalog to the caller

#### `FederatedCatalogFilteringApiV2`
OpenAPI interface definition for the catalog filtering API:
- Provides Swagger/OpenAPI documentation
- Defines security schemes (Bearer token, API key)
- Documents response codes and behaviors

### DID Resolution Layer

#### `AuthorityCatalogDidResolver`
Resolves the catalog filter service endpoint from the authority's DID document:
- Queries the DID resolver registry for the authority's DID document
- Locates the service endpoint with type `FederatedCatalogFilterService`
- Returns the service URL for making filter requests

### Data Model

#### `FilterRequest`
Request payload sent to the authority service:
- **token** - `TokenRepresentation` containing the participant's verifiable credentials
- **participantDid** - The DID of the requesting participant

## How It Works

### End-to-End Flow

1. **API Request**:
   ```
   GET /catalog/participantCatalog
   Authorization: Bearer <jwt-token>
   ```

2. **Authority Service Discovery**:
   - Resolves authority DID document
   - Extracts `FederatedCatalogFilterService` endpoint URL

3. **Credential Token Generation**:
   - Creates JWT token with participant identity claims
   - Includes scope `dse:VerifiableCredential:read` for credential access
   - Obtains verifiable credentials claim token from Identity Hub via `IdentityService`

4. **Filter Request**:
   ```
   POST <authority-service-url>
   Content-Type: application/json
   {
     "token": { ... },
     "participantDid": "did:web:participant.example"
   }
   ```

5. **Filtered Catalog Response**:
   - Authority validates credentials
   - Applies filtering logic based on policies
   - Returns filtered catalog

### Token Generation

The extension generates a JWT token with the following claims:

| Claim | Description | Value |
|-------|-------------|-------|
| `iss` | Issuer | Participant's DID |
| `sub` | Subject | Participant's DID |
| `jti` | JWT ID | Random UUID |
| `iat` | Issued At | Current timestamp |
| `exp` | Expiration | 1 hour from now |
| `scope` | Access Scope | `dse:VerifiableCredential:read` |
| `aud` | Audience | Authority DID |

### Credential Scope

The extension requests all verifiable credentials:
- **Scope**: `dse:VerifiableCredential:read`
- This allows the authority to validate:
  - MembershipCredentials
  - DomainCredentials

## Error Handling

The extension handles several error scenarios:

1. **Authority Service Not Found**:
   - Error: `Could not find service with type 'FederatedCatalogFilterService' in DID document`
   - Cause: Authority DID document missing required service entry

2. **DID Resolution Failed**:
   - Error: `Could not resolve authority catalog filter url`
   - Cause: DID resolver cannot resolve authority DID

3. **Credential Token Failed**:
   - Error: Token generation fails in IdentityService
   - Cause: Identity Hub unavailable or credentials not issued

4. **Network Errors**:
   - Error: `IOException` or `InterruptedException`
   - Cause: Network issues connecting to authority service

5. **Empty Catalog Response**:
   - HTTP 500 returned
   - Cause: Authority service returned empty or null response

## Security Considerations

### Authentication
- API requires authentication (Bearer token or API key)
- Tokens are validated by the EDC framework before reaching the controller

### Authorization
- The authority service performs actual authorization
- Filtering is based on verifiable credentials presented
- Participants only see assets they have access to

### Token Security
- JWT tokens have 1-hour expiration
- Each request generates a new token with unique `jti`
- Tokens are signed by the participant's private key

## Dependencies

- **EDC IAM SPI** - Identity and token management
- **EDC DID SPI** - DID resolution and document processing
- **EDC Web SPI** - REST API framework
- **Jackson** - JSON serialization/deserialization
- **Jakarta REST** - JAX-RS annotations
