# Data Plane Public API v2

This module provides HTTP proxy functionality for the EDC Data Plane, enabling consumers to actively query data from provider data sources through a public API endpoint. It supports both provider-side public access and consumer-side data proxy scenarios.

## Overview

The Data Plane Public API v2 acts as a data proxy that:
- Enables data consumers to query provider data sources directly through HTTP
- Supports all HTTP verbs (GET, POST, PUT, PATCH, DELETE, HEAD)
- Proxies query parameters, path parameters, and request bodies to backend data sources
- Provides both public-facing and consumer-specific API contexts
- Handles authorization and authentication through access tokens

## Architecture

### Provider-Side Architecture (Public API)

```
External Consumer → Public API (port 8185) → Authorization → Data Pipeline → Backend Data Source
                         ↓
                  Bearer Token / API Key
```

### Consumer-Side Architecture (Data API)

```
Consumer Application → Data API (port 8282) → EDR Store → Authorization → Data Pipeline → Provider's Data Source
                            ↓
                    Contract Agreement ID
```

## Components

### Extension Layer

#### `DataPlanePublicApiV2Extension`
Provider-side extension that exposes a public API for external data access:
- **API Context**: `PUBLIC`
- **Default Port**: `8185`
- **Default Path**: `/api/public`
- Registers endpoints for provider-side data access
- Configures public endpoint URL for EDR generation
- Supports optional response channel configuration
- Uses `DataPlaneAuthorizationService` for token validation

#### `DataPlaneProxyApiExtension`
Consumer-side extension that provides a data proxy API for consuming data from providers:
- **API Context**: `data`
- **Default Port**: `8282`
- **Default Path**: `/api/data`
- Registers endpoints for consumer-side data consumption
- Uses `ConsumerDataPlaneAuthorizationService` with EDR store
- Supports contract-based authorization

### Controller Layer

#### `DataPlanePublicApiV2Controller`
REST controller that handles all HTTP requests and proxies them to backend data sources:
- **Path**: `/{any:.*}` - Matches all paths for proxying
- **Content Type**: `WILDCARD` - Accepts and produces any media type
- Supports all HTTP methods: GET, POST, PUT, PATCH, DELETE, HEAD
- Implements asynchronous response handling using JAX-RS `@Suspended AsyncResponse`
- Integrates with `PipelineService` for data transfer orchestration

**Request Flow:**
1. Extracts authorization token from `Authorization` header
2. Calls `DataPlaneAuthorizationService.authorize()` to validate token and resolve source data address
3. Creates `DataFlowStartMessage` with request details
4. Initiates asynchronous data transfer through pipeline service
5. Streams response back to consumer

**Error Handling:**
- **401 Unauthorized**: Missing `Authorization` header
- **403 Forbidden**: Invalid or expired token, authorization failure
- **500 Internal Server Error**: Data transfer failure, unhandled exceptions

### Authorization Layer

#### `ConsumerDataPlaneAuthorizationService`
Consumer-side authorization service that uses EDR (Endpoint Data Reference) store:
- Implements `DataPlaneAuthorizationService` interface
- Queries EDR store by contract agreement ID
- Resolves transfer process ID from EDR entries
- Converts EDR to `HttpDataAddress` for data transfer
- Adds `Contract-Id` header for tracking

**Token Format:**
- Token is expected to be the contract agreement ID
- Queries EDR store using: `EndpointDataReferenceEntry.AGREEMENT_ID = token`

**EDR Properties Mapping:**
- `edc:endpoint` → `baseUrl` in HttpDataAddress
- `edc:authorization` → `authCode` in HttpDataAddress
- `Authorization` header → `authKey` in HttpDataAddress
- Contract ID → Additional header `Contract-Id`

**Proxy Configuration:**
- `proxyPath`: true - Proxies URL path to backend
- `proxyQueryParams`: true - Forwards query parameters
- `proxyBody`: true - Forwards request body
- `proxyMethod`: true - Forwards HTTP method

### Request Processing Layer

