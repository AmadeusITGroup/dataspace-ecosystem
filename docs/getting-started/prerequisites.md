# Prerequisites

Before you begin working with the Dataspace Ecosystem, ensure you have the following prerequisites installed and configured.

## System Requirements

### Hardware

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| CPU | 4 cores | 8+ cores |
| RAM | 8 GB | 16+ GB |
| Disk | 20 GB | 50+ GB SSD |

### Operating System

- macOS 12+ (Monterey or later)
- Linux (Ubuntu 20.04+, Debian 11+, RHEL 8+)
- Windows 10/11 with WSL2

## Required Software

### Java Development Kit (JDK)

**Version**: JDK 17 or later (LTS recommended)

=== "macOS (Homebrew)"
    ```bash
    brew install openjdk@17
    export JAVA_HOME=/opt/homebrew/opt/openjdk@17
    ```

=== "Linux (apt)"
    ```bash
    sudo apt update
    sudo apt install openjdk-17-jdk
    ```

=== "Windows"
    Download from [Adoptium](https://adoptium.net/) or use:
    ```powershell
    winget install EclipseAdoptium.Temurin.17.JDK
    ```

Verify installation:
```bash
java -version
```

### Gradle

**Version**: 8.0 or later

The project includes Gradle wrapper, so manual installation is optional:

```bash
./gradlew --version
```

### Docker or Podman

**Version**: Docker 20.10+ / Podman 4.0+ (with Docker Compose 2.0+ or Podman Compose)

Either Docker or Podman can be used as the container runtime.

=== "Docker (macOS)"
    ```bash
    brew install --cask docker
    ```

=== "Docker (Linux)"
    Follow [Docker Engine installation](https://docs.docker.com/engine/install/)

=== "Docker (Windows)"
    Download [Docker Desktop](https://www.docker.com/products/docker-desktop/)

=== "Podman (macOS)"
    ```bash
    brew install podman
    podman machine init
    podman machine start
    ```

=== "Podman (Linux)"
    ```bash
    # Fedora/RHEL
    sudo dnf install podman podman-compose
    
    # Ubuntu/Debian
    sudo apt install podman podman-compose
    ```

Verify installation:
```bash
# Docker
docker --version
docker compose version

# Or Podman
podman --version
podman-compose --version
```

### Kubernetes (Optional)

For Kubernetes deployment:

- **kubectl**: v1.25+
- **Helm**: v3.10+
- **Kind** or **Minikube**: For local clusters

```bash
# Install kubectl
brew install kubectl

# Install Helm
brew install helm

# Install Kind
brew install kind
```

### Terraform (Optional)

For infrastructure provisioning:

```bash
brew install terraform
terraform --version
```

## Development Tools

### IDE

Any IDE with Java support can be used. Below are two popular options for which we provide setup instructions in the [Development Setup](../developer-guide/setup/development-setup.md) guide:

- **IntelliJ IDEA** (Community or Ultimate)
- **VS Code** with [Java Extension Pack](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)

### Git

```bash
git --version
```

## Network Requirements

Ensure these ports are available:

| Port | Service |
|------|---------|
| 8080-8090 | Control Plane APIs |
| 8180-8190 | Data Plane APIs |
| 8280-8290 | Identity Hub APIs |
| 5432 | PostgreSQL |
| 9200 | Elasticsearch |

## Environment Setup

### Clone Repository

```bash
git clone https://github.com/AmadeusITGroup/dataspace-ecosystem/
cd dataspace-ecosystem
```

### Verify Build

```bash
./gradlew build
```

## Next Steps

- Follow the [Quick Start Guide](quick-start.md)
- Review [Configuration Options](configuration.md)
