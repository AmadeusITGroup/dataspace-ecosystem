# Key Concepts

This page explains the fundamental concepts used throughout the Dataspace Ecosystem.

## Core Concepts

### Participant

A **Participant** is any organization or entity that joins the dataspace. Participants can act as:

- **Data Providers**: Organizations that offer data assets
- **Data Consumers**: Organizations that request and use data
- **Both**: Most participants act in both roles

### Asset

An **Asset** represents any resource that can be shared within the dataspace:

- Datasets (files, databases)
- APIs and services

### Policy

A **Policy** defines the rules and conditions for using an asset:

```json
{
    "odrl:action": {
      "@id": "odrl:use"
    },
    "odrl:constraint": {
      "odrl:leftOperand": {
        "@id": "dse-policy:GenericClaim.$.MembershipCredential.name"
      },
      "odrl:operator": {
        "@id": "odrl:eq"
      },
      "odrl:rightOperand": "testparticipant"
    }
  }
```

This policy uses the **ODRL (Open Digital Rights Language)** standard to define data usage rules. Let's break down each component:

#### Policy Components

- **`odrl:action`**: Defines the permitted action
    - `"odrl:use"` - Allows general usage of the asset (read, access, process)

- **`odrl:constraint`**: Defines conditions that must be satisfied for the action to be permitted
  - **`odrl:leftOperand`**: What is being evaluated
    - `"dse-policy:GenericClaim.$.MembershipCredential.name"`: Extracts the "name" field from a MembershipCredential in the participant's Verifiable Credential
  - **`odrl:operator`**: The comparison operation
    - `"odrl:eq"` : Equals (exact match)
  - **`odrl:rightOperand`**: The value to compare against
    - `"testparticipant"` : The required participant name

#### Policy Evaluation

This policy effectively states: *"Allow usage of this asset only if the requesting participant has a MembershipCredential where the name field equals 'testparticipant'"*

When a participant requests access:
1. The system extracts their Verifiable Credentials
2. It looks for a MembershipCredential
3. It checks if the `name` field equals "testparticipant"
4. Access is granted only if this condition is satisfied


### Contract

A **Contract** is an agreement between a provider and consumer that binds a policy to an asset:

1. **Contract Definition**: Provider publishes available terms
2. **Contract Negotiation**: Consumer requests and negotiates
3. **Contract Agreement**: Binding agreement after successful negotiation

### Catalog

The **Catalog** contains all available data offerings from participants:

- Metadata about assets
- Associated policies and contracts
- Discovery and search capabilities

## Identity Concepts

### Decentralized Identifier (DID)

A **DID** is a globally unique identifier that enables verifiable, decentralized digital identity:

```
did:web:example.com:participant1
```

### Verifiable Credential (VC)

A **Verifiable Credential** is a tamper-proof claim about a participant:

- Membership credentials
- Compliance certifications
- Business attributes

### Verifiable Presentation (VP)

A **Verifiable Presentation** allows participants to present one or more credentials for verification.

## Protocol Concepts

### IDS Protocol

The International Data Spaces (IDS) protocol defines the standard communication patterns:

- Catalog Protocol
- Contract Negotiation Protocol
- Transfer Process Protocol

### Catalog Protocol

The **Catalog Protocol** enables discovery and browsing of data offerings:

1. **Catalog Request**: Consumer queries available datasets
2. **Catalog Response**: Provider returns catalog with assets, policies, and contract definitions
3. **Dataset Request**: Consumer requests detailed information about specific assets

### Contract Negotiation Protocol

The **Contract Negotiation Protocol** handles agreement establishment between participants:

1. **Contract Request**: Consumer initiates negotiation with desired terms
2. **Contract Offer**: Provider responds with counter-offer or acceptance
3. **Contract Agreement**: Final binding contract established
4. **Contract Verification**: Both parties verify the agreed terms

### Transfer Process

A **Transfer Process** orchestrates the actual data exchange:

1. **Transfer Request**: Consumer initiates data transfer after successful contract negotiation
2. **Transfer Preparation**: Provider prepares data access and establishes transfer channel
3. **Data Exchange**: Actual data flow occurs from provider to consumer
4. **Transfer Confirmation**: Both parties verify successful completion of data transfer

## Architecture Concepts

### Control Plane

The **Control Plane** handles all management operations:

- Catalog management
- Contract negotiations
- Policy evaluation
- Asset management

### Data Plane

The **Data Plane** handles actual data movement:

- Data source connections
- Data transformations
- Transfer protocols (HTTP, S3, etc.)

### Extension Points

The ecosystem is built on an extensible architecture:

- **SPI (Service Provider Interface)**: Define contracts
- **Extensions**: Implement specific functionality
- **Launchers**: Configure runtime behavior

## Next Steps

- Explore the [System Architecture](../architecture/system-overview.md)
- Start with the [Quick Start Guide](../getting-started/quick-start.md)