#### `DataFlowRequestSupplier`
Transforms HTTP requests into `DataFlowStartMessage` for pipeline processing:
- Implements `BiFunction<ContainerRequestContextApi, DataAddress, DataFlowStartMessage>`
- Extracts request properties (method, body, query params, path, media type)
- Creates flow type as `PULL` (consumer-initiated transfer)
- Generates unique process and flow IDs
- Sets destination as `AsyncStreamingDataSink`

#### `ContainerRequestContextApiImpl`
Implementation of request context API that wraps JAX-RS `ContainerRequestContext`:
- Extracts HTTP headers (first value only if multiple present)
- Formats query parameters as `key=value&key2=value2` string
- Reads request body from input stream
- Handles media type extraction
- Normalizes request path (removes leading slash)

#### `ContainerRequestContextApi`
Interface for request context abstraction:
- Enables mocking in tests
- Provides simplified API for accessing request properties
- Decouples controller logic from JAX-RS specifics

### API Documentation

#### `DataPlanePublicApiV2`
OpenAPI interface defining the public API contract:
- Documents all supported HTTP methods
- Defines security schemes (Bearer token and API key)
- Provides operation descriptions and response codes
- Categorized under "Data Plane public API" tag

**Response Codes:**
- **400**: Missing access token
- **403**: Access token expired or invalid
- **500**: Failed to transfer data

## How It Works

### Provider-Side Flow (Public API)

1. **Request Received**:
   ```
   GET /api/public/data/asset-123?param=value 
   Host: provider.example.com
   Authorization: Bearer <jwt-token>
   ```

2. **Token Validation**:
   ```
   Controller extracts token
   → DataPlaneAuthorizationService.authorize()
   → Validates token and resolves source DataAddress
   ```

3. **Request Transformation**:
   ```
   DataFlowRequestSupplier creates DataFlowStartMessage:
   - Process ID: UUID
   - Flow Type: PULL
   - Source: Resolved DataAddress
   - Destination: AsyncStreamingDataSink
   - Properties: method, path, query params, body
   ```

4. **Data Transfer**:
   ```
   PipelineService.transfer() called
   → Selects appropriate data source
   → Fetches data from backend
   → Streams to AsyncStreamingDataSink
   ```

5. **Response Streaming**:
   ```
   AsyncStreamingDataSink writes to response
   → StreamingOutput consumer
   → AsyncResponse.resume() with Response
   → Data streamed to client
   ```

### Consumer-Side Flow (Data API)

1. **Consumer Request**:
   ```
   GET /api/data/my-data HTTP/1.1
   Host: consumer-data-plane.example.com
   Authorization: contract-agreement-123
   ```

2. **EDR Resolution**:
   ```
   ConsumerDataPlaneAuthorizationService:
   - Query EDR store by agreement ID
   - Get most recent EDR entry
   - Resolve transfer process ID
   - Convert EDR to HttpDataAddress
   ```

3. **Provider Request**:
   ```
   HttpDataAddress contains:
   - Provider's endpoint URL
   - Authorization token
   - Contract-Id header
   - Proxy flags enabled
   ```

4. **Data Proxy**:
   ```
   Pipeline transfers data from provider
   → Through consumer's data plane
   → Back to consumer application
   ```

## Supported HTTP Methods

| Method | Description | Body Support |
|--------|-------------|--------------|
| GET | Retrieve data | No |
| HEAD | Retrieve headers only | No |
| POST | Create or query | Yes |
| PUT | Update/replace | Yes |
| PATCH | Partial update | Yes |
| DELETE | Remove data | Optional |

### Contract Enforcement
- Consumer-side enforces contract agreement IDs
- EDR store ensures valid contracts exist
- Contract-Id header tracks usage per contract

## Dependencies

- **EDC Data Plane SPI** - Pipeline service and authorization interfaces
- **EDC Web SPI** - Web service and API context management
- **EDC Connector SPI** - Endpoint and data address models
- **EDC EDR SPI** - Endpoint data reference store (consumer-side)
- **Jakarta REST** - JAX-RS annotations and async responses
- **Swagger/OpenAPI** - API documentation
