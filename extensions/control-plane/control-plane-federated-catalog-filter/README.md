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
- **Path**: `/v4/catalogs/request` on the management API (`POST /api/management/v4/catalogs/request`). This is a
  DSE path: the upstream federated catalog query API uses `/v4/catalogs/request`, and the two never share a path.
- **Method**: POST
- **Authentication**: Supports Bearer token and API key authentication
- **Response**: the catalog filter's reply, as is (see [Filtered Catalog Response](#filtered-catalog-response))

**Process Flow:**
1. Validates and transforms the optional management v4 `QuerySpec`
2. Resolves the authority's catalog filter service URL from its DID document
3. Generates a verifiable credential claim token using the participant's identity
4. Sends a filter request to the authority service
5. Returns the filter's reply to the caller unchanged: same HTTP status, same `Content-Type`, same body. The control
   plane does not parse, expand or re-compact it.

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
- **tokenRepresentation** - `TokenRepresentation` containing the participant's verifiable credentials token
- **participantDid** - The DID of the requesting participant
- **query** - The `QuerySpec` of the API request (`QuerySpec.none()` when the request has no body)

## How It Works

### End-to-End Flow

1. **API Request** (management API v4, same request body as the federated catalog `POST /v4/catalogs/request`):
   ```
   POST /api/management/v4/catalogs/request
   Content-Type: application/json
   {
     "@context": { "@vocab": "https://w3id.org/edc/v0.0.1/ns/" },
     "@type": "QuerySpec",
     "limit": 50
   }
   ```
   The body is optional (no body = default `QuerySpec`). A present body must have the compact `@type` `QuerySpec`:
   a missing `@type`, an expanded IRI (`https://w3id.org/edc/v0.0.1/ns/QuerySpec`) or another type returns `400`.

2. **Authority Service Discovery**:
   - Resolves authority DID document
   - Extracts `FederatedCatalogFilterService` endpoint URL

3. **Credential Token Generation**:
   - Creates JWT token with participant identity claims
   - Includes scope `org.eclipse.dse.vc.type:VerifiableCredential:read` for credential access
   - Obtains verifiable credentials claim token from Identity Hub via `IdentityService`

4. **Filter Request**:
   ```
   POST <authority-service-url>
   Content-Type: application/json
   {
     "tokenRepresentation": { "token": "Bearer ..." },
     "participantDid": "did:web:participant.example",
     "query": { "offset": 0, "limit": 50, "filterExpression": [], "sortOrder": "ASC" }
   }
   ```

5. **Filtered Catalog Response**:
   - Authority validates credentials
   - Queries the federated catalog with the `QuerySpec` and applies the visibility filter (dataset policies)
   - Returns the filtered catalogs; the control plane returns this reply as is

### Filtered Catalog Response

The response is the catalog filter's own output, not the federated catalog management v4 shape. The filter
serializes the catalogs with the EDC DSP 2025-1 transformers and compacts them with its `CATALOG_FILTER_API` scope:
the DSP 2025-1 context, the EDC dspace context and the DSE prefixes (`dse`, `dse-policy`, `skos`, `prov`, `adms`).
Terms defined by the DSP 2025-1 context are unprefixed and always arrays where the context declares a set
(`dataset`, `service`, `distribution`, `hasPolicy`, `permission`, `constraint`); other terms keep their prefix
(`dct:`, `dcat:`, `edc:`, ...). Shortened example:

```json
[
  {
    "@id": "10363878-6cee-45d2-b57c-d476cc418619",
    "@type": "Catalog",
    "participantId": "did:web:provider-identityhub:api:did",
    "edc:originator": "https://provider-controlplane:8282/api/dsp/2025-1",
    "service": [
      { "@id": "...", "@type": "DataService", "endpointDescription": "dspace:connector",
        "endpointURL": "https://provider-controlplane:8282/api/dsp/2025-1" }
    ],
    "dataset": [
      {
        "@id": "rest-api",
        "@type": "Dataset",
        "id": "rest-api",
        "description": "a basic REST API",
        "edc:name": "Test Asset REST",
        "edc:createdAt": "2026-11-06T18:36:18.479Z",
        "hasPolicy": [
          {
            "@id": "<offer id>",
            "@type": "Offer",
            "permission": [
              { "action": "use",
                "constraint": [ { "leftOperand": "dse-policy:Membership", "operator": "eq", "rightOperand": "active" } ] }
            ]
          }
        ],
        "distribution": [
          { "@type": "Distribution", "format": "HttpData-PULL",
            "accessService": { "@id": "...", "@type": "DataService", "endpointDescription": "dspace:connector",
                               "endpointURL": "https://provider-controlplane:8282/api/dsp/2025-1" } }
        ]
      }
    ],
    "dcat:themeTaxonomy": { "@id": "urn:dse:taxonomy", "@type": "skos:ConceptScheme", "skos:hasTopConcept": { "...": "..." } },
    "@context": [
      "https://w3id.org/dspace/2025/1/context.jsonld",
      "https://w3id.org/edc/dspace/v0.0.1",
      { "dse": "https://w3id.org/dse/v0.0.1/ns/", "dse-policy": "https://w3id.org/dse/policy/",
        "prov": "http://www.w3.org/ns/prov#", "skos": "http://www.w3.org/2004/02/skos/core#", "adms": "http://www.w3.org/ns/adms#" }
    ]
  }
]
```

