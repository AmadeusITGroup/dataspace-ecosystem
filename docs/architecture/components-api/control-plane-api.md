# Control Plane API

The Control Plane Management API provides endpoints for managing assets, policies, contracts, and catalog operations in the DSE.

## Base URL

```
{{protocol}}://{{apiGatewayHost}}/{{participantId}}/{{controlPlaneManagementApi}}
```

**Note:** Replace `{{participantId}}` with specific participant identifiers:
- `{{providerId}}` for provider operations (creating assets, policies, contracts)
- `{{consumerId}}` for consumer operations (negotiations, transfers)
- `{{authorityId}}` for catalog queries

## Assets

### Create Asset

```http
POST /{{providerId}}/{{controlPlaneManagementApi}}/v4/assets/
Content-Type: application/json
```

**Request:**

```json
{
    "@type": "Asset",
    "@id": "{{assetId}}",
    "properties": {
        "version": "1.0",
        "name": "user-test",
        "description": "user test files",
        "id": "{{assetId}}",
        "contenttype": "application/json"
    },
    "dataAddress": {
        "@type": "DataAddress",
        "type": "HttpData",
        "baseUrl": "http://hello-world-api-nginx:80/data.json",
        "proxyPath": "true"
    },
    "@context": {
        "@vocab": "https://w3id.org/edc/v0.0.1/ns/",
        "edc": "https://w3id.org/edc/v0.0.1/ns/",
        "odrl": "http://www.w3.org/ns/odrl/2/"
    }
}
```

### Get Asset

```http
GET /{{providerId}}/{{controlPlaneManagementApi}}/v4/assets/{assetId}
```

### Query Assets

```http
POST /{{providerId}}/{{controlPlaneManagementApi}}/v4/assets/request
Content-Type: application/json
```

**Request:**

```json
{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@type": "QuerySpec",
  "offset": 0,
  "limit": 50,
  "filterExpression": [
    {
      "@type": "Criterion",
      "operandLeft": "properties.contenttype",
      "operator": "=",
      "operandRight": "application/json"
    }
  ]
}
```

### Delete Asset

```http
DELETE /{{providerId}}/{{controlPlaneManagementApi}}/v4/assets/{assetId}
```

## Policy Definitions

### Create Policy Definition

```http
POST /{{providerId}}/{{controlPlaneManagementApi}}/v4/policydefinitions
Content-Type: application/json
```

**Request:**

```json
{
    "@context": {
        "@vocab": "https://w3id.org/edc/v0.0.1/ns/",
        "dse-policy": "https://w3id.org/dse/policy/"
    },
    "@id": "{{policy_definition_id}}",
    "@type": "PolicyDefinition",
    "policy": {
        "@context": "http://www.w3.org/ns/odrl.jsonld",
        "@type": "http://www.w3.org/ns/odrl/2/Set",
        "permission": [
            {
                "action": "use",
                "constraint": {
                    "@type": "Constraint",
                    "leftOperand": "dse-policy:Membership",
                    "operator": "odrl:eq",
                    "rightOperand": "active"
                }
            }
        ]
    }
}
```

### Get Policy Definition

```http
GET /{{providerId}}/{{controlPlaneManagementApi}}/v4/policydefinitions/{policyId}
```

### Query Policy Definitions

```http
POST /{{providerId}}/{{controlPlaneManagementApi}}/v4/policydefinitions/request
Content-Type: application/json
```

### Delete Policy Definition

```http
DELETE /{{providerId}}/{{controlPlaneManagementApi}}/v4/policydefinitions/{policyId}
```

## Contract Definitions

### Create Contract Definition

```http
POST /{{providerId}}/{{controlPlaneManagementApi}}/v4/contractdefinitions
Content-Type: application/json
```

**Request:**

```json
{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@id": "{{contract_definition_id}}",
  "@type": "ContractDefinition",
  "https://w3id.org/edc/v0.0.1/ns/accessPolicyId": "{{policy_definition_id}}",
  "https://w3id.org/edc/v0.0.1/ns/contractPolicyId": "{{policy_definition_id}}",
  "https://w3id.org/edc/v0.0.1/ns/assetsSelector": {
    "@type": "Criterion",
    "https://w3id.org/edc/v0.0.1/ns/operandLeft": "https://w3id.org/edc/v0.0.1/ns/id",
    "https://w3id.org/edc/v0.0.1/ns/operator": "=",
    "https://w3id.org/edc/v0.0.1/ns/operandRight": "{{assetId}}"
  }
}
```

### Get Contract Definition

```http
GET /{{providerId}}/{{controlPlaneManagementApi}}/v4/contractdefinitions/{contractDefId}
```

### Query Contract Definitions

```http
POST /{{providerId}}/{{controlPlaneManagementApi}}/v4/contractdefinitions/request
Content-Type: application/json
```

