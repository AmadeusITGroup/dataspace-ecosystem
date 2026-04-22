# Setup

Set up your development environment for working with the Dataspace Ecosystem. This section covers all the tools, IDE configurations, and environment setup required to start developing.

---

<div class="grid cards" markdown>

-   :material-microsoft-visual-studio-code:{ .lg .middle } **Development Setup**

    ---

    Configure your IDE (IntelliJ IDEA or VS Code), JDK, Gradle, and get your local development environment ready.

    [:octicons-arrow-right-24: Development Setup](development-setup.md)

-   :fontawesome-brands-golang:{ .lg .middle } **Go Development Setup**

    ---

    Containerized Go builds for the Kafka proxy authentication plugins. No local Go installation required.

    [:octicons-arrow-right-24: Go Setup](go-development-setup.md)

-   :material-file-document-edit:{ .lg .middle } **Documentation Setup**

    ---

    Install MkDocs and serve the documentation site locally for previewing and editing docs.

    [:octicons-arrow-right-24: Docs Setup](docs-setup.md)

</div>

---

## At a Glance

| Guide | Purpose | Key Tools |
|-------|---------|-----------|
| [Development Setup](development-setup.md) | Core development environment | JDK 17+, Gradle, IntelliJ IDEA or VS Code |
| [Go Development Setup](go-development-setup.md) | Go plugin builds | Podman/Docker, Go 1.24.7 (containerized) |
| [Documentation Setup](docs-setup.md) | Docs preview & authoring | Python 3.8+, MkDocs, Material theme |

## Next Steps

- After setup, review the [Project Structure](../project-structure.md) to understand the codebase
- Follow the [Quick Start](../../getting-started/quick-start.md) to deploy a local dataspace
- Learn about [Build](../build-docs/index.md) options for Docker image customization

