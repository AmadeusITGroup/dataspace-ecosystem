# Identity Hub IATP Extension

This module provides scope-to-criterion transformation functionality for the Eclipse Dataspace Components (EDC) Identity Hub, enabling the conversion of IATP (Identity and Trust Protocol) scope strings into query criteria for retrieving verifiable credentials.

## Overview

The Identity Hub IATP extension bridges the gap between IATP scope strings (used in OAuth-style authorization) and EDC's query criteria system. It allows external systems to request verifiable credentials using standardized scope notation (e.g., `dse:MembershipCredential:read`), which are then translated into internal query criteria for credential retrieval.

## Architecture

```
IATP Scope String → ScopeToCriterionTransformer → Query Criterion → Identity Hub → Verifiable Credentials
```

**Flow:**
1. External request includes scope string (e.g., `dse:MembershipCredential:read`)
2. `DseScopeToCriterionTransformer` parses and validates the scope
3. Transformer creates a `Criterion` for querying credentials by type
4. Identity Hub executes query against verifiable credential store
5. Matching credentials are returned

## Components

### Extension Layer

#### `IdentityHubIatpExtension`
The main service extension that provides the scope transformation capability:
- Implements `ServiceExtension` to integrate with EDC runtime
- Provides `ScopeToCriterionTransformer` as a service
- Enables Identity Hub to interpret IATP scope strings

**Extension Name:** `"DSE IATP"`


### Transformation Layer

#### `DseScopeToCriterionTransformer`
Implements the scope-to-criterion transformation logic:
- Parses IATP-compliant scope strings
- Validates scope format and operations
- Generates query criteria for credential type matching

## Scope String Format

### Components

| Component | Description | Example Values |
|-----------|-------------|----------------|
| **Scope Alias** | Namespace identifier | `dse` (required) |
| **Credential Type** | Type of verifiable credential | `MembershipCredential`, `DomainCredential`, `VerifiableCredential` |
| **Operation** | Access operation | `read`, `all`, `*` |

### Valid Scope Examples

```
dse:MembershipCredential:read
dse:DomainCredential:read
dse:VerifiableCredential:all
dse:CustomCredential:*
```

## Transformation Logic

### Input Processing

1. **Tokenization**: Split scope string by `:` separator
2. **Validation**: Check format, alias, and operation
3. **Extraction**: Extract credential type
4. **Criterion Creation**: Generate query criterion

### Output Criterion

The transformer creates a `Criterion` with:
- **Left Operand**: `verifiableCredential.credential.type`
- **Operator**: `contains`
- **Right Operand**: The credential type from the scope

**Example Transformation:**
```
Input:  dse:MembershipCredential:read

Output: Criterion {
    leftOperand: "verifiableCredential.credential.type",
    operator: "contains",
    rightOperand: "MembershipCredential"
}
```

## Validation Rules

### 1. Scope Alias Validation

**Rule:** Must be exactly `dse` (case-insensitive)

**Configuration:**
```java
DSE_VC_TYPE_SCOPE_ALIAS = "dse"
```

### 2. Format Validation

**Rule:** Must have exactly 3 components separated by `:`

**Valid:**
- `dse:MembershipCredential:read`


### 3. Operation Validation

**Rule:** Operation must be one of: `read`, `all`, `*`

**Allowed Operations:**
- `read` - Standard read operation
- `all` - Read all credentials of the type
- `*` - Wildcard, same as `all`

## Usage

### Requesting Credentials by Scope

When making requests to the Identity Hub with IATP scopes:

```http
POST /identity/v1/credentials/query
Authorization: Bearer <token>
Content-Type: application/json

{
  "scope": "dse:MembershipCredential:read"
}
```

### Multiple Scopes

Request multiple credential types:

```json
{
  "scopes": [
    "dse:MembershipCredential:read",
    "dse:DomainCredential:read"
  ]
}
```

### Wildcard Query

Request all credentials:

```json
{
  "scope": "dse:VerifiableCredential:all"
}
```

## Integration with Identity Hub

### Identity Hub Usage

The Identity Hub internally uses this transformer:

1. Receives IATP scope string in request
2. Calls `transformer.transform(scope)`
3. Gets `Criterion` for querying
4. Executes query against credential store
5. Returns matching verifiable credentials

## Credential Type Matching

### Contains Operator

The transformer uses the `contains` operator, which means:

**Credential Types:**
```json
{
  "@type": ["DomainCredential", "MembershipCredential"]
}
```

### Type Hierarchy Support

The `contains` operator supports credential type hierarchies:
- Base type: `VerifiableCredential`
- Specific types: `MembershipCredential`, `DomainCredential`, etc.

## Dependencies

- **EDC Identity Hub SPI** - Provides `ScopeToCriterionTransformer` interface
- **EDC SPI** - Query criterion model and result types
- **EDC Runtime** - Service extension framework

## Related Components

- **Identity Hub Core** - Uses this transformer for credential queries
- **IATP Protocol** - Defines scope string format
- **Verifiable Credentials** - The credentials being queried
- **Policy Evaluation** - May use scopes for authorization decisions

