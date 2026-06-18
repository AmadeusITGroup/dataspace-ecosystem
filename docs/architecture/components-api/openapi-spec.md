# Published OpenAPI Spec

The merged Dataspace Ecosystem OpenAPI specification, combining all components into a single interactive reference.
## Download

- [Download the merged OpenAPI YAML](../../assets/openapi/dse-full-spec.yaml)


## Interactive Explorer

<swagger-ui src="../../assets/openapi/dse-full-spec.yaml"/>


## How it is produced

The published file combines:

- upstream EDC and Identity Hub OpenAPI sources pinned in `swagger-config.yaml`
- local Gradle-generated OpenAPI specs under `resources/openapi/yaml/`

For the public repository, the generated YAML is synced from the source repository workflow before the public docs site is deployed.