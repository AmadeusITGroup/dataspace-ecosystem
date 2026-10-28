# TLS Configuration

## Overview

The Dataspace Ecosystem supports running with or without TLS for internal service communication. A single toggle controls the entire stack end-to-end, from Helm chart probe schemes to Terraform-managed infrastructure and Java system test configuration.

By default, TLS is **enabled**. Disabling it is useful for local development and debugging where certificate management is not required.

On top of internal TLS, the control-plane's `management` API also supports mutual TLS (client certificate authentication) — see [Mutual TLS (mTLS)](#mutual-tls-mtls-for-control-plane-management-api) below.

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

## Mutual TLS (mTLS) for Control Plane Management API

In addition to general internal TLS, the control-plane's `management` HTTP context (port 8181) can require client certificate authentication. This is configured through the `control-plane` Helm chart only (it is not wired into the system-tests Terraform), is layered on top of `internalTls` — it requires `internalTls.enabled = true` — and applies only to the contexts listed in `internalTls.mtls.contexts`; `dsp` and `onboarding` endpoints are unaffected.

### Configuration Parameters

| Parameter | Scope | Default | Description |
|-----------|-------|---------|-------------|
| `internalTls.mtls.enabled` | Helm chart (`control-plane`) | `false` | Enables client cert validation on the Jetty HTTPS connector |
| `internalTls.mtls.contexts` | Helm chart (`control-plane`) | `"management"` | Comma-separated web contexts that require a client cert |
| `internalTls.mtls.clientAuthRequired` | Helm chart (`control-plane`) | `true` | Whether a client cert is mandatory on those contexts. When `false` the cert is only requested at TLS level, but with `allowedClientSans` set, requests without one are still rejected (`403`) — the only difference is a handshake failure vs. a `403`. Cert-less clients are only accepted together with `allowAnyTrustedClient` |
| `internalTls.mtls.allowedClientSans` | Helm chart (`control-plane`) | `""` | Comma-separated list of client certificate Subject Alternative Name (`dNSName`) entries authorized to call the context(s) above, matched case-insensitively. **Required** when mTLS is enabled, unless `allowAnyTrustedClient` is set — the control-plane fails to start otherwise |
| `internalTls.mtls.allowAnyTrustedClient` | Helm chart (`control-plane`) | `false` | Explicit opt-in to authorize any certificate trusted by the truststore when `allowedClientSans` is empty. Logs a warning at startup; local development only |
| `internalTls.truststorePath` | Helm chart (`control-plane`) | `"/shared/cacerts"` | Truststore used to validate client certs |
| `internalTls.truststoreType` | Helm chart (`control-plane`) | `"PKCS12"` | Truststore format, must match the file at `truststorePath` |
| `internalTls.truststoreSecretName` | Helm chart (`control-plane`) | `""` | Name of the required Secret holding the truststore password |

### How it Works

- `JettyMtlsExtension` (`extensions/control-plane/jetty-mtls`) configures Jetty's HTTPS connector to require a trusted client certificate (`needClientAuth`), scoped only to the web contexts listed in `internalTls.mtls.contexts`.
- Client certificates are validated against the truststore at `internalTls.truststorePath`. The default `/shared/cacerts` contains the internal CA **and** the JVM's default CAs, so a certificate from any of those CAs passes TLS-level validation.
- Chaining to a trusted CA only proves who issued the certificate, not that it belongs to an authorized caller. `JettyMtlsExtension` therefore registers a JAX-RS filter that rejects (`403`) requests whose client certificate has no `dNSName` Subject Alternative Name in `internalTls.mtls.allowedClientSans`, scoped to the same contexts. SAN (not Subject CN) is used because RFC 6125 and modern CA policies deprecate the CN for identity verification, and many CAs now issue certificates with an empty Subject that rely entirely on SAN.
- The allowlist is fail-closed: with mTLS enabled and an empty `allowedClientSans`, the control-plane refuses to start unless `allowAnyTrustedClient: true` is set. With that opt-in, a warning is logged at startup — and a second one if `clientAuthRequired` is `false`, since clients without any certificate are then accepted too.

!!! warning "TLS-terminating proxies"
    If an ingress or proxy terminates the caller's TLS connection and re-connects to the control-plane with its own client certificate, the control-plane only sees the proxy's certificate. Do **not** add the proxy's SAN to `allowedClientSans`: every caller that can reach the proxy would then be authorized. Route authorized callers to the `management` context directly (TLS passthrough or in-cluster access) so their own certificate is presented.

### Example: Adding an Allowed SAN

To authorize a new caller, find the `dNSName` Subject Alternative Name of its client certificate and add it to `internalTls.mtls.allowedClientSans` (comma-separated).

```bash
openssl x509 -noout -ext subjectAltName -in cr-bff-client.crt
# X509v3 Subject Alternative Name:
#     DNS:cr-bff.svc.cluster.local
```

=== "values.yaml"
    ```yaml
    # charts/control-plane/values.yaml
    controlplane:
      internalTls:
        mtls:
          enabled: true
          contexts: "management"
          allowedClientSans: "cr-bff.svc.cluster.local"
    ```

=== "helm --set"
    ```bash
    helm upgrade --install control-plane charts/control-plane \
      --set controlplane.internalTls.mtls.enabled=true \
      --set controlplane.internalTls.mtls.allowedClientSans="cr-bff.svc.cluster.local"
    ```

To authorize more than one caller, separate SAN entries with commas, e.g. `allowedClientSans: "cr-bff.svc.cluster.local,other-bff.svc.cluster.local"`.

## Next Steps

- Configure your [Base Image](base-image-configuration.md) for production requirements
- Choose a [Vault Type](vault-selection-guide.md) for your deployment environment
- Return to the [Developer Guide](../index.md) for the full development workflow
