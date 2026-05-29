# Configuration

Comprehensive reference for all configuration options across the Dataspace Ecosystem components, from Helm values and Terraform variables to environment variables and secrets management.

## Configuration Sources

Configuration can be provided through:

1. **Environment variables** (highest priority)
2. **System properties** (`-D` flags)
3. **Helm values** (for Kubernetes deployments)
4. **Default values** (lowest priority)

The `@Setting` annotation defines configuration options in Java code:

```java
@Setting(description = "Authority DID", key = "dse.authority.did", required = true)
public String authorityDid;

@Setting(defaultValue = "azurite", description = "Blob Storage Type", key = "storage.type")
public String blobStorageType;
```

## Terraform Variables

When deploying via Terraform, configure these variables in `system-tests/variables.tf`:

| Variable | Default | Description |
|----------|---------|-------------|
| `provider_name` | `provider` | Name for the provider participant |
| `consumer_name` | `consumer` | Name for the consumer participant |
| `authority_name` | `authority` | Name for the authority |
| `environment` | `production` | Environment: `local`, or `production` |
| `kube_context` | `kind-dse-cluster` | Kubernetes context name |
| `kube_config_path` | `~/.kube/config` | Path to kubeconfig |
| `auth_enabled` | `true` | Enable authentication for proxies |
| `auth_mechanism` | `PLAIN` | Auth mechanism: `PLAIN` or `OAUTHBEARER` |
| `tls_enabled` | `true` | Enable TLS for internal service communication (see [TLS Configuration](../developer-guide/build-docs/tls-configuration.md)) |

## Control Plane Configuration

### Helm Values (`charts/control-plane/values.yaml`)

```yaml
controlplane:
  # API Endpoints
  endpoints:
    default:
      port: 8080
      path: /api
    management:
      port: 8181
      path: /api/management
    protocol:
      port: 8282
      path: /api/dsp
    control:
      port: 8383
      path: /api/control
  
  # PostgreSQL
  postgresql:
    schema:
      autocreate: true
    jdbcUrl: "jdbc:postgresql://postgresql:5432/edc"
    credentials:
      secret:
        name: "postgresql-credentials"
        userKey: "username"
        passwordKey: "password"
  
  # HashiCorp Vault
  vault:
    hashicorp:
      url: "http://vault:8200"
      paths:
        secret: /v1/secret
        folder: ""
        health: /v1/sys/health
      timeout: 30
  
  # STS Configuration
  sts:
    tokenUrl: ""
    clientId: ""
    clientSecretAlias: ""
```

## Identity Hub Configuration

### Key Settings

| Setting | Key | Description |
|---------|-----|-------------|
| STS Public Key Alias | `edc.iam.sts.publickey.alias` | Alias for the STS public key in vault |
| STS Private Key Alias | `edc.iam.sts.privatekey.alias` | Alias for the STS private key in vault |
| Super-User Services | `edc.ih.api.superuser.services` | Service endpoints for super-user (JSON array) |
| Force Recreate | `edc.ih.api.superuser.force.recreate` | Force recreate participant context on startup |

### Helm Values (`charts/identity-hub/values.yaml`)

```yaml
identityhub:
  endpoints:
    default:
      port: 8080
      path: /api
    identity:
      port: 8181
      path: /api/identity
    credentials:
      port: 8282
      path: /api/credentials
    did:
      port: 8383
      path: /api/did
    sts:
      port: 8484
      path: /api/sts
  
  did:
    web:
      url: "did:web:identityhub%3A8383:api:did"
      useHttps: false
```

## Telemetry Agent Configuration

| Setting | Key | Required | Description |
|---------|-----|----------|-------------|
| Authority DID | `dse.authority.did` | Yes | DID of the dataspace authority |
| Private Key Alias | `dse.credential-manager.private-key.alias` | Yes | Vault alias for signing tokens |
| Iteration Wait | `dse.telemetry-agent.state-machine.iteration-wait-millis` | No | State machine iteration wait (ms) |
| Batch Size | `dse.telemetry-agent.state-machine.batch-size` | No | Records to process per batch |
| Retry Limit | `dse.telemetry-agent.send.retry.limit` | No | Max retries before failure |
| Retry Base Delay | `dse.telemetry-agent.send.retry.base-delay.ms` | No | Base delay for retry backoff (ms) |

