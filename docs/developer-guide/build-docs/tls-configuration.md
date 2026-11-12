# TLS Configuration

## Overview

The Dataspace Ecosystem supports running with or without TLS for internal service communication. A single toggle controls the entire stack end-to-end, from Helm chart probe schemes to Terraform-managed infrastructure and Java system test configuration.

By default, TLS is **enabled**. Disabling it is useful for local development and debugging where certificate management is not required.

## Configuration Parameters

| Parameter | Scope | Default | Description |
|-----------|-------|---------|-------------|
| `internalTls.enabled` | Helm chart | `true` | Controls probe scheme and TLS volume mount per component |
| `internalTls.secretName` | Helm chart | `"internal-service-tls"` | Kubernetes secret containing `tls.crt`, `tls.key`, and `ca.crt` |
| `tls_enabled` | Terraform | `true` | Master switch for the entire system test stack |

## Helm Charts

Each chart exposes an `internalTls` block under its component key in `values.yaml`.

### Component Keys

| Chart | Component Key |
|-------|---------------|
| `control-plane` | `controlplane` |
| `data-plane` | `dataplane` |
| `identity-hub` | `identityhub` |
| `federated-catalog` | `federatedcatalog` |
| `federated-catalog-filter` | `federatedcatalogfilter` |
| `telemetry-agent` | `telemetryagent` |
| `issuer-service` | `issuerservice` |
| `telemetry-service` | `telemetryservice` |
| `telemetry-csv-manager` | `telemetrycsvmanager` |
| `telemetry-storage` | `telemetrystorage` |

### Usage

=== "TLS Enabled (Default)"
    ```yaml
    <component>:
      internalTls:
        enabled: true
        secretName: "internal-service-tls"
    ```

=== "TLS Disabled"
    ```yaml
    # charts/<chart>/values.yaml — or pass via --set
    <component>:
      internalTls:
        enabled: false
        secretName: ""
    ```

!!! info "What `internalTls.enabled` controls"
    - Liveness, startup, and readiness probe scheme (`HTTPS` ↔ `HTTP`)
    - Mount of the `internal-tls-volume` secret into the pod
    - EDC config properties (`edc.web.https.*`) and `JAVA_TOOL_OPTIONS` trust-store flags (injected by Terraform, not the chart)

---

## System Tests (Terraform)

A single Terraform variable controls TLS across the entire stack.

```hcl
# system-tests/variables.tf
variable "tls_enabled" {
  type    = bool
  default = true   # set to false to run without TLS
}
```

### Usage

=== "TLS Enabled (Default)"
    ```bash
    terraform -chdir=system-tests apply -auto-approve \
      -var="environment=local"
    # tls_enabled defaults to true — no extra flag needed
    ```

=== "TLS Disabled"
    ```bash
    terraform -chdir=system-tests apply -auto-approve \
      -var="environment=local" \
      -var="tls_enabled=false"
    ```

### Impact Matrix

The following table summarizes the end-to-end impact of the `tls_enabled` flag:

| Layer | TLS ON | TLS OFF |
|-------|--------|---------|
| Internal CA & wildcard cert | Created | Skipped |
| Pod-to-pod communication | HTTPS | HTTP |
| Vault listener | `tls_disable=0` | `tls_disable=1` |
| Vault init/keygen jobs | `https://` + `VAULT_CACERT` | `http://` |
| Nginx ingress backend | `backend-protocol: HTTPS` + proxy-ssl | `backend-protocol: HTTP` |
| Ingress TLS termination | Enabled (wildcard cert) | Disabled |
| Helm `internalTls.enabled` | `true` | `false` |
| Java test scheme (`SCHEME`) | `https` | `http` |

---

## Java System Tests

The test scheme is derived automatically from the TLS flag via an environment variable or JVM system property.

=== "Environment Variable"
    ```bash
    TLS_ENABLED=false ./gradlew :system-tests:runner:test -DincludeTags="EndToEndTest"
    ```

=== "JVM System Property"
    ```bash
    ./gradlew :system-tests:runner:test -DincludeTags="EndToEndTest" -Dtls.enabled=false
    ```

!!! tip "Cluster Hostname"
    `CLUSTER_HOSTNAME` adapts automatically based on the TLS setting (`localhost:443` → `localhost:80`).

---

## Next Steps

- Configure your [Base Image](base-image-configuration.md) for production requirements
- Choose a [Vault Type](vault-selection-guide.md) for your deployment environment
- Return to the [Developer Guide](../index.md) for the full development workflow
