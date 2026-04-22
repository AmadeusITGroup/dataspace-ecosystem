# Base Docker Image Configuration

## Overview

The project allows customization of the base Docker image used for building all service containers. This provides flexibility to:

- Use different JRE distributions (Eclipse Temurin, Amazon Corretto, etc.)
- Switch between image registries
- Use custom base images
- Test with different Java versions or OS distributions

## Default Base Image

By default, the project uses CentOS with OpenJDK 21:

```properties
registryUrl=quay.io/centos
baseImage=centos:latest
installJava=true
```

This uses a RHEL-compatible base image with Java installed via `yum`. You can override these settings to use alternative images as needed.

## Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `baseImage` | `centos:latest` | The Docker image name (without registry prefix). Examples: `centos:stream9`, `ubi9-minimal:latest` |
| `registryUrl` | `quay.io/centos` | The container registry URL. Examples: `quay.io`, `registry.access.redhat.com`, or a custom registry |
| `installJava` | `true` | Whether to install Java 21 via `yum` during image build. Set to `false` if your base image already includes a JRE |

## Usage

### Option 1: Command Line Parameter

=== "Custom Base Image"
    ```bash
    ./gradlew clean build podmanize \
      -PregistryUrl=quay.io/centos \
      -PbaseImage=centos:stream9

    # or with Docker
    ./gradlew clean build dockerize \
      -PregistryUrl=quay.io/centos \
      -PbaseImage=centos:stream9
    ```

=== "Pre-built JRE Image"
    ```bash
    ./gradlew clean build podmanize \
      -PregistryUrl=docker.io \
      -PbaseImage=eclipse-temurin:21-jre \
      -PinstallJava=false
    ```

### Option 2: Modify `gradle.properties`

Edit the `gradle.properties` file in the project root directory:

```properties
# Default (CentOS with OpenJDK 21)
registryUrl=quay.io/centos
baseImage=centos:latest
installJava=true

# Alternative: use a pre-built JRE image
# registryUrl=docker.io
# baseImage=eclipse-temurin:21-jre
# installJava=false
```

Then run:

```bash
./gradlew clean build dockerize
# or
./gradlew clean build podmanize
```

!!! tip "Combining with Vault Type Selection"
    All base image parameters can be used together with the `vaultType` parameter:

    ```bash
    ./gradlew clean build podmanize \
      -PvaultType=hashicorp \
      -PregistryUrl=quay.io/centos \
      -PbaseImage=centos:latest \
      -PinstallJava=true
    ```

    See [Vault Selection Guide](vault-selection-guide.md) for more information on the `vaultType` parameter.

## Validation

To verify the base image is being used correctly:

1. **Check build output** for the `FROM` statement:
    ```
    FROM quay.io/centos/centos:latest
    ```

2. **Inspect the built image**:
    ```bash
    podman history <image-name>:latest
    # or
    docker history <image-name>:latest
    ```

3. **Check Java version in container**:
    ```bash
    podman run --rm <image-name>:latest java -version
    # or
    docker run --rm <image-name>:latest java -version
    ```

4. **Verify OS and package manager**:
    ```bash
    podman run --rm <image-name>:latest cat /etc/os-release
    ```

## Docker Image Structure

All Dockerfiles use the following pattern:

```dockerfile
ARG REGISTRY_URL="quay.io/centos"
ARG BASE_IMAGE="centos:latest"
FROM ${REGISTRY_URL}/${BASE_IMAGE}
# Set INSTALL_JAVA=false only if the base image already includes a JRE.
ARG INSTALL_JAVA=true

# Optionally install Java 21 via yum
RUN if [ "$INSTALL_JAVA" = "true" ]; then yum update -y && yum clean all; fi
RUN if [ "$INSTALL_JAVA" = "true" ]; then yum install java-21-openjdk -y && yum clean all; fi

# Create a group and user (non-root) with fixed UID/GID
RUN groupadd -g 1000 appuser && useradd -u 1000 -g appuser -s /bin/sh -m appuser
```

| Argument | Gradle Property | Default | Description |
|----------|----------------|---------|-------------|
| `REGISTRY_URL` | `registryUrl` | `quay.io/centos` | Container registry URL |
| `BASE_IMAGE` | `baseImage` | `centos:latest` | Docker image name |
| `INSTALL_JAVA` | `installJava` | `true` | Set to `false` if the base image already includes a JRE |

!!! info "Security"
    Containers run as a non-root user `appuser` (UID/GID `1000`) for improved security.

## Common Base Image Examples

=== "CentOS with OpenJDK 21 (Default)"
    ```bash
    ./gradlew podmanize
    # Uses default: quay.io/centos/centos:latest with java-21-openjdk
    ```

=== "CentOS Stream 9"
    ```bash
    ./gradlew podmanize \
      -PregistryUrl=quay.io/centos \
      -PbaseImage=centos:stream9
    ```

=== "Eclipse Temurin (JRE included)"
    ```bash
    ./gradlew podmanize \
      -PregistryUrl=docker.io \
      -PbaseImage=eclipse-temurin:21-jre-alpine \
      -PinstallJava=false
    ```

=== "Custom Image"
    ```bash
    ./gradlew podmanize \
      -PregistryUrl=<your-registry> \
      -PbaseImage=<your-image> \
      -PinstallJava=false
    ```

!!! warning "RHEL Compatibility"
    If `installJava=true` (default), the base image must support `yum`/`dnf`. If you use a non-RHEL-family image that already includes Java 21, set `-PinstallJava=false`.

## Affected Services

All Docker-based services use the configurable base image:

| Service | Configurable |
|---------|:------------:|
| Control Plane | :material-check: |
| Data Plane | :material-check: |
| Identity Hub | :material-check: |
| Federated Catalog | :material-check: |
| Issuer Service | :material-check: |
| Telemetry Agent | :material-check: |
| Telemetry Service | :material-check: |
| Telemetry CSV Manager | :material-check: |
| Kafka Proxy K8s Manager | :material-check: |

## Default Behavior

If no parameters are specified, the build defaults to:

| Parameter | Default Value |
|-----------|---------------|
| **Registry** | `quay.io/centos` |
| **Image** | `centos:latest` |
| **Install Java** | `true` (installs `java-21-openjdk` via `yum`) |

You can override these defaults either via command-line parameters or by modifying `gradle.properties`.

## Init Container: `cert-installer`

All component Helm charts include a `cert-installer` init container that runs before the main application container. Its purpose is to build a Java truststore containing the Vault CA certificate, so the main container can establish a trusted TLS connection to HashiCorp Vault.

### How It Works

1. Copies the system Java truststore from the image (`/etc/pki/ca-trust/extracted/java/cacerts`) into a shared `emptyDir` volume mounted at `/opt/ca`
2. Sets world-readable permissions (`chmod 666`) so the main container can read the file
3. Imports the Vault CA certificate (from a Kubernetes Secret) into the truststore via `keytool`
4. The main container mounts the same `emptyDir` at `/shared` and reads the truststore through `JAVA_TOOL_OPTIONS`

### Using a Different Image for the Init Container

- Set `certInstallerImage` to any image that has Java (`keytool`) available
- Update the `cp` source path in `initContainers` inside `values.yaml` to match the truststore location for your image, as it differs per JRE distribution
- To find the truststore path in your image:

    ```bash
    docker run --rm <your-image> find / -name "cacerts" 2>/dev/null
    ```

## See Also

- [Vault Selection Guide](vault-selection-guide.md) - Choose between HashiCorp and Azure vault
- [Build Overview](index.md) - Overview of all build options
- [Quick Start](../../getting-started/quick-start.md) - Full deployment walkthrough
