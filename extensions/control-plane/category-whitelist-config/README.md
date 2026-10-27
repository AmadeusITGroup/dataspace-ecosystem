# DSE Category Whitelist Config

Loads a **category whitelist** from a ConfigMap-backed file at control-plane boot, parses it once,
and holds it in memory as a `CategoryWhitelist` service. The file is provided by the deployment
(any chart or tool that creates the ConfigMap); this extension and the control-plane chart carry no
category data.

> Scope: **read path only**. This extension proves the read contract works at boot and fails fast on
> error. Serving the list over an endpoint, and creation-time rejection of non-whitelisted
> categories, are separate (future) work.

## Configuration

| Setting (`@Setting`) | Env var | Default |
|---|---|---|
| `dse.category.whitelist.enabled` | `DSE_CATEGORY_WHITELIST_ENABLED` | `false` |
| `dse.category.whitelist.path` | `DSE_CATEGORY_WHITELIST_PATH` | `/etc/dse/category-whitelist/whitelist.json` |

> EDC maps an env var to a setting key by lowercasing it and turning `_` into `.`
> (`DSE_CATEGORY_WHITELIST_PATH` → `dse.category.whitelist.path`). An `EDC_`-prefixed name would
> map to `edc.dse...`, a different key, and would have no effect.

When `dse.category.whitelist.enabled` is `false` (the default) the file is never read, no `CategoryWhitelist`
service is registered, and the control plane boots without category validation. This is the mode
for deployments that do not use a whitelist.

When enabled, the file is typically projected from a ConfigMap (data key `whitelist.json`) mounted at
`/etc/dse/category-whitelist/`.

## Mounting the file with the control-plane chart

The `charts/control-plane` chart exposes generic hooks, all rendered with `tpl` (so
`{{ .Release.Name }}` resolves). A consumer that creates a `<release>-category-whitelist` ConfigMap
mounts it like this:

```yaml
controlplane:
  env:
    DSE_CATEGORY_WHITELIST_ENABLED: "true"
  volumes:
    - name: category-whitelist
      configMap:
        name: '{{ .Release.Name }}-category-whitelist'
  volumeMounts:
    - name: category-whitelist
      mountPath: /etc/dse/category-whitelist
      readOnly: true
  # Optional: restart the pods when the ConfigMap changes, if a ConfigMap reloader
  # (e.g. Stakater Reloader) runs in the cluster.
  deploymentAnnotations:
    configmap.reloader.stakater.com/reload: '{{ .Release.Name }}-category-whitelist'
```

The chart never creates the ConfigMap. Enable all three of `env`, `volumes` and `volumeMounts`
together: enabling without the mount fails at boot, and a mount without its volume is rejected by
Kubernetes.

## File format

A JSON object with a `categories` ordered array. Each entry is
`{ id, name, description, internalId, parentId }` (`parentId` is `null` for a top-level category).
`internalId` is carried explicitly in the file — it is **not** derived from the name.

## Fail-fast

When enabled, if the file is missing, unreadable, empty, or unparseable, `initialize()` throws an
`EdcException` and EDC boot aborts. This is deliberate: starting with an empty whitelist would
silently hide every dataset downstream, which looks like a data outage rather than a configuration
error. The file is read once at startup, so changing the list means updating the ConfigMap and
restarting the control-plane pods: through a ConfigMap reloader annotation (see above) where one is
installed, otherwise `kubectl rollout restart deployment/<release>`.

Code that consumes the `CategoryWhitelist` service must treat it as optional: it is registered only
when enabled, so use `@Inject(required = false)` and handle `null`.

## Validation

When enabled the file is rejected if it:

- has trailing content after the JSON object (e.g. two concatenated documents);
- has no categories, or a `null` entry;
- has an entry with a blank `id` or `internalId`;
- has duplicate `id` or `internalId` values;
- has a `parentId` that does not reference an `id` in the same file;
- has a category that is its own parent, or a `parentId` cycle (A → B → A).

The same checks run in the `CategoryWhitelist` constructor (`IllegalArgumentException`), so an
instance is always a well-formed tree.
