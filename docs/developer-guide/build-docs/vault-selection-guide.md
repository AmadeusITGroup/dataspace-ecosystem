# Vault Type Selection Guide

## Overview

The Dataspace Ecosystem supports two vault implementations for secrets management. By selecting which vault type to build, you control which Docker images are created, reducing build time and storage when only one variant is needed.

| Vault Type | Value | Description |
|------------|-------|-------------|
| **HashiCorp Vault** | `hashicorp` | Default. Builds only HashiCorp Vault variants |
| **Azure Key Vault** | `azure` | Builds only Azure Key Vault variants |
| **Both** | `both` | Builds both variants (one image per vault type per service) |

## Usage

### Option 1: Command Line Parameter

=== "HashiCorp Vault (Default)"
    ```bash
    ./gradlew clean build podmanize -PvaultType=hashicorp
    # or load to Kind cluster
    ./gradlew loadToKind -PvaultType=hashicorp
    ```

=== "Azure Key Vault"
    ```bash
    ./gradlew clean build podmanize -PvaultType=azure
    # or load to Kind cluster
    ./gradlew loadToKind -PvaultType=azure
    ```

=== "Both Variants"
    ```bash
    ./gradlew clean build podmanize -PvaultType=both
    # or load to Kind cluster
    ./gradlew loadToKind -PvaultType=both
    ```

### Option 2: Modify `gradle.properties`

Edit the `gradle.properties` file in the project root directory:

```properties
# For HashiCorp Vault only (default)
vaultType=hashicorp

# For Azure Key Vault only
# vaultType=azure

# For both variants
# vaultType=both
```

Then run:

```bash
./gradlew clean build podmanize
# or
./gradlew loadToKind
```

!!! tip "Combining with Base Image Configuration"
    The `vaultType` parameter can be used alongside `baseImage`, `registryUrl`, and `installJava`:

    ```bash
    ./gradlew clean build podmanize \
      -PvaultType=hashicorp \
      -PregistryUrl=quay.io/centos \
      -PbaseImage=centos:latest \
      -PinstallJava=true
    ```

    See [Base Image Configuration](base-image-configuration.md) for details on image customization.

## How It Works

The build pipeline uses the `vaultType` property to determine which images to produce:

1. **Dependency Resolution**: Based on `vaultType`, the appropriate vault variant(s) are included as Gradle dependencies.
2. **Image Building**: Docker/Podman tasks are only created for launcher projects matching the selected vault type.
3. **Output**: The number of images per service depends on the selected type:

    | `vaultType` | Images per Service | Total Build Time |
    |-------------|-------------------|------------------|
    | `hashicorp` | 1 | Fastest |
    | `azure` | 1 | Fastest |
    | `both` | 2 | ~2× longer |

## Affected Services

### Vault-Dependent Services

Most services require a vault for secrets management (key storage, credential signing, etc.):

| Service                 | HashiCorp Variant | Azure Variant |
|-------------------------|-------------------|---------------|
| Control Plane           | :material-check: | :material-check: |
| Data Plane              | :material-check: | :material-check: |
| Identity Hub            | :material-check: | :material-check: |
| Federated Catalog       | :material-check: | :material-check: |
| Federated Catalog Filter | :material-check: | :material-check: |
| Issuer Service          | :material-check: | :material-check: |
| Telemetry Agent         | :material-check: | :material-check: |
| Telemetry Service       | :material-check: | :material-check: |
| Kafka Proxy K8s Manager | :material-check: | |

### Vault-Independent Services

These services do not require a vault and produce a single image regardless of the `vaultType` setting:

- Telemetry Storage
- Telemetry CSV Manager

## Image Naming Convention

Built images follow this naming pattern:

```
{service-name}-postgresql-{vault-type}vault:latest
```

**Examples:**

| Service | HashiCorp Image | Azure Image |
|---------|-----------------|-------------|
| Control Plane | `control-plane-postgresql-hashicorpvault:latest` | `control-plane-postgresql-azurevault:latest` |
| Data Plane | `data-plane-postgresql-hashicorpvault:latest` | `data-plane-postgresql-azurevault:latest` |
| Identity Hub | `identity-hub-postgresql-hashicorpvault:latest` | `identity-hub-postgresql-azurevault:latest` |

!!! note
    Vault-independent services (Telemetry Storage, Telemetry CSV Manager, Kafka Proxy K8s Manager) do not follow this naming convention, as they do not include a vault-specific suffix.

## Default Behavior

If no `vaultType` is specified, the build defaults to **`hashicorp`**.

```bash
# These two commands are equivalent
./gradlew clean build podmanize
./gradlew clean build podmanize -PvaultType=hashicorp
```

## See Also

- [Base Image Configuration](base-image-configuration.md) - Customize the Docker base image for all services
- [Build Overview](index.md) - Overview of all build options
- [Quick Start](../../getting-started/quick-start.md) - Full deployment walkthrough including image building