### Delete Contract Definition

```http
DELETE /{{providerId}}/{{controlPlaneManagementApi}}/v4/contractdefinitions/{contractDefId}
```

## Contract Negotiations

### Initiate Contract Negotiation

```http
POST /{{consumerId}}/{{controlPlaneManagementApi}}/v4/contractnegotiations
Content-Type: application/json
```

**Request:**

```json
{
    "@context": {
        "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
    },
    "@type": "ContractRequest",
    "counterPartyAddress": "{{protocol}}://{{apiGatewayHost}}/cp/dsp/{{providerId}}",
    "protocol": "dataspace-protocol-http:2025-1",
    "policy": {
      "@id": "{{policyId}}",
      "@type": "http://www.w3.org/ns/odrl/2/Offer",
      "http://www.w3.org/ns/odrl/2/obligation": [],
      "http://www.w3.org/ns/odrl/2/permission": {
        "http://www.w3.org/ns/odrl/2/action": {
          "@id": "http://www.w3.org/ns/odrl/2/use"
        },
        "http://www.w3.org/ns/odrl/2/constraint": {
          "http://www.w3.org/ns/odrl/2/leftOperand": {
            "@id": "https://w3id.org/dse/policy/Membership"
          },
          "http://www.w3.org/ns/odrl/2/operator": {
            "@id": "http://www.w3.org/ns/odrl/2/eq"
          },
          "http://www.w3.org/ns/odrl/2/rightOperand": "active"
        }
      },
      "http://www.w3.org/ns/odrl/2/prohibition": [],
      "http://www.w3.org/ns/odrl/2/target": {
            "@id": "{{assetId}}"
        },
        "http://www.w3.org/ns/odrl/2/assigner": {
            "@id": "{{providerDID}}"
        }
    }
}
```

### Get Contract Negotiation

```http
GET /{{consumerId}}/{{controlPlaneManagementApi}}/v4/contractnegotiations/{negotiationId}
```

### Query Contract Negotiations

```http
POST /{{consumerId}}/{{controlPlaneManagementApi}}/v4/contractnegotiations/request
Content-Type: application/json
```

### Get Contract Agreement

```http
GET /{{consumerId}}/{{controlPlaneManagementApi}}/v4/contractnegotiations/{negotiationId}/agreement
```

## Catalog

### Query the Filtered Catalog (control plane)

```http
POST /{{consumerId}}/{{controlPlaneManagementApi}}/v4/catalogs/request
Content-Type: application/json
```

**Request:** an optional `QuerySpec`, same as the federated catalog query below (compact `@type: QuerySpec`; no body
means the default query). The path is DSE's own (`/v4/catalogs/request`), not the upstream federated catalog
`/v4/catalogs/request`.

The control plane forwards the query to the authority catalog filter, which queries the federated catalog and keeps
only the datasets this participant may see. The response is the **catalog filter's reply as is** (status,
`Content-Type`, body), not the federated catalog management v4 response: a JSON array of DSP 2025-1 catalogs,
compacted with the DSP 2025-1 context, the EDC dspace context and the DSE prefixes.

**Response (shortened):**

```json
[
  {
    "@id": "10363878-6cee-45d2-b57c-d476cc418619",
    "@type": "Catalog",
    "participantId": "did:web:provider-identityhub:api:did",
    "edc:originator": "https://provider-controlplane:8282/api/dsp/2025-1",
    "service": [ { "@id": "...", "@type": "DataService", "endpointDescription": "dspace:connector", "endpointURL": "https://provider-controlplane:8282/api/dsp/2025-1" } ],
    "dataset": [
      {
        "@id": "rest-api",
        "@type": "Dataset",
        "id": "rest-api",
        "edc:name": "Test Asset REST",
        "hasPolicy": [
          {
            "@id": "{{policyId}}",
            "@type": "Offer",
            "permission": [ { "action": "use", "constraint": [ { "leftOperand": "dse-policy:Membership", "operator": "eq", "rightOperand": "active" } ] } ]
          }
        ],
        "distribution": [ { "@type": "Distribution", "format": "HttpData-PULL", "accessService": { "@id": "...", "@type": "DataService", "endpointURL": "..." } } ]
      }
    ],
    "@context": [
      "https://w3id.org/dspace/2025/1/context.jsonld",
      "https://w3id.org/edc/dspace/v0.0.1",
      { "dse": "https://w3id.org/dse/v0.0.1/ns/", "dse-policy": "https://w3id.org/dse/policy/", "prov": "http://www.w3.org/ns/prov#", "skos": "http://www.w3.org/2004/02/skos/core#", "adms": "http://www.w3.org/ns/adms#" }
    ]
  }
]
```

