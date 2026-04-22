# Go Development Setup

This guide explains how Go builds work in the Dataspace Ecosystem project and ensures team-wide consistency.

## Overview

The project uses **containerized Go builds** to ensure all team members use the exact same Go version without requiring local Go installation.

## Go Version

- **Toolchain Version**: go1.24.7
- **Container Image**: `golang:1.24.7`

## How It Works

### Automatic Container-Based Builds

The build system automatically uses containerized Go when:
1. **Podman** or **Docker** is available on your system
2. Called through Gradle: `./gradlew clean build`
3. Called directly: `make build` (in the `plugins/kafka-proxy-auth` directory)

### Container Detection

The Makefile automatically detects your container runtime:
- **Preferentially uses Podman** (if available)
- **Falls back to Docker** (if Podman not available)
- **Falls back to local Go** (if no container runtime available)

### Build Targets

Available Make targets in `plugins/kafka-proxy-auth/`:

```bash
# Build all binaries (recommended)
make build

# Clean build artifacts
make clean

# Run tests
make test

# Download and verify dependencies
make deps

# Tidy module dependencies
make tidy

# Setup (pull Go container image)
make setup-go

# Build specific binaries
make build-provider
make build-verifier
make build-token-info
```

### Gradle Integration

The Go builds are fully integrated with Gradle:

```bash
# Build everything including Go plugins
./gradlew build

# Clean everything including Go plugins
./gradlew clean

# Build just the Go plugins
./gradlew :plugins:buildGoPlugins

# Clean just the Go plugins
./gradlew :plugins:cleanGoPlugins
```

## Verification

Test your setup:

```bash
cd /path/to/dataspace-ecosystem
./gradlew :plugins:buildGoPlugins
```

Expected output:
```
> Task :plugins:buildGoPlugins
Building Go authentication plugins...
Using containerized Go 1.24.7 with /opt/podman/bin/podman
Building entra-token-provider...
Building entra-token-verifier...
Building entra-token-info...
Go plugins built successfully
```

## Troubleshooting

### Container Runtime Issues

1. **Podman not found**: Install Podman or Docker
2. **Container image pull fails**: Check network connectivity
3. **Permission denied**: Ensure your user has container runtime permissions

### Local Go Issues

1. **Go not found**: Install Go 1.24.7 or use container runtime
2. **Wrong Go version**: Update Go installation
3. **Module errors**: Run `make tidy` to clean dependencies

### Build Issues

1. **Clean and rebuild**: `./gradlew clean build`
2. **Check working directory**: Ensure you're in the project root
3. **Container permissions**: Some systems require rootless container setup

## Benefits of This Approach

✅ **Zero local Go installation required** (with containers)  
✅ **Guaranteed version consistency** across team  
✅ **Cross-platform compatibility** (Linux, macOS, Windows)  
✅ **Seamless Gradle integration**  
✅ **Automatic fallback** to local Go if needed  
✅ **No changes to existing workflow**  

## File Structure

```
plugins/kafka-proxy-auth/
├── Makefile              # Containerized Go build logic
├── go.mod               # Go module definition (requires Go 1.23.0)
├── go.sum               # Go module checksums
├── cmd/                 # Go application entry points
├── bin/                 # Built binaries (created during build)
└── Dockerfile           # Container image for deployment
```

For questions or issues, check the Makefile in `plugins/kafka-proxy-auth/Makefile`.

## See Also

- [Development Setup](development-setup.md) - IDE and JDK configuration
- [Documentation Setup](docs-setup.md) - MkDocs local preview
- [Build Guides](../build-docs/index.md) - Docker image and vault configuration