## Telemetry Service Configuration

### Event Broker SAS Token Settings

| Setting | Key | Description |
|---------|-----|-------------|
| SAS Validity | `dse.credential-factory.azure.event-hub.sas.validity` | Token validity in seconds (default: 300) |
| Event Broker URI | `dse.credential-factory.azure.event-hub.sas.uri` | Event Broker endpoint URI |
| Key Name | `dse.credential-factory.azure.event-hub.sas.key.name` | SAS key name |
| Key Vault Alias | `dse.credential-factory.azure.event-hub.sas.key.alias` | Vault alias for SAS key |
| Connection String Alias | `dse.credential-factory.azure.event-hub.connection-string.alias` | Vault alias for connection string |

## Telemetry CSV Manager Configuration

### Database Settings

| Setting | Key | Description |
|---------|-----|-------------|
| Datasource URL | `edc.datasource.default.url` | JDBC connection URL |
| Datasource User | `edc.datasource.default.user` | Database username |
| Datasource Password | `edc.datasource.default.password` | Database password |

### Storage Settings

| Setting | Key | Default | Description |
|---------|-----|---------|-------------|
| Storage Type | `storage.type` | `azurite` | `azurite` (local) or `azure` (production) |
| Azurite Connection | `azurite.connection.string` | - | Azurite connection string |
| Azurite Container | `azurite.storage.container` | - | Azurite container name |
| Azure Client ID | `azure.client.id` | - | Azure AD client ID |
| Azure Client Secret | `azure.client.secret` | - | Azure AD client secret |
| Azure Tenant ID | `azure.tenant.id` | - | Azure AD tenant ID |
| Azure Container | `azure.storage.container` | - | Azure Blob container name |
| Azure Endpoint | `azure.storage.endpoint` | - | Azure Blob endpoint URL |

## Federated Catalog Configuration

| Setting | Key | Required | Description |
|---------|-----|----------|-------------|
| Authority DID | `dse.authority.did` | Yes | DID of the dataspace authority |

## Database Configuration

### PostgreSQL (Production)

```yaml
# In Helm values
postgresql:
  jdbcUrl: "jdbc:postgresql://postgresql:5432/edc"
  credentials:
    secret:
      name: "postgresql-credentials"
      userKey: "username"
      passwordKey: "password"
```

### SQL Store Datasource

```properties
edc.sql.store.membership.datasource=default
```

## Vault Configuration

### HashiCorp Vault (Kubernetes)

```yaml
vault:
  hashicorp:
    url: "http://vault:8200"
    cert:
      secretName: "tls-ca"
      tlsPath: "/shared/"
    token:
      secret:
        name: "vault-token"
        tokenKey: "token"
    paths:
      secret: /v1/secret
      folder: ""
```

## Environment Variables

Convert properties to environment variables by:
- Replacing `.` with `_`
- Converting to uppercase

```bash
# dse.authority.did -> DSE_AUTHORITY_DID
export DSE_AUTHORITY_DID="did:web:authority:8383:api:did"

# edc.datasource.default.url -> EDC_DATASOURCE_DEFAULT_URL
export EDC_DATASOURCE_DEFAULT_URL="jdbc:postgresql://localhost:5432/edc"

# storage.type -> STORAGE_TYPE
export STORAGE_TYPE="azure"
```

## Standalone Connector Configuration

For self-hosted deployments, create `terraform.tfvars`:

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

## Secrets Management

!!! warning "Never commit secrets"
    Always use Vault or Kubernetes secrets for sensitive values.

Sensitive settings should reference vault aliases:

```properties
# Reference secrets via vault alias
edc.iam.sts.privatekey.alias=my-participant-private-key
dse.credential-manager.private-key.alias=telemetry-signing-key
```

## TLS Configuration

TLS for internal service communication is enabled by default. Each Helm chart exposes an `internalTls` block, and a single Terraform variable (`tls_enabled`) controls TLS across the entire stack.

See the [TLS Configuration](../developer-guide/build-docs/tls-configuration.md) guide for details on toggling TLS, per-chart component keys, and the full impact matrix.

## See Also

- [Quick Start](quick-start.md)
- [Prerequisites](prerequisites.md)
- [TLS Configuration](../developer-guide/build-docs/tls-configuration.md)
- [System Overview](../architecture/system-overview.md)