| Status | When |
|---|---|
| `200` | the catalog filter's catalogs |
| `400` | the body is not a v4 `QuerySpec`, or the catalog filter answered `400`: no participant token, or the federated catalog rejected the query (EDC error body with the federated catalog message, forwarded) |
| `403` | the catalog filter rejected the participant token, or the federated catalog answered `403` (EDC error body, forwarded) |
| `500` | unexpected error in the catalog filter (forwarded), or the control plane could not obtain its credentials token; no EDC error body (Jetty error page) |
| `502` | the catalog filter could not be reached or its URL could not be resolved from the authority DID document (EDC error body, `"type": "BadGateway"`); or the catalog filter answered `502` (forwarded): the federated catalog could not be resolved or reached, or answered a status that has no upstream exception that keeps the federated catalog message (`ObjectNotFoundException` has a fixed text), e.g. `404`, `500`, `503` (the message names it) |

Any other status of the catalog filter (e.g. `401`, `409` from the federated catalog) is forwarded the same way.

### Query the Federated Catalog (authority)

```http
POST /{{authorityId}}/{{federatedCatalogManagementAPI}}/v4/catalogs/request
Content-Type: application/json
```

**Request:**

```json
{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@type": "QuerySpec",
  "offset": 0,
  "limit": 50,
  "filterExpression": []
}
```

## Transfer Processes

### Initiate Transfer

```http
POST /{{consumerId}}/{{controlPlaneManagementApi}}/v4/transferprocesses
Content-Type: application/json
```

**Request:**

```json
{
    "@context": {
        "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
    },
    "@type": "TransferRequest",
    "connectorId": "{{providerDID}}",
    "counterPartyAddress": "{{protocol}}://{{apiGatewayHost}}/cp/dsp/{{providerId}}",
    "protocol": "dataspace-protocol-http:2025-1",
    "contractId": "{{contractAgreementId}}",
    "privateProperties": {},
    "transferType": "HttpData-PULL"
}
```

### Get Transfer Process

```http
GET /{{consumerId}}/{{controlPlaneManagementApi}}/v4/transferprocesses/{transferId}
```

### Get Transfer State

```http
GET /{{consumerId}}/{{controlPlaneManagementApi}}/v4/transferprocesses/{transferId}/state
```

### Terminate Transfer

```http
POST /{{consumerId}}/{{controlPlaneManagementApi}}/v4/transferprocesses/{transferId}/terminate
Content-Type: application/json
```

**Request:**

```json
{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@type": "TerminateTransfer",
  "reason": "a reason to terminate"
}
```

### Query Transfer Processes

```http
POST /{{consumerId}}/{{controlPlaneManagementApi}}/v4/transferprocesses/request
Content-Type: application/json
```

**Request:**

```json
{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@type": "QuerySpec",
  "offset": 0,
  "limit": 50,
  "filterExpression": [
    {
      "@type": "Criterion",
      "operandLeft": "state",
      "operator": "=",
      "operandRight": "STARTED"
    }
  ]
}
```

## Data Plane Access

### Access Data

```http
GET /{{consumerId}}/dp/data
Contract-Id: {{contractAgreementId}}
Authorization: {{contractAgreementId}}
```

## Authentication & Authorization

All Control Plane Management API endpoints require:

- Proper participant identification in the URL path
- Valid DID-based authentication for cross-participant communication

## Environment Variables Used

The following variables are commonly used in DSE requests:

- `{{protocol}}`: HTTP or HTTPS
- `{{apiGatewayHost}}`: API Gateway hostname  (hosted in port 80 -> localhost:80)
- `{{providerId}}`: Provider participant identifier
- `{{consumerId}}`: Consumer participant identifier
- `{{authorityId}}`: Authority participant identifier (for catalog queries)
- `{{controlPlaneManagementApi}}`: Management API path segment
- `{{federatedCatalogManagementAPI}}`: federated catalog management API path segment
- `{{apiGatewayKey}}`: API Management admin key
- `{{assetId}}`: Asset identifier
- `{{policy_definition_id}}`: Policy definition identifier
- `{{contract_definition_id}}`: Contract definition identifier
- `{{contract_id}}`: Contract negotiation identifier
- `{{transferId}}`: Transfer process identifier
- `{{providerDID}}`: Provider DID (e.g. `did:web:provider-identityhub:api:did`)
- `{{contractAgreementId}}`: Contract agreement identifier
- `{{policyId}}`: Policy (offer) ID extracted from the control-plane catalog response (`dataset[].hasPolicy[0].@id`)

## See Also

- [Quick Start](../../getting-started/quick-start.md) — Deploy services and start making API calls
- [API Overview](overview.md) — Common patterns, authentication, and end-to-end workflow
- **[Control Plane Architecture](../components/control-plane.md)** — Understand the component behind these APIs: how contract negotiation works, state machines, policy evaluation, and extension points
- [Data Plane API](data-plane-api.md) — After negotiating a contract here, use the Data Plane API to access the actual data
