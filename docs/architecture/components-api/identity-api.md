# Identity API

The Identity API provides endpoints for managing decentralized identities, verifiable credentials, and presentations. This includes both the Identity Hub API for participant operations and the Issuer Service API for administrative functions.

## Base URLs

The participant-scoped paths use the upstream form `/v1beta/participants/<participantContextId>/…`. The `participantContextId` is the participant DID as is (e.g. `did:web:provider-identityhub:api:did`): no base64 encoding, and no port in the DID (see [DID Format](#did-format)).

### Identity Hub API
```
{{protocol}}://{{apiGatewayHost}}/ih/identity/v1beta
```

### Issuer Service API

**Setup Operations (attestations, credential definitions, holders):**
```
{{protocol}}://{{apiGatewayHost}}/authority/is/issueradmin/v1beta
```

**Runtime Operations (attestation-membership):**
```
{{protocol}}://{{apiGatewayHost}}/{{authorityId}}/is/issueradmin/v1beta
```

## Credential Management

### Request Credential

Participants can request verifiable credentials from issuers:

```http
POST {{protocol}}://{{apiGatewayHost}}/ih/identity/v1beta/participants/{{participantDID}}/credentials/request
Content-Type: application/json
participant: {{participant}}
```

Where:
- `{{participantDID}}`: Can be `{{providerDID}}`, `{{consumerDID}}`, or `{{authorityDID}}`
- `{{participant}}`: Can be `name of the participant` (for Provider/Consumer) or `authority` (for Authority)
- `holderPid`: the holder-side id of this request. IdentityHub 0.18 rejects a request whose `holderPid` already exists (`409 Conflict`), so send a new value every time, e.g. a UUID (the Bruno requests generate one in their pre-request script). If it is omitted, IdentityHub assigns a random one. The request state is returned by `GET …/v1beta/participants/{{participantDID}}/credentials/request/<holderPid>`.

**Request:**

```json
{
    "issuerDid": "{{authorityDID}}",
    "holderPid": "<new UUID per request>",
    "credentials": [
    {
        "id": "membership-credential-id",
        "type": "MembershipCredential",
        "format": "vc11-sl2021/jwt"
    }
    ]
}
```

`format` is a DCP credential profile: `vc11-sl2021/jwt` (VC 1.1 as JWT, the profile of the DSE credential definitions) or `vc20-bssl/jwt` (VC 2.0). IdentityHub 0.18 accepts the old `CredentialFormat` enum names only as a fallback.

## Issuer Administration API

### Create Attestation Definition

Define attestation templates for credential issuance:

```http
POST /authority/is/issueradmin/v1beta/participants/{{authorityDID}}/attestations
Content-Type: application/json
```

**Request:**

```json
{
    "attestationType": "database",
    "configuration": {
        "dataSourceName": "membership",
        "tableName": "membership_attestation"
    },
    "id": "membership-attestation-def-1"
}
```

### Create Credential Definition

Define credential types and their structure:

```http
POST /authority/is/issueradmin/v1beta/participants/{{authorityDID}}/credentialdefinitions
Content-Type: application/json
```

**Request:**

```json
{
    "attestations": [
        "membership-attestation-def-1"
    ],
    "credentialType": "MembershipCredential",
    "dataModel": "V_1_1",
    "id": "membership-credential-def-1",
    "jsonSchema": "{}",
    "format": "vc11-sl2021/jwt",
    "validity": 315576000,
    "jsonSchemaUrl": "https://example.com/schema/membership-credential.json",
    "mappings": [
        {
            "input": "name",
            "output": "credentialSubject.name",
            "required": "true"
        },
        {
            "input": "membership_type",
            "output": "credentialSubject.membership.membershipType",
            "required": "true"
        },
        {
            "input": "membership_start_date",
            "output": "credentialSubject.membership.since",
            "required": "true"
        }
    ],
    "rules": []
}
```

### Create Holder

Register a new credential holder:

```http
POST /authority/is/issueradmin/v1beta/participants/{{authorityDID}}/holders
Content-Type: application/json
```

**Request:**

```json
{
    "holderId": "did:web:{{participantId}}-identityhub:api:did",
    "name": "{{participantId}}",
    "did": "did:web:{{participantId}}-identityhub:api:did"
}
```

### Create Attestation Membership

Attest to a participant's membership credentials:

```http
POST /{{authorityId}}/is/issueradmin/v1beta/participants/{{authorityDID}}/attestation-membership
Content-Type: application/json
```

**Request:**

```json
{
    "id": "did:web:{{participantId}}-identityhub:api:did",
    "name": "{{participantId}}",
    "holderId": "did:web:{{participantId}}-identityhub:api:did",
    "membershipType": "FullMember"
}
```

## DID Format

Participant DIDs follow the pattern:
```
did:web:{{participantId}}-identityhub:api:did
```

Where:
- `{{participantId}}`: The participant identifier (providerId, consumerId, authorityId)
- `{{participantId}}-identityhub`: the identity hub Kubernetes Service, without port. The Service exposes the DID endpoint (container port 8383) on the default port of the scheme, 443 (80 when `did.web.useHttps` is false), so the DID resolves to `https://{{participantId}}-identityhub/api/did/did.json`
- `api:did`: API path for DID document

The DID is also the `participantContextId` in the Identity and Issuer-admin API paths, used as is: `/v1beta/participants/did:web:{{participantId}}-identityhub:api:did/…`. A DID with a port (`%3A8383`) cannot be used there: it would need `%253A` in the path, which Jetty rejects (`400 Ambiguous URI path encoding`).

## Environment Variables Used

The following variables are commonly used in identity API requests:

- `{{protocol}}`: HTTP or HTTPS
- `{{apiGatewayHost}}`: API Gateway hostname
- `{{providerId}}`: Provider participant identifier  
- `{{consumerId}}`: Consumer participant identifier
- `{{authorityId}}`: Authority participant identifier
- `{{providerDID}}`: Provider DID identifier for credential requests
- `{{consumerDID}}`: Consumer DID identifier for credential requests
- `{{authorityDID}}`: Authority DID identifier for admin operations

## Credential Types

### MembershipCredential

Standard credential type for dataspace membership:

**Format**: `vc11-sl2021/jwt`
**Schema**: `https://example.com/schema/membership-credential.json`

**Subject Structure**:
```json
{
  "credentialSubject": {
    "name": "participant-name",
    "membership": {
      "membershipType": "FullMember",
      "since": "2024-01-01T00:00:00Z"
    }
  }
}
```

## Error Handling

### Common Error Codes

| Code | Description |
|------|-------------|
| `400 Bad Request` | Invalid request format or missing parameters |
| `401 Unauthorized` | Invalid API key or authentication |
| `403 Forbidden` | Insufficient permissions for operation |
| `404 Not Found` | Participant or resource not found |
| `409 Conflict` | Resource already exists |
| `500 Internal Server Error` | Identity service error |

## See Also

- [Quick Start](../../getting-started/quick-start.md) — Deploy services and start making API calls
- [API Overview](overview.md) — Common patterns, authentication, and end-to-end workflow
- **[Identity Hub Architecture](../components/identity-hub.md)** — Understand the component behind these APIs: DID resolution, VC authorization flows, onboarding process, and extension points
- [Control Plane API](control-plane-api.md) — After obtaining credentials via the Identity API, use the Control Plane API to discover data and negotiate contracts
