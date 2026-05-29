# Testing Guide

Comprehensive testing strategy for the Dataspace Ecosystem covering unit tests, integration tests, and end-to-end tests.

## System Tests (End-to-End)

System tests validate the entire Dataspace with all components deployed in Kubernetes.

### Prerequisites

- Terraform
- Podman or Docker
- Kind
- cURL, [Postman](https://www.postman.com/), or [Bruno](https://www.usebruno.com/)

### Setup Kubernetes Cluster

Create and prepare a local Kubernetes cluster:

```bash
kind create cluster --name dse-cluster --config kind.config.yaml
```

You can find the [kind.config.yaml](https://github.com/AmadeusITGroup/dataspace-ecosystem/blob/main/system-tests/kind.config.yaml) file in the system-tests directory.

#### Install Ingress Controller

Install an Ingress Controller to interact with microservices running in the cluster from the host:

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
```

!!! note "Windows Users"
    On Windows, the Ingress Controller may not become ready after initial installation due to resource scheduling issues with Kind. If the controller pod stays in a pending or crash-looping state, restart it:

    ```bash
    kubectl rollout restart deployment/ingress-nginx-controller -n ingress-nginx
    ```

Verify that the Ingress Controller is up:

```bash
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=90s
```

### Build and Load Container Images

=== "Docker"
    Create the Docker images:

    ```bash
    ./gradlew clean shadowJar dockerize
    ```

    Load the Docker images into the cluster:

    ```bash
    kind load docker-image \
      control-plane-postgresql-hashicorpvault:latest \
      data-plane-postgresql-hashicorpvault:latest \
      federated-catalog-postgresql-hashicorpvault:latest \
      federated-catalog-filter:latest \
      identity-hub-postgresql-hashicorpvault:latest \
      issuer-service-postgresql-hashicorpvault:latest \
      telemetry-service-postgresql-hashicorpvault:latest \
      telemetry-agent-postgresql-hashicorpvault:latest \
      telemetry-storage:latest \
      telemetry-csv-manager:latest \
      kafka-proxy-k8s-manager:latest \
      --name dse-cluster
    ```

=== "Podman"
    Create the container images:

    ```bash
    ./gradlew clean podmanize
    ```

=== "Podman (Single Command)"
    Create the images and load them into the Kubernetes cluster in one step:

    ```bash
    ./gradlew clean loadToKind
    ```

!!! tip "Faster Rebuilds with Caching"
    If you have already built the project once, you can skip the `clean` step and leverage Gradle's build cache to only recompile the services affected by your changes:

    ```bash
    ./gradlew build loadToKind
    ```

    This can significantly reduce build times during iterative development, as unchanged services will be picked up from the cache.

### Deploy the Dataspace

Once you have configured the participants you want to deploy using the `participants` field of the `variables.tf` file in the `system-tests` directory, run the following Terraform commands:

```bash
terraform -chdir=system-tests init
terraform -chdir=system-tests destroy -auto-approve -var="environment=local"
terraform -chdir=system-tests apply -auto-approve -var="environment=local"
```

!!! tip "Running without TLS"
    To deploy without TLS (useful for local debugging), add `-var="tls_enabled=false"` to the `apply` command. See the [TLS Configuration](build-docs/tls-configuration.md) guide for full details on what this disables.

To destroy the dataspace:

```bash
terraform -chdir=system-tests destroy -auto-approve -var="environment=local"
```

### (Optional) Deploy a Single Connector

To deploy a single connector, due to the dependency on the database, one of the following conditions must be met:

- The full Dataspace is already deployed
- The database is already deployed; this can be done by running the following command inside the `system-tests` folder:

```bash
terraform apply -target=module.postgres -auto-approve
```

!!! tip "Performance Consideration"
    If the Dataspace is already deployed, deploying another connector on top of it may fail on a local machine due to high memory consumption. The database-only approach is preferred.

Once the database is deployed:

1. Rename `standalone-providers.tf.disabled` to `standalone-providers.tf`
2. Create a `terraform.tfvars` file with at least the following:

    ```terraform
    # Participant Configuration for self-hosted Connector
    participant_name = "your-participant-name"

    # Container Images for Self-Hosted Environments
    control_plane_image      = "control-plane-postgresql-hashicorpvault"
    data_plane_image         = "data-plane-postgresql-hashicorpvault"
    identity_hub_image       = "identity-hub-postgresql-hashicorpvault"
    telemetry_agent_image    = "telemetry-agent-postgresql-hashicorpvault"

    # Kubernetes Configuration (for standalone mode)
    # Replace "kind-dse-cluster" with your actual Kubernetes context name
    # (for example, the output of `kubectl config current-context`)
    kube_context     = "kind-dse-cluster"
    kube_config_path = "~/.kube/config"

    # Environment Configuration
    environment = "selfhosted"

    # Charts Path (relative to participant module)
    charts_path = "../../../charts"
    ```

3. Inside the `participant` folder, run:

    ```bash
    terraform init
    terraform destroy
    terraform apply -auto-approve
    ```

### Running System Tests

As the Event Broker uses the `sb://` protocol and Nginx has issues working with it, port forwarding is required to allow the system tests to connect directly to the local Pod instances.

Open a terminal and execute the following `kubectl` commands (keep the terminal open during test execution):

```bash
kubectl port-forward eventhubs-0 52717:5672 &
kubectl port-forward postgresql-0 57521:5432 &
```

Execute the system tests:

```bash
./gradlew :system-tests:runner:test -DincludeTags="EndToEndTest"
```

!!! tip "Running tests without TLS"
    If you deployed without TLS, pass the flag to the test runner as well:

    ```bash
    TLS_ENABLED=false ./gradlew :system-tests:runner:test -DincludeTags="EndToEndTest"
    ```

    See [TLS Configuration](build-docs/tls-configuration.md) for all options.

!!! warning "Important"
    - Tests can only be run **once per deployment**
    - To rerun tests, destroy and redeploy the dataspace first
    - Keep the port forwarding terminal open during test execution

### Cleanup After Testing

```bash
# Destroy the dataspace
terraform -chdir=system-tests destroy -auto-approve -var="environment=local"

# Delete the Kind cluster
kind delete cluster --name dse-cluster

# Clean build artifacts
./gradlew clean
```

## See Also

- [Development Setup](setup/development-setup.md) - IDE configuration and debugging setup
- [Quick Start Guide](../getting-started/quick-start.md) - Complete deployment and testing walkthrough