A catalog without visible datasets has no `dataset` key. Offers are serialized by the EDC DSP 2025-1 policy
transformer: empty `prohibition` / `obligation` lists are omitted, rule lists and `constraint` are arrays, `action`,
`operator` and `leftOperand` are plain strings (`use`, `eq`, `dse-policy:Membership`), and `assigner` / `assignee` /
`target`, when a policy has them, are plain IRIs (the catalog offers currently have none of the three).

### Token Generation

The extension generates a JWT token with the following claims:

| Claim | Description | Value |
|-------|-------------|-------|
| `iss` | Issuer | Participant's DID |
| `sub` | Subject | Participant's DID |
| `jti` | JWT ID | Random UUID |
| `iat` | Issued At | Current timestamp |
| `exp` | Expiration | 1 hour from now |
| `scope` | Access Scope | `org.eclipse.dse.vc.type:VerifiableCredential:read` |
| `aud` | Audience | Authority DID |

### Credential Scope

The extension requests all verifiable credentials:
- **Scope**: `org.eclipse.dse.vc.type:VerifiableCredential:read`
- This allows the authority to validate:
  - MembershipCredentials
  - DomainCredentials

## Error Handling

Once the catalog filter replies, its status, `Content-Type` and body are forwarded unchanged, errors included. The
filter writes its errors with the EDC exception mappers, so they have the EDC JSON error body
(`[{"message": ..., "type": ..., "path": ..., "invalidValue": ...}]`):

| Filter reply | Control plane response |
|---|---|
| `200` with the catalogs | `200`, same body |
| `400` `InvalidRequest`: no participant token in the filter request, or the federated catalog rejected the query with `400` (its messages are kept) | same status, same body |
| `403` `NotAuthorized`: the participant token is rejected, or the federated catalog answered `403`; `401` / `409`: the federated catalog answered that status (its messages are kept) | same status, same body |
| `502` `BadGateway`: the federated catalog URL cannot be resolved from the authority DID document, the federated catalog cannot be reached, or it answered a status that has no upstream exception that keeps the federated catalog message (`ObjectNotFoundException` has a fixed text), e.g. `404`, `500`, `503`; the message names that status | `502`, same body |
| `500`: unexpected filter error (EDC sets no entity, so the body is the Jetty error page) | `500`, same body |

A sort field that the datasets do not have is rejected by the federated catalog before the query (DSE `v4:QuerySpec`
validator of `federated-catalog-cache`): `400` `ValidationFailure` with the message
`Cannot sort by '<field>': field does not exist in Dataset`. The filter keeps the status and the message (`400`
`InvalidRequest`), and the control plane returns that reply as is.

Before the filter replies, the EDC error mapping applies:

| Failure | Status | Body |
|---|---|---|
| Request body is not a v4 `QuerySpec` (missing, expanded or wrong `@type`) | `400` | EDC error (`InvalidRequest` / `ValidationFailure`) |
| Filter URL cannot be resolved: authority DID not resolvable, or no `FederatedCatalogFilterService` service in its DID document | `502` | EDC error, `"type": "BadGateway"`, message `Could not resolve the authority catalog filter url: ...` |
| Filter not reachable (connection refused or reset, connect timeout, TLS failure, invalid URL in the DID document) | `502` | EDC error, `"type": "BadGateway"`, message `Could not reach the authority catalog filter <url>: ...` |
| Credentials token cannot be obtained (`IdentityService`/STS) | `500` | no EDC error (EDC unexpected-exception mapping: no entity, Jetty error page) |

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
