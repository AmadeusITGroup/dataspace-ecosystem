# Architecture

This section provides a comprehensive view of the Dataspace Ecosystem architecture, from the high-level system design to the internals of each component.

Understanding the architecture is essential for deploying, extending, and operating the Dataspace Ecosystem effectively.

!!! tip "New to the Dataspace Ecosystem?"
    If you're exploring this project for the first time, we recommend starting with the [Introduction](../overview/introduction.md) and [Key Concepts](../overview/key-concepts.md) pages before diving into the architecture. They provide essential context on what the ecosystem does and the terminology used throughout this documentation.

??? info "Glossary — Key Terms Used in This Section"
    | Term | Definition |
    |------|------------|
    | **Control Plane** | The management brain of each participant — handles contracts, policies, asset registration, and catalog operations. Does not move data itself. |
    | **Data Plane** | The data transfer engine — moves actual data between participants after a contract has been agreed upon. |
    | **Federated Catalog** | A central registry that aggregates data offerings from all participants so consumers can discover what's available across the entire dataspace. |
    | **Identity Hub** | A component that manages a participant's decentralized identity (DID) and stores/presents verifiable credentials (VCs) used for trust and authentication. |
    | **DID (Decentralized Identifier)** | A globally unique, self-owned identifier (e.g., `did:web:example.com`) that does not depend on a central authority. Used to identify participants. |
    | **VC (Verifiable Credential)** | A digitally signed, tamper-proof claim about a participant (e.g., "this organization is a member of the dataspace"). |
    | **VP (Verifiable Presentation)** | A package of one or more Verifiable Credentials presented by a participant to prove claims about itself. |
    | **ODRL** | Open Digital Rights Language — a W3C standard used to express usage policies (e.g., "only members can access this data"). |
    | **DSP (Dataspace Protocol)** | The standard communication protocol (based on IDS specifications) used for contract negotiation and catalog exchange between connectors. |
    | **JSON-LD** | JSON for Linked Data — the request/response format used by all APIs, which adds semantic context to standard JSON. |
    | **SAS Token** | Shared Access Signature token — a time-limited credential used by telemetry agents to publish events to the Event Broker. |
    | **SPI (Service Provider Interface)** | Java interfaces that define contracts for dependency injection, allowing default implementations to be replaced by custom ones. |

---

<div class="grid cards" markdown>

-   :material-sitemap:{ .lg .middle } **System Overview**

    ---

    High-level architecture diagram, component map, design principles, and communication patterns across the entire ecosystem.

    [:octicons-arrow-right-24: System Overview](system-overview.md)

-   :material-cog:{ .lg .middle } **Control Plane**

    ---

    Manages contract negotiations, policy evaluations, asset management, and catalog operations for each participant.

    [:octicons-arrow-right-24: Control Plane](components/control-plane.md)

-   :material-swap-horizontal:{ .lg .middle } **Data Plane**

    ---

    Handles the actual data transfer between participants, supporting multiple protocols and data consumption tracking.

    [:octicons-arrow-right-24: Data Plane](components/data-plane.md)

-   :material-folder-network:{ .lg .middle } **Federated Catalog**

    ---

    Aggregates and filters catalogs across all participants in the dataspace for unified data discovery.

    [:octicons-arrow-right-24: Federated Catalog](components/federated-catalog.md)

-   :material-shield-key:{ .lg .middle } **Identity Hub**

    ---

    Manages decentralized identities (DIDs), verifiable credentials (VCs), and credential verification flows.

    [:octicons-arrow-right-24: Identity Hub](components/identity-hub.md)

-   :material-chart-line:{ .lg .middle } **Telemetry**

    ---

    Collects data consumption records, generates SAS tokens for Event Broker access, and produces billing reports.

    [:octicons-arrow-right-24: Telemetry](components/telemetry.md)

</div>

---

## Next Steps

- Start with the [System Overview](system-overview.md) for a bird's-eye view
- Explore individual component pages for implementation details
- See how APIs map to the architecture in the [API Reference Overview](components-api/overview.md)
- Review the [Developer Guide](../developer-guide/index.md) for hands-on instructions

