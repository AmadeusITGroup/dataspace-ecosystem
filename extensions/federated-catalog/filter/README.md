# Federated Catalog Filter Extension

## Overview

This extension provides a VC (Verifiable Credential)-based filtering mechanism for the Eclipse Dataspace Components (EDC) federated catalog. It allows participants to filter catalog assets based on their verifiable credentials and policies.

## Components

### API Layer

#### `FederatedCatalogFilterApiV2`
- **Type**: Interface
- **Purpose**: Defines the REST API contract for the federated catalog filter service
- **Endpoint**: POST `/filter`
- **Functionality**: Accepts filter requests and returns filtered catalog entries based on participant VCs

#### `VcCatalogFilterController`
- **Type**: REST Controller
- **Path**: `/filter`
- **Purpose**: Implements the catalog filtering REST endpoint
- **Process Flow**:
  1. Validates the incoming token representation
  2. Authenticates the request using the IdentityService
  3. Fetches and filters the catalog based on participant credentials
  4. Returns filtered catalogs as JSON-LD
- **Response Codes**:
  - `200 OK`: Successfully filtered catalog entries
  - `204 No Content`: No catalog entries after filtering
  - `400 Bad Request`: Missing or invalid request
  - `403 Forbidden`: Invalid or expired token
  - `500 Internal Server Error`: Processing error

### Extension

#### `VcCatalogFilterExtension`
- **Type**: Service Extension
- **Purpose**: Bootstraps and configures the catalog filter service
- **Configuration**:
  - Port: `web.http.catalog.port` (default: 8383)
  - Path: `web.http.catalog.path` (default: `/api/catalogfilter`)
  - Authority DID: `dse.authority.did` (required)
- **Responsibilities**:
  - Registers JSON-LD namespaces (EDC, ODRL, DCAT, DCT, DSPACE)
  - Registers type transformers for ODRL policy elements
  - Configures web service endpoints
  - Initializes the FederatedCatalogService

### Core Services

#### `FederatedCatalogService`
- **Purpose**: Main service for fetching and filtering federated catalogs
- **Key Methods**:
  - `fetchAndFilterCatalog()`: Retrieves catalogs and applies policy-based filtering
  - `filterCatalog()`: Filters catalog entries based on participant DID and dataset policies
  - `retrieveCatalog()`: Fetches catalog data from the authority's DID-resolved endpoint (federated catalog)
- **Filtering Logic**:
  1. Checks if catalog properties contain the participant DID
  2. Evaluates dataset contract policies against participant credentials
  3. Returns only datasets that pass policy evaluation

#### `AuthorityCatalogFilterDidResolver`
- **Purpose**: Resolves the federated catalog service URL from the authority's DID document
- **Service Type**: Looks for `FederatedCatalogService` in the DID document's service endpoints
- **Returns**: The catalog service endpoint URL

#### `IdentityServiceValidator`
- **Purpose**: Validates JWT claim tokens and extracts claim
- **Validation Scope**: `VerifiableCredential:read` with `discoverability:use` action
- **Returns**: Validated `ClaimToken` containing participant credentials

## Architecture

```
┌─────────────────────┐
│  REST Client        │
└──────────┬──────────┘
           │ POST /filter
           ↓
┌─────────────────────────────────┐
│ VcCatalogFilterController       │
│  - Validates token              │
│  - Delegates to services        │
└──────────┬──────────────────────┘
           │
           ↓
┌─────────────────────────────────┐
│ IdentityServiceValidator        │
│  - Verifies JWT token           │
│  - Extracts ClaimToken          │
└──────────┬──────────────────────┘
           │
           ↓
┌─────────────────────────────────┐
│ FederatedCatalogService         │
│  - Fetches catalog              │
│  - Applies policy filtering     │
└──────────┬──────────────────────┘
           │
           ├──→ AuthorityCatalogFilterDidResolver
           │    (Resolves catalog endpoint)
           │
           └──→ PolicyEngine
                (Evaluates dataset policies)
```

## Data Flow

1. **Request**: Client sends a POST request with claim token and participant DID
2. **Authentication**: Token is validated against the IdentityService
3. **Resolution**: Authority DID is resolved to get the federated catalog service endpoint
4. **Retrieval**: Catalog data is fetched from the resolved endpoint
5. **Filtering**: 
   - Catalogs matching participant DID are included
   - Datasets are evaluated against their policies using participant VCs
6. **Response**: Filtered catalogs are transformed to JSON-LD and returned

## Policy Evaluation

The service evaluates dataset policies using the `PolicyEngine` with a `CatalogDiscoveryPolicyContext` that includes:
- Participant agent with VC claims
- Dataset offer policies
- ODRL policy constraints

Only datasets that pass all policy evaluations are included in the filtered results.

## Dependencies

- EDC Core (IdentityService, PolicyEngine, TypeTransformerRegistry)
- DID Resolution (DidResolverRegistry)
- JSON-LD processing
- JAX-RS for REST endpoints
- HTTP Client for external catalog queries

