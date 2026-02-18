# Participant Registry Node Directory Extension

## Overview

This extension provides a dynamic `TargetNodeDirectory` implementation for the Eclipse Dataspace Components (EDC) federated catalog crawler. It automatically discovers and resolves participant nodes from a holder registry, enabling the federated catalog to crawl all registered participants in the dataspace.

## Components

### Core Classes

#### `ParticipantTargetNodeDirectory`
- **Type**: TargetNodeDirectory Implementation
- **Purpose**: Provides a directory of target nodes for the catalog crawler based on registered participants
- **Key Features**:
  - Queries the `HolderStore` to retrieve all registered participants
  - Resolves each participant's DID to a target node

#### `ParticipantToTargetNodeResolver`
- **Type**: Function<Holder, Result<TargetNode>>
- **Purpose**: Resolves participant holder information into crawler-compatible target nodes
- **Resolution Process**:
  1. Resolves the participant's DID to a DID document
  2. Extracts the `DSPMessaging` service endpoint from the DID document
  3. Creates a `TargetNode` with the DSP protocol configuration

#### `ParticipantTargetNodeDirectoryExtension`
- **Type**: Service Extension
- **Extension Name**: "Participant Target Node Directory"
- **Purpose**: Bootstraps and configures the participant-based target node directory
- **Dependencies**:
  - `HolderStore`: Registry of participant holders
  - `TransactionContext`: Transaction management for store operations
  - `DidResolverRegistry`: DID resolution service
  - `Monitor`: Logging and monitoring

## Architecture

```
┌────────────────────────────────┐
│  Federated Catalog Crawler     │
└──────────────┬─────────────────┘
               │ getAll()
               ↓
┌────────────────────────────────────────┐
│  ParticipantTargetNodeDirectory        │
│  - Queries HolderStore                 │
│  - Filters current participant         │
│  - Resolves to target nodes            │
└──────────────┬─────────────────────────┘
               │
               ↓
┌────────────────────────────────────────┐
│  ParticipantToTargetNodeResolver       │
│  - Resolves DID document               │
│  - Extracts DSPMessaging endpoint      │
└──────────────┬─────────────────────────┘
               │
               ├──→ DidResolverRegistry
               │    (Resolves participant DID)
               │
               └──→ HolderStore
                    (Source of participant data)
```

## Data Flow

1. **Query**: Crawler calls `getAll()` on the directory
2. **Retrieve**: Directory queries `HolderStore` for all registered participants
3. **Filter**: Current participant (self) is filtered out using `participantContextId`
4. **Resolve**: Each participant holder is passed to `ParticipantToTargetNodeResolver`
5. **DID Resolution**: Resolver fetches the DID document for each participant
6. **Endpoint Extraction**: DSP Messaging endpoint is extracted from the DID document
7. **Target Node Creation**: `TargetNode` objects are created with:
   - Participant context ID
   - Participant DID
   - DSP endpoint URL
   - Supported protocols (DATASPACE_PROTOCOL_HTTP)
8. **Error Handling**: Failed resolutions are logged but don't halt the process
9. **Return**: List of successfully resolved target nodes is returned to the crawler

## DID Document Requirements

For successful resolution, each participant's DID document must contain a service entry with:
- **ServiceEndpoint**: Valid URL for DSP communication

## Benefits

- **Dynamic Discovery**: Automatically discovers new participants added to the holder registry
- **Self-Exclusion**: Prevents the crawler from attempting to crawl itself
- **Resilient**: Individual resolution failures don't prevent crawling other participants
- **Centralized Management**: Participant list is managed through the existing holder registry
- **Protocol Compliance**: Ensures only DSP-compatible endpoints are used

## Dependencies

- EDC Core (Monitor, TransactionContext, ServiceExtension)
- EDC Crawler SPI (TargetNode, TargetNodeDirectory)
- EDC DID SPI (DidDocument, DidResolverRegistry)
- EDC Issuer Service SPI (Holder, HolderStore)
- DSP Protocol (DATASPACE_PROTOCOL_HTTP)

