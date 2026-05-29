# Build

This section covers the build configuration options for creating container images of the Dataspace Ecosystem services. Customize base images, select vault implementations, and fine-tune your build pipeline.

---

<div class="grid cards" markdown>

-   :material-docker:{ .lg .middle } **Base Image Configuration**

    ---

    Customize the Docker base image for all services. Choose between CentOS, Eclipse Temurin, or your own custom image.

    [:octicons-arrow-right-24: Base Image Configuration](base-image-configuration.md)

-   :material-shield-lock:{ .lg .middle } **Vault Selection Guide**

    ---

    Select between HashiCorp Vault and Azure Key Vault implementations for secrets management in your deployment.

    [:octicons-arrow-right-24: Vault Selection Guide](vault-selection-guide.md)

-   :material-lock-outline:{ .lg .middle } **TLS Configuration**

    ---

    Toggle TLS for internal service communication across Helm charts, Terraform, and system tests.

    [:octicons-arrow-right-24: TLS Configuration](tls-configuration.md)

</div>

---

## Next Steps

- Configure your [Base Image](base-image-configuration.md) for production requirements
- Choose a [Vault Type](vault-selection-guide.md) for your deployment environment
- Configure [TLS](tls-configuration.md) for your deployment environment
- Return to the [Developer Guide](../index.md) for the full development workflow

