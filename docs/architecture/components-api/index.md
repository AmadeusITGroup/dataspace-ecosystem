# API Reference

The Dataspace Ecosystem provides a set of REST APIs that allow you to create assets, policies, and link them together into contract definitions, while managing each one independently. 
You can also negotiate contracts, transfer data, and handle decentralized identities, all programmatically. Every API uses **JSON-LD** (JSON for Linked Data) as its request/response format.

Each API corresponds to a specific [architectural component](../index.md). 
The **Control Plane API** manages business logic (assets, policies, contracts), 
the **Data Plane API** handles actual data movement, 
and the **Identity API** manages trust and credentials. 
Understanding which component owns which API helps you navigate the ecosystem effectively.

!!! tip "New to the Dataspace Ecosystem?"
    If this is your first time exploring the Dataspace Ecosystem, we recommend reading these pages first for essential context:

    - **[Introduction](../../overview/introduction.md)** — What the ecosystem is and why it exists
    - **[Key Concepts](../../overview/key-concepts.md)** — Fundamental terms: assets, policies, contracts, DIDs, VCs
    - **[Architecture Overview](../index.md)** — How the components fit together and which APIs they expose

---

<div class="grid cards" markdown>

-   :material-book-open-variant:{ .lg .middle } **Overview**

    ---

    API structure, authentication methods, common request/response patterns, pagination, filtering, and error handling.

    [:octicons-arrow-right-24: API Overview](overview.md)

-   :material-cog:{ .lg .middle } **Control Plane API**

    ---

    Management and protocol APIs for assets, policies, contract definitions, contract negotiations, and transfer processes.

    [:octicons-arrow-right-24: Control Plane API](control-plane-api.md)

-   :material-swap-horizontal:{ .lg .middle } **Data Plane API**

    ---

    Public and control APIs for data transfer endpoints and data consumption tracking.

    [:octicons-arrow-right-24: Data Plane API](data-plane-api.md)

-   :material-shield-key:{ .lg .middle } **Identity API**

    ---

    DID resolution, credential management, verifiable presentations, and STS (Secure Token Service) endpoints.

    [:octicons-arrow-right-24: Identity API](identity-api.md)

</div>

---

## Next Steps

- Start with the [API Overview](overview.md) for common patterns and conventions
- Explore individual API pages for endpoint-specific documentation
- Review the [Architecture Overview](../index.md) to understand how components and APIs relate
- Follow the [Getting Started](../../getting-started/index.md) guide to deploy services and start making API calls
