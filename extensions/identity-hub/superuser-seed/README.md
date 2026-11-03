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
     → Check the DID document services (see "DID Document Services Sync")
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

## DID Document Services Sync

`edc.ih.api.superuser.services` is only used when the participant context is created. Without the sync, changing a
service endpoint in the configuration (for example the `FederatedCatalogService` URL) never reaches an environment that
already exists.

When the participant already exists, the extension compares the configured services with the stored DID document,
matching them by `id`. A configured service is outdated if its id is missing, present more than once, or has a different
`type` or `serviceEndpoint`.

| `edc.ih.api.superuser.services.sync` | Behaviour |
|---|---|
| `false` (default) | Each outdated service is logged as a `WARNING` (old and new value). Nothing is written. |
| `true` | In one transaction, each outdated service replaces all entries with its id (at the position of the first one, or appended when absent), the DID resource is stored, and the DID document is published again. |

- Services whose id is not in the configuration (e.g. added through the Identity API) are never touched.
- Keys, credentials and the participant context are not changed.
- The upstream `PATCH /dids/{did}/endpoints` (`DidDocumentService.replaceService`) is not used: in IdentityHub 0.18.1 it
  appends the new entry instead of replacing it. Duplicates it left behind are removed by the sync.
- A failed store update fails the startup. A failed re-publication (failed result or exception) is only logged: a
  document that is already published is served from the store, so the new value is visible anyway.
- An invalid configuration (a service without `id`, `type` or `serviceEndpoint`, or an id configured twice) is logged
  as a `WARNING` and the check is skipped; the startup continues.
- The DID resolvers pick a service by `type` (first match). Another stored entry with the type of a configured service
  but a different id is not touched by the sync and is logged as a `WARNING`: remove it through the Identity API.
- No row lock: a change made to the same DID document through the Identity API while the sync runs can be overwritten.
  Do not edit the Super-User DID document while IdentityHub pods are starting with `true`.

Chart value: `identityhub.participantcontext.superuser.syncServices` (env `EDC_IH_API_SUPERUSER_SERVICES_SYNC`).

**Rollout:** deploy with `false` and check the `WARNING` lines in each environment, then set `true` for one deployment.
Once the warnings are gone, set it back to `false`: with `false` and an up-to-date document only one `INFO` line (the
parsed value) is logged, and while an environment still differs every restart repeats the warnings.

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
