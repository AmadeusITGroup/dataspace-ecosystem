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
POST /{{providerId}}/{{controlPlaneManagementApi}}/v3/assets/
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
GET /{{providerId}}/{{controlPlaneManagementApi}}/v3/assets/{assetId}
```

### Query Assets

```http
POST /{{providerId}}/{{controlPlaneManagementApi}}/v3/assets/request
Content-Type: application/json
```

**Request:**

```json
{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "querySpec": {
    "offset": 0,
    "limit": 50,
    "filterExpression": [
      {
        "operandLeft": "properties.contenttype",
        "operator": "=",
        "operandRight": "application/json"
      }
    ]
  }
}
```

### Delete Asset

```http
DELETE /{{providerId}}/{{controlPlaneManagementApi}}/v3/assets/{assetId}
```

## Policy Definitions

### Create Policy Definition

```http
POST /{{providerId}}/{{controlPlaneManagementApi}}/v3/policydefinitions
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
    "@type": "PolicyDefinitionDto",
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
GET /{{providerId}}/{{controlPlaneManagementApi}}/v3/policydefinitions/{policyId}
```

### Query Policy Definitions

```http
POST /{{providerId}}/{{controlPlaneManagementApi}}/v3/policydefinitions/request
Content-Type: application/json
```

### Delete Policy Definition

```http
DELETE /{{providerId}}/{{controlPlaneManagementApi}}/v3/policydefinitions/{policyId}
```

## Contract Definitions

### Create Contract Definition

```http
POST /{{providerId}}/{{controlPlaneManagementApi}}/v3/contractdefinitions
Content-Type: application/json
```

**Request:**

```json
{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@id": "{{contract_definition_id}}",
  "@type": "https://w3id.org/edc/v0.0.1/ns/ContractDefinition",
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
GET /{{providerId}}/{{controlPlaneManagementApi}}/v3/contractdefinitions/{contractDefId}
```

### Query Contract Definitions

```http
POST /{{providerId}}/{{controlPlaneManagementApi}}/v3/contractdefinitions/request
Content-Type: application/json
```

### Delete Contract Definition

```http
DELETE /{{providerId}}/{{controlPlaneManagementApi}}/v3/contractdefinitions/{contractDefId}
```

## Contract Negotiations

### Initiate Contract Negotiation

```http
POST /{{consumerId}}/{{controlPlaneManagementApi}}/v3/contractnegotiations
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
    "protocol": "dataspace-protocol-http",
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
            "@id": "{{providerDIDNoBase64}}"
        }
    }
}
```

### Get Contract Negotiation

```http
GET /{{consumerId}}/{{controlPlaneManagementApi}}/v3/contractnegotiations/{negotiationId}
```

### Query Contract Negotiations

```http
POST /{{consumerId}}/{{controlPlaneManagementApi}}/v3/contractnegotiations/request
Content-Type: application/json
```

### Get Contract Agreement

```http
GET /{{consumerId}}/{{controlPlaneManagementApi}}/v3/contractnegotiations/{negotiationId}/agreement
```

## Catalog

### Query Catalog

```http
POST /{{authorityId}}/{{catalogManagementAPI}}/v1alpha/catalog/query
Content-Type: application/json
```

**Request:**

```json
{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "counterPartyAddress": "{{protocol}}://{{apiGatewayHost}}/cp/dsp/{{providerId}}",
  "protocol": "dataspace-protocol-http",
  "querySpec": {
    "limit": 50
  }
}
```

## Transfer Processes

### Initiate Transfer

```http
POST /{{consumerId}}/{{controlPlaneManagementApi}}/v3/transferprocesses
Content-Type: application/json
```

**Request:**

```json
{
    "@context": {
        "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
    },
    "@type": "TransferRequest",
    "connectorId": "{{providerDIDNoBase64}}",
    "counterPartyAddress": "{{protocol}}://{{apiGatewayHost}}/cp/dsp/{{providerId}}",
    "protocol": "dataspace-protocol-http",
    "contractId": "{{contractAgreementId}}",
    "privateProperties": {},
    "transferType": "HttpData-PULL"
}
```

### Get Transfer Process

```http
GET /{{consumerId}}/{{controlPlaneManagementApi}}/v3/transferprocesses/{transferId}
```

### Get Transfer State

```http
GET /{{consumerId}}/{{controlPlaneManagementApi}}/v3/transferprocesses/{transferId}/state
```

### Terminate Transfer

```http
POST /{{consumerId}}/{{controlPlaneManagementApi}}/v3/transferprocesses/{transferId}/terminate
Content-Type: application/json
```

**Request:**

```json
{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@type": "https://w3id.org/edc/v0.0.1/ns/TerminateTransfer",
  "reason": "a reason to terminate"
}
```

### Query Transfer Processes

```http
POST /{{consumerId}}/{{controlPlaneManagementApi}}/v3/transferprocesses/request
Content-Type: application/json
```

**Request:**

```json
{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "querySpec": {
    "offset": 0,
    "limit": 50,
    "filterExpression": [
      {
        "operandLeft": "state",
        "operator": "=", 
        "operandRight": "STARTED"
      }
    ]
  }
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
- `{{catalogManagementAPI}}`: Catalog management API path segment
- `{{apiGatewayKey}}`: API Management admin key
- `{{assetId}}`: Asset identifier
- `{{policy_definition_id}}`: Policy definition identifier
- `{{contract_definition_id}}`: Contract definition identifier
- `{{contract_id}}`: Contract negotiation identifier
- `{{transferId}}`: Transfer process identifier
- `{{providerDIDNoBase64}}`: Provider DID without Base64 encoding
- `{{contractAgreementId}}`: Contract agreement identifier
- `{{policyId}}`: Policy ID extracted from catalog response

## See Also

- [Quick Start](../../getting-started/quick-start.md) — Deploy services and start making API calls
- [API Overview](overview.md) — Common patterns, authentication, and end-to-end workflow
- **[Control Plane Architecture](../components/control-plane.md)** — Understand the component behind these APIs: how contract negotiation works, state machines, policy evaluation, and extension points
- [Data Plane API](data-plane-api.md) — After negotiating a contract here, use the Data Plane API to access the actual data
