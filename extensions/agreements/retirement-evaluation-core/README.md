# Agreement Retirement Evaluation Core

This module provides the functionality for retiring and reactivating contract agreements in the Eclipse Dataspace Components (EDC) framework. It prevents retired agreements from being used in transfer processes and policy monitoring.

## Overview

This module represents the core functionality used by the API through the implementation of the AgreementsRetirementService interface. The retirement evaluation system allows administrators to mark contract agreements as "retired", which prevents them from being used for data transfers. This is useful for deprecating agreements, handling security issues, or managing the lifecycle of data sharing contracts.

## Components

### Service Layer

#### `AgreementsRetirementServiceImpl`
Implementation of the AgreementsRetirementService that:
- Validates that agreements exist before retiring them
- Manages transactions using `TransactionContext`
- Delegates storage operations to `AgreementsRetirementStore`

### Validation Layer

#### `AgreementRetirementValidator`
A policy validator that intercepts policy evaluation for:
- **Transfer Process Context**: Validates agreements before initiating data transfers
- **Policy Monitor Context**: Validates agreements during ongoing policy monitoring

If an agreement is retired, the validator reports a problem and blocks the operation.

### Storage Layer

#### `InMemoryAgreementsRetirementStore`
Default in-memory implementation of AgreementsRetirementStore providing:
- Thread-safe storage using `ConcurrentHashMap`
- Query support using `QueryResolver`
- Duplicate detection and error handling

### Extension Layer

#### `AgreementRetirementServiceExtension`
Provides the `AgreementsRetirementService` as a service in the EDC runtime.

#### `DefaultAgreementRetirementStoreProviderExtension`
Provides the default in-memory store implementation. Can be overridden by custom implementations.

#### `AgreementsRetirementPreValidatorRegisterExtension`
Registers the retirement validator as a pre-validator in the policy engine for both:
- Transfer process policy evaluation
- Policy monitoring

The implementation of the AgreementsRetirementService and the AgreementRetirementValidator are used by the API layer to perform retirement operations and validation.

## How It Works

1. **Retirement Flow**:
   - An agreement is marked as retired through the service
   - The service validates the agreement exists
   - The entry is stored in the retirement store

2. **Validation Flow**:
   - When a transfer or policy monitoring occurs
   - The validator checks if the agreement is retired
   - If retired, the operation is blocked with an error message

3. **Reactivation Flow**:
   - A retired agreement can be reactivated
   - The entry is removed from the retirement store
   - The agreement can be used again


