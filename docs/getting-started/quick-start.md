# Quick Start

Get the Dataspace Ecosystem up and running locally using Kubernetes (Kind).

## Prerequisites

Before starting, ensure you have installed:

- **JDK 17** or later
- **Terraform**
- **Docker or Podman**
- **Kind** (Kubernetes in Docker)
- **kubectl**
- **cURL or Postman**

See [Prerequisites](prerequisites.md) for installation instructions.

## Overview

This guide walks you through:

1. Creating a local Kubernetes cluster
2. Building and loading container images
3. Deploying the dataspace with Terraform
4. Running end-to-end tests

## Step 1: Create a Local Kubernetes Cluster

```bash

# Create the Kind cluster from the source directory
kind create cluster --name dse-cluster --config system-tests/kind.config.yaml
```

### Install Ingress Controller

Install the NGINX Ingress Controller to access services from your host:

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
```

Wait for the Ingress Controller to be ready:

```bash
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=90s
```

!!! note "Windows Users"
    On Windows, you may need to patch the resource limits:
    ```bash
    kubectl patch deployment ingress-nginx-controller -n ingress-nginx \
      --type='json' \
      -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/resources", "value": {"limits": {"cpu": "500m", "memory": "512Mi"}, "requests": {"cpu": "100m", "memory": "90Mi"}}}]'
    
    kubectl rollout restart deployment/ingress-nginx-controller -n ingress-nginx
    ```

## Step 2: Build Container Images

=== "Docker"
    ```bash
    # Build all images
    ./gradlew clean shadowJar dockerize
    
    # Load images into Kind cluster
    kind load docker-image \
      control-plane-postgresql-hashicorpvault:latest \
      data-plane-postgresql-hashicorpvault:latest \
      federated-catalog-postgresql-hashicorpvault:latest \
      identity-hub-postgresql-hashicorpvault:latest \
      issuer-service-postgresql-hashicorpvault:latest \
      telemetry-service-postgresql-hashicorpvault:latest \
      telemetry-agent-postgresql-hashicorpvault:latest \
      backend-service-provider:latest \
      telemetry-storage-postgresql-hashicorpvault:latest \
      telemetry-csv-manager-postgresql-hashicorpvault:latest \
      --name dse-cluster
    ```

=== "Podman"
    ```bash
    # Build all images with Podman
    ./gradlew clean podmanize
    ```

=== "Podman (Single Command)"
    ```bash
    # Build and load to Kind in one step
    ./gradlew clean loadToKind
    ```

## Step 3: Deploy the Dataspace

Deploy all components using Terraform:

```bash
# Initialize Terraform
terraform -chdir=system-tests init

# Deploy the dataspace
terraform -chdir=system-tests apply -auto-approve -var="environment=local"
```

This deploys:

- **Authority components**: Federated Catalog, Issuer Service, Identity Hub, Participant Registry
- **Participants' connectors**: Control Plane, Data Plane, Identity Hub, Telemetry Agent
- **Supporting services**: PostgreSQL, HashiCorp Vault, Telemetry Storage

## Step 4: Verify Deployment

Check that all Pods are running:

```bash
kubectl get pods -A
```

All pods should show `Running` status.

## Step 5: Run End-to-End Tests

Before running tests, set up port forwarding:

```bash
# In a separate terminal (keep it open)
kubectl port-forward eventhubs-0 52717:5672 &
kubectl port-forward postgresql-0 57521:5432 &
```

Run the tests:

```bash
./gradlew :system-tests:runner:test -DincludeTags="EndToEndTest"
```

!!! warning "One-Time Execution"
    Tests can only be run once per deployment. To rerun, destroy and redeploy the dataspace first.

## Cleanup

### Destroy the Dataspace

```bash
terraform -chdir=system-tests destroy -auto-approve -var="environment=local"
```

### Delete the Kind Cluster

```bash
kind delete cluster --name dse-cluster
```

### Clean Build Artifacts

```bash
./gradlew clean
```

## Deploy a Single Connector (Optional)

To deploy just one participant connector without the full dataspace:

### Prerequisites

Either deploy the full dataspace first, or deploy only the database:

```bash
cd system-tests
terraform apply -target=module.postgres -auto-approve
```

### Configuration

1. Rename `standalone-providers.tf.disabled` to `standalone-providers.tf`

2. Create `terraform.tfvars` in the participant folder:

```hcl
# Participant Configuration
participant_name = "your-participant-name"

# Container Images
control_plane_image   = "control-plane-postgresql-hashicorpvault"
data_plane_image      = "data-plane-postgresql-hashicorpvault"
identity_hub_image    = "identity-hub-postgresql-hashicorpvault"
telemetry_agent_image = "telemetry-agent-postgresql-hashicorpvault"

# Kubernetes Configuration
kube_context     = "kind-dse-cluster"
kube_config_path = "~/.kube/config"
environment      = "selfhosted"
charts_path      = "../../../charts"
```

3. Deploy the connector:

```bash
cd system-tests/modules/participant
terraform init
terraform apply -auto-approve
```

## Troubleshooting

### Pod Issues

```bash
# Check pod status
kubectl get pods -A

# View pod logs
kubectl logs <pod-name> -n <namespace>

# Describe pod for events
kubectl describe pod <pod-name> -n <namespace>
```

### Ingress Issues

```bash
# Check ingress status
kubectl get ingress -A

# Verify ingress controller
kubectl get pods -n ingress-nginx
```

### Image Loading Issues

```bash
# Verify images are loaded in Kind
docker exec -it dse-cluster-control-plane crictl images
```

### Port Forwarding Issues

```bash
# Kill existing port forwards
pkill -f "kubectl port-forward"

# Restart port forwarding
kubectl port-forward eventhubs-0 52717:5672 &
kubectl port-forward postgresql-0 57521:5432 &
```

## Next Steps

- Read about [Configuration](configuration.md) options
- Explore the [Architecture](../architecture/system-overview.md)
- Check the [API Reference](../architecture/components-api/overview.md)
- Review the [system-tests readme](https://github.com/AmadeusITGroup/dataspace-ecosystem/) for advanced options
