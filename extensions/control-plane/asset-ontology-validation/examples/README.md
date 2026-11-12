The devbox fixtures use `http://10.89.0.4:18080`, where `10.89.0.4` is the current devbox address on the shared Kind network. Configure the control plane with:
dse.asset.ontology.validation.allowed.hosts=10.89.0.4
# Local publication demo

The fixtures use `http://localhost:18080`, so configure the control plane with:

```properties
dse.asset.ontology.validation.allowed.hosts=localhost
dse.asset.ontology.validation.allow.http=true
dse.asset.ontology.validation.allow.private.addresses=true
```

The last two settings are only for this local demonstration. Keep both disabled in shared and production environments.

From the repository root, serve the OpenAPI documents with:

```bash
python3 -m http.server 18080 --bind 127.0.0.1 \
python3 -m http.server 18080 --bind 0.0.0.0 \
  --directory extensions/control-plane/asset-ontology-validation/examples

For a devbox deployment, Terraform can inject these settings by running the lifecycle command with `TF_VAR_ontology_validation_demo_host=10.89.0.4`.
Adjust the management URL and authentication header if the control plane is exposed through the DSE API gateway. Re-check the devbox IP with `podman inspect $(hostname) --format '{{json .NetworkSettings.Networks}}'` after recreating the devbox container, then update the fixture assets and Terraform variable if it changes.
```

Publish the valid asset:

```bash
curl -i -X POST http://localhost:8181/management/v3/assets \
  -H 'Content-Type: application/json' \
  -H 'X-Api-Key: password' \
  --data @extensions/control-plane/asset-ontology-validation/examples/valid-asset.json
```

The asset should be created successfully. Then publish the invalid asset:

```bash
curl -i -X POST http://localhost:8181/management/v3/assets \
  -H 'Content-Type: application/json' \
  -H 'X-Api-Key: password' \
  --data @extensions/control-plane/asset-ontology-validation/examples/invalid-asset.json
```

The second publication should be rejected because `debugCode` is not defined by the IATA `Ack` class.

Adjust the management URL and authentication header if the control plane is exposed through the DSE API gateway. If the control plane runs inside Docker or Kubernetes, `localhost` refers to that container or pod; serve the files on a hostname reachable from it and update the asset URLs and allowlist accordingly.