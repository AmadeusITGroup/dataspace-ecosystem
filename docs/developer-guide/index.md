# Developer Guide

Everything you need to develop, build, test, and contribute to the Dataspace Ecosystem. This guide covers the development workflow from initial setup to running end-to-end tests.

---

<div class="grid cards" markdown>

-   :material-folder-cog:{ .lg .middle } **Project Structure**

    ---

    Understand the repository layout: core modules, extensions, launchers, SPIs, and how they fit together.

    [:octicons-arrow-right-24: Project Structure](project-structure.md)

-   :material-wrench:{ .lg .middle } **Setup**

    ---

    Configure your development environment, IDE, and documentation tooling.

    [:octicons-arrow-right-24: Development Setup](setup/development-setup.md)

-   :material-hammer:{ .lg .middle } **Build**

    ---

    Docker image configuration, base image customization, and vault type selection for container builds.

    [:octicons-arrow-right-24: Build Guides](build-docs/base-image-configuration.md)

-   :material-test-tube:{ .lg .middle } **Testing**

    ---

    Testing strategy, from unit tests to full end-to-end system tests on Kubernetes.

    [:octicons-arrow-right-24: Testing Guide](testing.md)

-   :material-source-pull:{ .lg .middle } **Contributing**

    ---

    Contribution guidelines, code style, and the process for submitting changes.

    [:octicons-arrow-right-24: Contributing Guide](contributing.md)

</div>

---

## Module Architecture

The Dataspace Ecosystem follows a layered module architecture designed for extensibility:

```
┌─────────────────────────────────────────────────┐
│                  Launchers                       │
│   Runtime wiring & deployment configurations     │
├─────────────────────────────────────────────────┤
│                 Extensions                       │
│   Specific functionality & integrations          │
├─────────────────────────────────────────────────┤
│                    Core                          │
│   Default implementations & shared utilities     │
├─────────────────────────────────────────────────┤
│              SPI (Interfaces)                    │
│   Contracts & service provider interfaces        │
└─────────────────────────────────────────────────┘
```

| Layer | Purpose | Example |
|-------|---------|---------|
| **SPI** | Define contracts and interfaces | `TelemetryRecordStore`, `TelemetryService` |
| **Core** | Provide default implementations | `InMemoryTelemetryRecordStore` |
| **Extensions** | Add specific runtime features | `BillingConsumptionMetricsExtension` |
| **Launchers** | Wire everything for deployment | `telemetry-agent-postgresql-hashicorpvault` |

## Quick Links

| Topic | Description |
|-------|-------------|
| [Development Setup](setup/development-setup.md) | IDE configuration and local development |
| [Go Development Setup](setup/go-development-setup.md) | Containerized Go builds for plugins |
| [Documentation Setup](setup/docs-setup.md) | Serve and build the docs locally |
| [Base Image Configuration](build-docs/base-image-configuration.md) | Customize Docker base images |
| [Vault Selection Guide](build-docs/vault-selection-guide.md) | Choose between HashiCorp and Azure vault |
| [Testing Guide](testing.md) | System tests and end-to-end testing |
| [Contributing](contributing.md) | How to contribute to the project |

