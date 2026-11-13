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
- **Response Codes** (the participant control plane forwards them as is). Errors are thrown as EDC exceptions and
  written by the EDC exception mappers with the EDC JSON error body (`[{"message", "type", "path", "invalidValue"}]`):
  - `200 OK`: the filtered catalogs (an empty JSON array when there are none)
  - `400 Bad Request` (`InvalidRequest`): the request has no participant token (`tokenRepresentation`)
  - `403 Forbidden` (`NotAuthorized`): invalid or expired token
  - the federated catalog answered `400`, `401`, `403` or `409` (the statuses with an upstream EDC exception that keeps
    the federated catalog messages): the same status, with the messages of its EDC error body
  - `502 Bad Gateway` (`BadGateway`): the federated catalog URL cannot be resolved from the authority DID document, the
    federated catalog cannot be reached, or it answered another status (e.g. `404`, `500`, `503`), which has no upstream
    exception that keeps the federated catalog message (`ObjectNotFoundException` has a fixed text); the message names
    that status. Bodies that are not EDC error bodies (e.g. the Jetty error page of a `500`) are only logged
  - `500 Internal Server Error`: unexpected error; EDC sets no entity, so the body is the Jetty error page
- **Response body**: a JSON array of catalogs written with the EDC DSP 2025-1 transformers and compacted with the
  `CATALOG_FILTER_API` scope (DSP 2025-1 context, EDC dspace context, DSE prefixes). The control plane
  `POST /v4/catalogs/request` returns it unchanged, see
  [`control-plane-federated-catalog-filter`](../../control-plane/control-plane-federated-catalog-filter/README.md#filtered-catalog-response).

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

