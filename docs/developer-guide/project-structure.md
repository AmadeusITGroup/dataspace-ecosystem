# Project Structure

Understanding the organization of the Dataspace Ecosystem codebase.

## Repository Layout

```
dataspace-ecosystem/
├── core/                       # Core implementations
├── extensions/                 # Extended functionality
├── launchers/                  # Runtime configurations & variants
├── spi/                        # Service Provider Interfaces
├── plugins/                    # Gradle plugins
├── system-tests/               # Integration & E2E tests
├── charts/                     # Helm charts for Kubernetes
├── docs/                       # Documentation
├── resources/                  # Shared resources (checkstyle, OpenAPI specs)
├── gradle/                     # Gradle wrapper & configuration
└── build.gradle.kts            # Root build configuration
```

## Module Types

### Core Modules (`core/`)

Provide default implementations:

```
core/
├── common/                     # Shared core logic
├── kafka-proxy-k8s-manager-core/
├── telemetry-agent-core/
└── telemetry-service-core/
```


### Extension Modules (`extensions/`)

Add specific functionality and integrations:

```
extensions/
├── agreements/
│   ├── retirement-evaluation-core/
│   ├── retirement-evaluation-api/
│   ├── retirement-evaluation-spi/
│   └── retirement-evaluation-store-sql/
├── common/
├── control-plane/
│   ├── asset-custom-property-subscriber/
│   ├── transfer-data-plane-signal-kafka/
│   └── control-plane-federated-catalog-filter/
├── data-plane/
│   ├── data-plane-public-api-v2/
│   └── data-plane-data-consumption-metrics/
├── federated-catalog/
│   ├── participant-registry-node-directory/
│   └── filter/
├── identity-hub/
│   ├── did-web-parser/
│   ├── identity-hub-iatp/
│   └── superuser-seed/
├── issuer-service/
│   ├── membership-attestation-api/
│   ├── membership-attestation-store-sql/
│   ├── domain-attestation-api/
│   └── domain-attestation-store-sql/
├── telemetry-agent/
│   └── event-hub-telemetry-record-publisher/
├── telemetry-service/
│   ├── event-hub-credential-factory/
│   └── telemetry-service-credential-api/
├── telemetry-storage/
│   ├── telemetry-storage-api/
│   └── telemetry-storage-store-sql/
└── telemetry-csv-manager/
    └── telemetry-csv-manager-api/
```

### Launcher Modules (`launchers/`)

Wire components for runtime. Each vault-dependent service has a base configuration, plus vault variants:

```
launchers/
├── control-plane/
│   ├── control-plane-base/                          # Base configuration
│   ├── control-plane-postgresql-hashicorpvault/     # PostgreSQL + HashiCorp Vault
│   └── control-plane-postgresql-azurevault/         # PostgreSQL + Azure Vault
├── data-plane/
│   ├── data-plane-base/
│   ├── data-plane-postgresql-hashicorpvault/
│   └── data-plane-postgresql-azurevault/
├── identity-hub/
│   ├── identity-hub-base/
│   ├── identity-hub-postgresql-hashicorpvault/
│   └── identity-hub-postgresql-azurevault/
├── federated-catalog/
│   ├── federated-catalog-base/
│   ├── federated-catalog-postgresql-hashicorpvault/
│   └── federated-catalog-postgresql-azurevault/
├── federated-catalog-filter/
│   ├── federated-catalog-filter-base/
│   ├── federated-catalog-filter-postgresql-hashicorpvault/
│   └── federated-catalog-filter-postgresql-azurevault/
├── issuer-service/
│   ├── issuer-service-base/
│   ├── issuer-service-postgresql-hashicorpvault/
│   └── issuer-service-postgresql-azurevault/
├── telemetry-service/
│   ├── telemetry-service-base/
│   ├── telemetry-service-postgresql-hashicorpvault/
│   └── telemetry-service-postgresql-azurevault/
├── telemetry-agent/
│   ├── telemetry-agent-base/
│   ├── telemetry-agent-postgresql-hashicorpvault/
│   └── telemetry-agent-postgresql-azurevault/
├── telemetry-storage/
│   ├── telemetry-storage-base/
│   ├── telemetry-storage-postgresql-hashicorpvault/
│   └── telemetry-storage-postgresql-azurevault/
├── telemetry-csv-manager/
│   ├── telemetry-csv-manager-base/
│   ├── telemetry-csv-manager-postgresql-hashicorpvault/
│   └── telemetry-csv-manager-postgresql-azurevault/
└── kafka-proxy-k8s-manager/
    └── kafka-proxy-k8s-manager-base/
```

**Configuration Variants:**

| Variant | Description |
|---------|-------------|
| **base** | Contains base configuration without runtime dependencies |
| **postgresql-hashicorpvault** | PostgreSQL database + HashiCorp Vault for secrets |
| **postgresql-azurevault** | PostgreSQL database + Azure Key Vault for secrets |


## Service Provider Interfaces (SPI)

SPIs define contracts that can be implemented by extensions:

```
spi/
├── common-spi/                 # Shared interfaces
├── telemetry-agent-spi/        # Telemetry agent contracts
├── issuer-service-spi/         # Issuer service contracts
├── telemetry-service-spi/      # Telemetry service contracts
├── telemetry-storage-spi/      # Storage layer contracts
└── federated-catalog-filter-spi/  # Catalog filtering contracts
```

## Directory Reference

### `charts/`

Helm charts for production deployments in Kubernetes clusters:

- `control-plane/`: Control Plane chart
- `data-plane/`: Data Plane chart
- `identity-hub/`: Identity Hub chart
- `federated-catalog/`: Federated Catalog chart
- And more for each service...

### `system-tests/`

Integration and end-to-end tests:

- `runner/`: Test execution module
- `modules/`: Terraform modules for test infrastructure
- `main.tf`, `providers.tf`: Terraform configuration

## See Also

- [System Overview](../architecture/system-overview.md) - Architecture and design principles
- [Development Setup](setup/development-setup.md) - IDE configuration and local development
- [Build Guides](build-docs/index.md) - Docker image and vault configuration
