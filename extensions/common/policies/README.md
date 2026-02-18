# Policy Framework Extensions

This module provides a comprehensive policy evaluation framework for the Eclipse Dataspace Components (EDC) that handles both inbound credential validation and outbound request scope extraction using the IATP (Identity and Trust Protocol).

## Overview

The policy system serves two complementary purposes:

1. **Inbound Request Validation**: Validates verifiable credentials presented by participants against policy constraints when accessing the resources
2. **Outbound Request Scope Extraction**: Automatically configures required credential scopes when making requests to other participants

## Components

### Extension Layer

#### `PolicyEvaluationExtension`
The main extension that bootstraps the policy evaluation framework:
- Registers JSON-LD namespaces for DSE policy vocabulary (`dse:`)
- Registers constraint functions for different policy contexts
- Binds constraint types to appropriate policy scopes

#### `IatpPatchExtension`
Configures IATP scope extraction for all request policy contexts:
- Registers post-validators that extract credential scope requirements
- Maps each policy context to its required credential scopes

**Credential Scopes:**
- `READ_MEMBERSHIP_CREDENTIAL_SCOPE` - `dse:MembershipCredential:read`
- `READ_DOMAIN_CREDENTIAL_SCOPE` - `dse:DomainCredential:read`
- `READ_ALL_CREDENTIAL_SCOPE` - `dse:VerifiableCredential:read`

## Policy Contexts

### `CatalogDiscoveryPolicyContext`
Specialized policy context for catalog discovery operations:
- Implements `ParticipantAgentPolicyContext` for agent-based evaluation
- Defines the `catalog.discovery` scope
- Provides access to the participant agent and their credentials

### `RequestCatalogDiscoveryContext`
Specialized request policy context for catalog discovery operations:
- Extends `RequestPolicyContext` 
- Defines the `request.catalog.discovery` scope
- Used when making catalog discovery requests at catalog discovery stage
- Carries the `RequestContext` and `RequestScope.Builder` for scope configuration

## Policy Scopes

1. **Catalog Scope** (`catalog.scope`)
   - Validates claims against policies when the federated catalog queries participant catalogs
   - Ensures federated catalog has appropriate credentials (Due to centralization, only Membership is required)

2. **Catalog Discovery Scope** (`catalog.discovery`)
   - Validates claims against policies when participants obtain the catalog, ensuring visibility restrictions on certain offerings
   - Ensures that offerings with correct prefix (RestrictedDiscoveryClaim) are filtered against participant credentials

3. **Negotiation Scope** (`contract.negotiation`)
   - Validates claims against policies during contract negotiation
   - Ensures both parties meet policy requirements

4. **Transfer Scope** (`transfer.process`)
   - Validates claims against policies before and during data transfers
   - Continuous validation throughout transfer lifecycle

### Request Scope Mapping

| Request Type | Policy Context | Required Scopes |
|--------------|----------------|-----------------|
| Catalog Query | `RequestCatalogPolicyContext` | `dse:MembershipCredential:read` |
| Contract Negotiation | `RequestContractNegotiationPolicyContext` | `dse:VerifiableCredential:read` |
| Transfer Process | `RequestTransferProcessPolicyContext` | `dse:VerifiableCredential:read` |
| Catalog Discovery | `RequestCatalogDiscoveryContext` | `dse:VerifiableCredential:read` |
| Telemetry Request | `RequestTelemetryPolicyContext` | `dse:MembershipCredential:read` |

## Constraint Functions

### `MembershipConstraintFunction`
Validates that participants possess an active membership credential:
- **Left Operand**: `dse:Membership`
- **Operator**: `EQ` (equals)
- **Right Operand**: `"active"`
- **Behavior**: Checks if the participant's credentials include a `MembershipCredential`
- **Use Case**: Verifying that a participant is an active member of a dataspace

### `JsonPathCredentialConstraintFunction`
Validates specific claims within verifiable credentials using JSON path expressions:
- **Left Operand**: `dse:GenericClaim.$.{CredentialType}.{path.to.claim}`
- **Operators**: `EQ`, `NEQ`, `IN`
- **Right Operand**: String value or List/array of strings (for IN operator)
- **Behavior**: Extracts a claim from a credential and compares it to the expected value
- **Use Case**: Fine-grained access control based on credential attributes

