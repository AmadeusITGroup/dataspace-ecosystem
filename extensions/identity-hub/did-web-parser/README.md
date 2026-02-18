# DID Web Parser Extension

This module provides a simplified DID (Decentralized Identifier) Web parser implementation for the Eclipse Dataspace Components (EDC) Identity Hub. It bypasses standard DID document resolution and directly returns the participant's configured DID.

## Overview

The DID Web Parser extension provides a `DidWebParser` implementation that:
- Ignores the provided DID URL
- Ignores the character encoding parameter
- Always returns the participant's configured DID from the EDC runtime context
- Bypasses standard `did:web` resolution mechanisms

## Components

### Extension Layer

#### `DidWebParserExtension`
The main service extension that provides the DID parser implementation:
- Implements `ServiceExtension` to integrate with EDC runtime
- Registers a `DidWebParser` provider
- Returns a parser implementation
- Uses the participant ID from `ServiceExtensionContext`

**Extension Name:** `"Did Web Parser"`

### Parser Implementation

#### Anonymous `DidWebParser` Implementation
A parser that:
- Implements the `DidWebParser` interface from Identity Hub SPI
- Accepts two parameters: `URI url` and `Charset charset`
- Returns `context.getParticipantId()` directly

## How It Works

This implementation:
1. Receives DID URL and charset (accepted due to method signature but not used)
2. Retrieves participant ID from service context
3. Returns participant ID directly

**Simplified Flow:**
```
Input: did:web:example.com:participant
Configuration: edc.participant.id=did:web:my-connector.example
→ parse() called
→ context.getParticipantId()
→ Returns: "did:web:my-connector.example"
```

## Dependencies

- **EDC Identity Hub SPI** - Provides `DidWebParser` interface
- **EDC Runtime** - Service extension framework
- **EDC SPI** - Core interfaces and context

## Extra Info
- This parser is intended for scenarios where the participant's DID is fixed and known, simplifying DID resolution. For participants within the cluster this implementation is fine as the DID is known, fixed and trusted. However, for self-hosted, a more robust DID resolution mechanism should be used to ensure proper verification and trustworthiness of DIDs.