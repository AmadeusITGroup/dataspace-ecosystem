# Kafka Proxy Entra ID Authentication Plugins

OAuth2/OIDC authentication plugins for grepplabs/kafka-proxy with Microsoft Entra ID integration.

## Overview

This plugin extends kafka-proxy with Entra ID (formerly Azure AD) authentication capabilities, enabling:
- **entra-token-provider**: Generates OAuth2 access tokens using client credentials flow
- **entra-token-verifier**: Validates OAuth2/OIDC tokens from incoming connections

## Architecture

```
Consumer → Consumer Proxy → Provider Proxy → Kafka Broker
          [OAuth2 Token]  [Token Validation]  [SASL/mTLS]
```

## Prerequisites

- Docker
- Microsoft Entra ID tenant with app registration

## Building

### Build binaries locally
```bash
make build
```

### Run tests
```bash
make test
```

### Build Docker image
```bash
make docker-build
```

### Clean build artifacts
```bash
make clean
```

## Configuration

### Environment Variables

#### Token Provider
- `ENTRA_TENANT_ID`: Azure tenant ID
- `ENTRA_CLIENT_ID`: Application (client) ID
- `ENTRA_CLIENT_SECRET`: Client secret value
- `ENTRA_SCOPE`: API scope (default: `api://kafka-proxy/.default`)

#### Token Verifier
- `ENTRA_TENANT_ID`: Azure tenant ID
- `ENTRA_CLIENT_ID`: Expected client ID in tokens
- `ENTRA_AUDIENCE`: Expected audience claim

## Usage with Kafka Proxy

### Provider Proxy (Token Verification)
```bash
kafka-proxy server \
  --bootstrap-server-mapping="broker:9092,0.0.0.0:30001" \
  --auth-local-enable \
  --auth-local-command=/usr/local/bin/entra-token-verifier \
  --auth-local-param="--tenant-id=${ENTRA_TENANT_ID}" \
  --auth-local-param="--client-id=${ENTRA_CLIENT_ID}" \
  --auth-local-mechanism=PLAIN
```

### Consumer Proxy (Token Generation)
```bash
kafka-proxy server \
  --bootstrap-server-mapping="proxy-provider:30001,0.0.0.0:30812" \
  --auth-gateway-client-enable \
  --auth-gateway-client-command=/usr/local/bin/entra-token-provider \
  --auth-gateway-client-param="--tenant-id=${ENTRA_TENANT_ID}" \
  --auth-gateway-client-param="--client-id=${ENTRA_CLIENT_ID}" \
  --auth-gateway-client-param="--client-secret=${ENTRA_CLIENT_SECRET}"
```

## Docker Image

The Docker image is based on `grepplabs/kafka-proxy:0.4.3` with the authentication plugins pre-installed.

### Pull image
```bash
docker pull kafka-proxy-entra-auth:latest
```

### Use in Kubernetes
```yaml
spec:
  containers:
  - name: kafka-proxy
    image: kafka-proxy-entra-auth:latest
    args:
      - server
      - --auth-local-enable
      - --auth-local-command=/usr/local/bin/entra-token-verifier
    env:
    - name: ENTRA_TENANT_ID
      valueFrom:
        secretKeyRef:
          name: entra-config
          key: tenant-id
```

## Development

### Project Structure
```
kafka-proxy-auth/
├── Dockerfile              # Docker image definition
├── Makefile               # Build automation
├── README.md              # This file
├── go.mod                 # Go module definition
├── go.sum                 # Go dependencies
├── bin/                   # Compiled binaries
│   ├── entra-token-provider
│   └── entra-token-verifier
└── cmd/                   # Source code
    ├── entra-token-provider/
    │   ├── main.go
    │   ├── main_test.go
    │   └── token.go
    └── entra-token-verifier/
        ├── main.go
        ├── verifier.go
        └── verifier_test.go
```

## Testing

### Unit Tests
```bash
make test
```

### Integration Tests
Integration tests are located in the `system-tests` module and test the plugins with actual kafka-proxy instances.

## Troubleshooting

### Token Verification Fails
- Verify tenant ID is correct
- Check client ID matches the token issuer
- Ensure token hasn't expired
- Validate audience claim matches expected value

### Token Provider Cannot Get Token
- Verify client credentials are correct
- Check network connectivity to Entra ID
- Ensure application has required API permissions
- Verify scope format is correct

## License

Apache License 2.0

## Contributing

See CONTRIBUTING.md in the root of the repository.