### `CatalogDiscoveryConstraintFunction`
Similar to `JsonPathCredentialConstraintFunction` but specifically for catalog discovery:
- **Left Operand**: `dse:RestrictedDiscoveryClaim.$.{CredentialType}.{path.to.claim}`
- **Operators**: `EQ`, `NEQ`, `IN`
- **Right Operand**: String value or List/array of strings
- **Behavior**: Validates claims for restricting which participants can discover catalogs
- **Use Case**: Implementing visibility rules for catalog discovery
- **Special Parsing**: Handles various array string formats including `['value1', 'value2']`

## How It Works

### Request Flow

1. **Request Initiation**: Participant makes a request (catalog query, catalog discovery, negotiation, transfer, telemetry) with verifiable credentials (claimToken)
2. **Credential Extraction**: Credentials are extracted and added to the `ParticipantAgent` under the `"vc"` key
3. **Policy Evaluation**: Policy engine evaluates constraints against the credentials
4. **Constraint Processing**: Each constraint function processes relevant credential claims
5. **Decision**: If all constraints pass, the request proceeds; otherwise, it's rejected

**Constraint Evaluation Process:**
1. Operator validation
2. Operand validation
3. Credential lookup by type
4. Claim extraction using JSON path
5. Claim sanitization (removes namespace prefixes: `https://w3id.org/dse#holderIdentifier` → `holderIdentifier`)
6. Comparison against expected value
7. Return result

## Base Classes and Utilities

### `AbstractDynamicCredentialConstraintFunction`
Abstract base class for all credential-based constraint functions:
- Implements `DynamicAtomicConstraintRuleFunction` for dynamic constraint evaluation
- Provides utility methods:
  - `getCredentialList()` - Extracts VC list from participant agent claims
  - `checkOperator()` - Validates that the operator is supported
- Expects credentials in the agent's claims under the `"vc"` key
- Defines common equality operators (`EQ`, `NEQ`)

### `CredentialTypePredicate`
A predicate for filtering credentials by type:
- Tests if a `VerifiableCredential` matches a specified type
- Uses suffix matching to handle different namespace formats

### `DefaultScopeExtractor<C extends RequestPolicyContext>`
A generic policy validator rule that extracts default credential scopes:
- Implements `PolicyValidatorRule` as a post-validator
- Takes a set of default scopes in its constructor
- Applies the scopes to the request scope builder during policy evaluation

## Supported Operators

| Operator | Functions | Description |
|----------|-----------|-------------|
| `EQ` | All | Equals - value must match exactly |
| `NEQ` | JsonPath, CatalogDiscovery | Not equals - value must not match |
| `IN` | JsonPath, CatalogDiscovery | In array - value must be in the list |

## Design Rationale

### Why Different Scopes?
Different request types have different credential requirements:
- **Membership-only** (catalog, telemetry): Lightweight, only requires membership proof
- **All credentials** (negotiation, transfer, discovery): May require domain-specific credentials for fine-grained access control

### Why Post-Validators?
Post-validators run after policy evaluation, ensuring that:
1. Policies are evaluated first
2. Scopes are extracted only for valid requests
3. Credential requirements are determined dynamically

## Integration Points

This module integrates with:
- **EDC Policy Engine** - Registers constraint functions and post-validators
- **IATP Protocol** - Configures credential scope requirements
- **Request Pipeline** - Processes all outbound requests
- **Verifiable Credentials** - Specifies which credential types to request

## Dependencies

- **EDC Policy Engine** - Core policy evaluation framework
- **EDC Verifiable Credentials SPI** - Credential models and utilities
- **EDC Request Context SPI** - Request policy context interfaces
- **EDC IAM SPI** - Request scope builder
- **JSON-LD** - JSON-LD processing and namespace management
- **Jackson** - JSON path evaluation (via `ReflectionUtil`)
- **Telemetry SPI** - Custom telemetry policy context

