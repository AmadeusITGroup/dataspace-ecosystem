# Data Plane Architecture

The Data Plane handles the actual data transfer between participants after a contract agreement has been established.

## Overview

```mermaid
graph LR
    subgraph ConsumerEcosystem[Consumer Ecosystem]
        CA[Consumer App]
        CDP[Consumer Data Plane]
    end
    
    subgraph ProviderEcosystem[Provider Ecosystem]
        PDP[Provider Data Plane]
    end

    PAPI[Provider API]

    CA --> CDP
    CDP --> PDP
    PDP --> PAPI
```

## Components

### Public API Controller

Handles incoming data requests via REST endpoints (GET, POST, PUT, DELETE, PATCH):

- Authorizes requests via `DataPlaneAuthorizationService`
- Creates `DataFlowStartMessage` for pipeline processing
- Uses `PipelineService` for data transfer
- Supports async streaming via `AsyncStreamingDataSink`

### Pipeline Service

Orchestrates data flow between sources and sinks:

```mermaid
graph LR
    Request[Data Request] --> Auth[Authorization Service]
    Auth --> Pipeline[Pipeline Service]
    Pipeline --> Source[Data Source]
    Source --> Sink[Data Sink]
    Sink --> Response[Response]
```

### Transfer States

```mermaid
stateDiagram-v2
    [*] --> INITIAL
    INITIAL --> PROVISIONING
    PROVISIONING --> PROVISIONED
    PROVISIONED --> REQUESTING
    REQUESTING --> STARTING
    STARTING --> STARTED
    STARTED --> COMPLETING
    COMPLETING --> COMPLETED
    STARTED --> SUSPENDING
    SUSPENDING --> SUSPENDED
    SUSPENDED --> STARTING
    STARTED --> TERMINATING
    TERMINATING --> TERMINATED
```

### Data Sources

Connect to various data backends:

| Type | Description |
|------|-------------|
| `HttpData` | HTTP/REST endpoints |
| `AmazonS3` | AWS S3 buckets |
| `AzureStorage` | Azure Blob Storage |
| `Kafka` | Apache Kafka topics |
| `Database` | SQL databases |

### Data Sinks

Deliver data to consumers:

```json
{
  "dataDestination": {
    "@type": "DataAddress",
    "type": "HttpProxy",
    "baseUrl": "https://consumer.example.com/data"
  }
}
```

## Transfer Types

### HTTP Pull (Data Proxy)

Consumer pulls data through their Data Plane, which proxies to the provider:

```mermaid
sequenceDiagram
    participant App as Consumer App
    participant CDP as Consumer Data Plane
    participant PDP as Provider Data Plane
    participant API as Provider API
    
    Note over App: App has EDR with access token from Control Plane
    App->>CDP: Data request with EDR token
    CDP->>PDP: Data request with token
    PDP->>PDP: Verify token & resolve data address
    PDP->>API: Fetch data
    API-->>PDP: Data
    PDP-->>CDP: Data
    CDP-->>App: Data
```

### HTTP Push

Provider initiates data transfer by pushing data to consumer data plane:

```mermaid
sequenceDiagram
    participant CCP as Consumer Control Plane
    participant PCP as Provider Control Plane
    participant PDP as Provider Data Plane
    participant API as Provider API
    participant CDP as Consumer Data Plane
    
    Note over CCP: Consumer initiates transfer with "HttpData-PUSH"
    CCP->>PCP: Transfer request
    PCP->>PCP: Contract validation & agreement
    PCP->>PDP: DataFlowStartMessage (flowType: PUSH, destination: Consumer DP)
    PDP->>API: Fetch data
    API-->>PDP: Data
    PDP->>CDP: Push data to consumer data plane endpoint
    CDP-->>PDP: Acknowledge receipt
    PDP-->>PCP: Transfer complete
    PCP-->>CCP: Transfer status
```


## Configuration

```properties
# Data Plane Configuration
edc.hostname=localhost
web.http.port=8383
web.http.path=/api
web.http.public.port=8484
web.http.public.path=/public
web.http.control.port=8585
web.http.control.path=/control
```

## Extension Points

### Custom Data Source

```java
@Provider
public class CustomDataSourceFactory implements DataSourceFactory {
    
    @Override
    public String supportedType() {
        return "CustomType";
    }
    
    @Override
    public DataSource createSource(DataFlowStartMessage request) {
        // Create custom data source
    }
}
```

### Custom Transfer Type

```java
@Extension(value = "Custom Transfer Extension")
public class CustomTransferExtension implements ServiceExtension {
    
    @Inject
    private TransferTypeManager manager;
    
    @Override
    public void initialize(ServiceExtensionContext context) {
        manager.registerTransferType(new CustomTransferType());
    }
}
```

## Security

### Data Encryption

- TLS for data in transit
- Optional payload encryption
- Token-based authentication

### Access Control

- Contract-based authorization
- Token validation
- Rate limiting

## See Also

- **[Data Plane API Reference](../components-api/data-plane-api.md)** — To see the REST endpoints exposed by this component, including data access examples, authentication headers, and error codes
- [Control Plane Architecture](control-plane.md) — The component that initiates transfers and provides contract agreement IDs used by the Data Plane
- [Telemetry Architecture](telemetry.md) — How data consumption is tracked for billing after transfers complete
- [API Reference Overview](../components-api/overview.md) — End-to-end API workflow showing how the Data Plane fits into the full data exchange flow
