# Asset DXP ontology validation

This control-plane extension validates opted-in asset OpenAPI documents before asset creation or update. It packages the approved INRIA DXP artifacts generated from IATA Open Air JSON Library 24.4.0; their source revision and hashes are recorded in `src/main/resources/ontology/provenance.properties`.

## Asset metadata

Validation runs when a `dcat:DataService` uses the configured profile in `dcterms:conformsTo`. Its `dcat:endpointDescription` must identify one HTTPS OpenAPI document:

```json
{
  "dcat:distribution": [{
    "@type": "dcat:Distribution",
    "dcat:accessService": {
      "@type": "dcat:DataService",
      "dcterms:conformsTo": {
        "@id": "https://w3id.org/dse/profile/inria-iata/24.4.0"
      },
      "dcat:endpointDescription": {
        "@id": "https://api.example.com/openapi.yaml"
      }
    }
  }]
}
```

Each successful JSON response to validate must annotate its object schema, or an array item schema, with the full ontology class IRI:

```yaml
components:
  schemas:
    Ack:
      type: object
      x-dxp-ontology-class: http://www.iata.org/ontology#Ack
      required: [statusCode]
      properties:
        statusCode:
          $ref: '#/components/schemas/Code'
```

At least one annotated `2xx` JSON response is required. Annotated schemas must contain all required IATA fields, may omit optional fields, and cannot add fields absent from the mapped class. Local component references and inline schemas are supported; external `$ref` and external examples are rejected.

Inline response examples are validated against the declared schema, including nested required fields and primitive types.

## Ontology discovery API

The extension exposes the available ontology profiles on the control-plane management API:

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/v1alpha/ontologies` | Lists ontology IDs, names, and short descriptions. |
| `GET` | `/v1alpha/ontologies/{id}` | Returns one profile with the metadata and examples needed to trigger validation, plus the full curated `components.schemas` used during validation. Returns `404` for an unknown ID. |

The packaged profile ID is `inria-iata-24.4.0`. Through the local provider ingress, for example, the list is available at `/provider/cp/management/v1alpha/ontologies`.

The class value `http://www.iata.org/ontology#Ack` is an RDF identifier, not a URL for an OpenAPI schema document. The detail response exposes the curated OpenAPI representation used to validate that class; the validator also applies the packaged RDF class, inheritance, domain, and range relationships.

## Configuration

| Setting | Default | Description |
| --- | --- | --- |
| `dse.asset.ontology.validation.profile` | `https://w3id.org/dse/profile/inria-iata/24.4.0` | Profile IRI that enables validation. |
| `dse.asset.ontology.validation.allowed.hosts` | empty | Comma-separated exact host allowlist. Opted-in publication fails until configured. |
| `dse.asset.ontology.validation.max.document.bytes` | `2097152` | Maximum downloaded document size. |
| `dse.asset.ontology.validation.request.timeout.seconds` | `10` | HTTPS connection and request timeout. |
| `dse.asset.ontology.validation.allow.http` | `false` | Allow HTTP URLs for local demonstrations only. |
| `dse.asset.ontology.validation.allow.private.addresses` | `false` | Allow private/loopback targets for local demonstrations only. |

Downloads reject user-info URLs, redirects, non-HTTPS schemes, unapproved hosts, non-public resolved addresses, unsupported media types, and oversized responses.

For the fixtures under `examples/`, set the allowlist to `localhost` and enable both local-demonstration settings. Never enable those settings in a shared or production environment.