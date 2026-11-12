# Participant Context Seed Extension

This module provides automatic creation of a super-user participant context in the Eclipse Dataspace Components (EDC) Identity Hub. It bootstraps the initial administrative user during startup, enabling immediate access to Identity Hub management functions.

## Overview

The Participant Context Seed extension automatically creates a "super-user" (root user) participant context with administrative privileges when the Identity Hub starts. This is essential for initial setup and configuration, as it provides the first administrative account that can then create and manage other participants.

## Purpose

In a fresh Identity Hub deployment:
- No participant contexts exist initially
- Administrative access is required to create the first participants

## Components

### Extension Layer

#### `ParticipantContextSeedExtension`
The main extension that creates the super-user participant context:
- Implements `ServiceExtension` to integrate with EDC runtime
- Runs during the `start()` lifecycle phase
- Creates a participant context with admin role
- Configures DID, keys, and service endpoints

**Extension Name:** `"Root User Seeding"`

## How It Works

### Startup Flow

1. **Extension Initialization**:
   ```
   initialize() called
   → Retrieves participant ID from context
   → Stores for later use
   ```

2. **Extension Start**:
   ```
   start() called
   → Check if super-user already exists
   → Retrieve public key from vault
   → Create participant context
   
3. **Existence Check**:
   ```
   Query ParticipantContextService
   → If participant exists:
     → Log "already exists"
     → Skip creation
   → If not exists:
     → Continue with creation
   ```

4. **Key Resolution**:
   ```
   Resolve public key from vault
   → If found:
     → Continue with creation
   → If not found:
     → Throw EdcException
   ```

5. **Participant Creation**:
   ```
   Build ParticipantManifest:
     - participantId: from configuration
     - did: same as participantId
     - active: true
     - key: KeyDescriptor with public/private key aliases
     - roles: [ROLE_ADMIN]
     - serviceEndpoints: from configuration
   
   Create participant context
   → If successful: Done
   → If failed: Throw EdcException
   ```

## Administrative Role

### ROLE_ADMIN

The super-user is automatically assigned the `ROLE_ADMIN` role, which grants:
- Full access to all Identity Hub APIs
- Ability to create and manage other participants
- Ability to issue and revoke credentials
- Ability to manage keys and DID documents
- All administrative operations

## Dependencies

- **EDC Identity Hub SPI** - Participant context service and models
- **EDC Vault** - Secure key storage
- **EDC Runtime** - Service extension framework
- **EDC Type Manager** - JSON serialization/deserialization
- **Jackson** - JSON processing for service endpoints

## Related Components

- **Identity Hub Core** - Uses the created super-user
- **Participant Context Service** - Manages participant contexts
- **Vault** - Stores cryptographic keys securely
- **DID Resolution** - Resolves the super-user's DID
