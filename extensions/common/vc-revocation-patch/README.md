# Verifiable Credential Revocation Service Patch

This module provides a temporary patch for the Eclipse Dataspace Components (EDC) to bypass verifiable credential revocation checks while the revocation service implementation is being stabilized.

## Overview

The revocation service in EDC is currently not stable. This extension provides a no-operation implementation of the revocation service that allows verifiable credentials to pass revocation checks without actually validating against revocation lists. This is a temporary workaround to enable testing and development while the proper revocation infrastructure is being finalized.

## Unstable Revocation Service
This development is a temporary workaround since current EDC version (0.14.0) VC revocation is still unstable and not compatible with the architecture of DSE. Newer versions of EDC (0.15.0) will include a new implementation of the revocation service that will be compatible with DSE architecture and will allow us to remove this patch and use the proper revocation service implementation.

## Components

### Extension Layer

#### `RevocationServicePatchExtension`
The main extension that patches the revocation service registry:
- Implements `ServiceExtension` to integrate with EDC runtime
- Injects the `RevocationServiceRegistry` to register custom services
- Registers a no-operation revocation service for supported credential status types

### Revocation Service Implementation

#### `NoopRevocationListService`
A minimal implementation of `RevocationListService` that:
- **Always returns success** for revocation checks
- **Always returns null** for status purpose queries
- Bypasses all actual revocation list validation logic

## How It Works

### Initialization Flow

1. **Extension Startup**:
   - The extension is loaded by the EDC runtime
   - The `RevocationServiceRegistry` is injected

2. **Service Registration**:
   - A `NoopRevocationListService` instance is registered
   - This overrides any default revocation service implementations

3. **Runtime Behavior**:
   - When credentials are validated, the EDC checks revocation status
   - The registered `NoopRevocationListService` is invoked
   - All revocation checks immediately return success
   - No network calls are made to revocation lists
   - No actual validation occurs (to be replaced later in EDC 0.15.0)

## Dependencies

- **EDC Verifiable Credentials SPI** - Provides revocation interfaces
- **EDC Runtime** - Service extension framework
- **EDC SPI** - Result types and core interfaces

