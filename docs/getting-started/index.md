# Getting Started

Everything you need to set up, configure, and run the Dataspace Ecosystem, from prerequisites to a fully deployed local environment.

---

<div class="grid cards" markdown>

-   :material-clipboard-check:{ .lg .middle } **Prerequisites**

    ---

    System requirements, required software (JDK, Docker/Podman, Kubernetes), and environment setup to get started.

    [:octicons-arrow-right-24: Check Prerequisites](prerequisites.md)

-   :material-rocket-launch:{ .lg .middle } **Quick Start**

    ---

    Step-by-step guide to create a local Kubernetes cluster, build container images, and deploy the full dataspace.

    [:octicons-arrow-right-24: Quick Start Guide](quick-start.md)

-   :material-tune:{ .lg .middle } **Configuration**

    ---

    Comprehensive reference for all configuration options: Helm values, Terraform variables, environment variables, and secrets management.

    [:octicons-arrow-right-24: Configuration Reference](configuration.md)

</div>

---

## Recommended Path

Follow these steps in order for the smoothest onboarding experience:

| Step | Action | Time Estimate |
|------|--------|---------------|
| 1 | Review [Prerequisites](prerequisites.md) and install required tools | ~15 min |
| 2 | Follow the [Quick Start](quick-start.md) guide to deploy locally | ~30 min |
| 3 | Explore [Configuration](configuration.md) options for customization | As needed |

## Deployment Options

The Dataspace Ecosystem supports multiple deployment strategies:

- **Local Development**: Kind cluster with Terraform for rapid iteration
- **Self-Hosted**: Deploy a standalone connector to an existing Kubernetes cluster
- **Production**: Full dataspace deployment with PostgreSQL and vault integration

## Next Steps

- Once deployed, explore the [Architecture](../architecture/index.md) to understand each component
- Set up your IDE with the [Development Setup](../developer-guide/setup/development-setup.md) guide
- Review the [API Reference](../architecture/components-api/index.md) to interact with the running services

