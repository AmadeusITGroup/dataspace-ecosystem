# Asset Custom Property Subscriber

This module provides an event-driven mechanism for automatically decorating EDC assets with custom properties, such as timestamps for creation and modification tracking.

## Overview

The Asset Custom Property Subscriber listens to asset lifecycle events (created, updated) and automatically adds or updates custom properties on assets. This enables auditing, tracking, and metadata enrichment without requiring manual property management in application code.

## Components

### Extension Layer

#### `AssetCustomPropertySubscriberExtension`
The main extension that bootstraps the asset property decoration system:
- Registers event subscribers for asset lifecycle events
- Configures decorators for different event types
- Integrates with EDC's `EventRouter` for event handling
- Uses dependency injection for required services

**Event Subscriptions:**
- **`AssetCreated`** - Triggered when a new asset is created
  - Adds `createdAt` timestamp property
- **`AssetUpdated`** - Triggered when an asset is updated
  - Preserves `createdAt` timestamp
  - Updates `updatedAt` timestamp

### Event Subscriber

#### `AssetCustomPropertySubscriber`
An event subscriber that applies decorators to assets:
- Implements `EventSubscriber` to receive event notifications
- Supports registering multiple `AssetDecorator` instances
- Retrieves the asset from the `AssetIndex` based on the event
- Rebuilds the asset with decorated properties
- Persists the updated asset back to the index

### Decorator Interface

#### `AssetDecorator`
A functional interface for implementing custom asset property decorators:
- Receives the existing asset and a builder for the new asset
- Allows reading existing properties and adding/modifying properties
- Can be chained together for multiple decorations

### Built-in Decorators

#### `AssetCreatedAtDecorator`
Adds or preserves the asset creation timestamp:
- **Property**: `edc:createdAt`
- **Behavior**: 
  - If the property already exists, preserves it
  - If not, sets it to the current time from the injected `Clock`

#### `AssetUpdatedAtDecorator`
Updates the asset modification timestamp:
- **Property**: `edc:updatedAt`
- **Behavior**: Always sets the property to the current time from the injected `Clock`

## How It Works

### Event Flow

1. **Asset Creation**:
   ```
   User creates asset via API
   → AssetCreated event is published
   → AssetCustomPropertySubscriber receives event
   → AssetCreatedAtDecorator adds createdAt timestamp
   → Asset is updated in AssetIndex
   ```

2. **Asset Update**:
   ```
   User updates asset via API
   → AssetUpdated event is published
   → AssetCustomPropertySubscriber receives event
   → AssetCreatedAtDecorator preserves createdAt timestamp
   → AssetUpdatedAtDecorator adds/updates updatedAt timestamp
   → Asset is updated in AssetIndex
   ```

### Synchronous Processing

The subscribers are registered using `registerSync()`, which means:
- Decorators are applied synchronously during the event processing
- The asset is updated before the API call returns
- Ensures consistency between the event and the asset state

### Property Namespace

All properties use the EDC namespace:
- `edc:createdAt` - Asset creation timestamp 
- `edc:updatedAt` - Asset last modification timestamp

## Dependencies

- **EDC Asset SPI** - Asset domain model and index
- **EDC Event SPI** - Event router and subscriber interfaces
- **Java Time API** - Clock for timestamp generation

