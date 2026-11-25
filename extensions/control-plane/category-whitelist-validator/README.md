# DSE Category Whitelist Validator

Rejects an asset whose `dcat:theme` category is **missing** or **not on the shared category
whitelist**, at publication time (asset create) and on asset update. Part of PSMP-9102 (enhance the
publication flow so an asset cannot be created with a non-whitelisted category).

## How it works

The extension `@Inject`s (optionally) the `CategoryWhitelist` service provided by the
[`category-whitelist-config`](../category-whitelist-config/README.md) extension (PSMP-9104) and
registers a `CategoryThemeValidator` against `EDC_ASSET_TYPE` via `JsonObjectValidatorRegistry`:

```java
validatorRegistry.register(EDC_ASSET_TYPE, new CategoryThemeValidator(whitelist));
```

EDC **composes** this with its built-in `AssetValidator`, so both run on the expanded JSON-LD asset
inside `BaseAssetApiController` **before the asset is stored**. A validation failure is turned into
an **HTTP 400** by the management API. No fork of EDC core.

Because `createAsset` and `updateAsset` both call `validator.validate(EDC_ASSET_TYPE, ...)`, the
rule applies to **create and update alike** from a single registration.

## Whitelist is required

The `CategoryWhitelist` injection is **required**. Category validation cannot be turned off: if the
whitelist file is missing or invalid, `category-whitelist-config` aborts boot, so the control plane
never runs without it.

## Rules

The whitelist applies to **parent** assets only. An asset is treated as a version (child) when it
carries `dcat:version` or `dcterms:isVersionOf`. A child is exempt from the category check. The
version value and the parent link are validated separately, not here.

Concretely, an asset is a child when `dct:isVersionOf` (expanded IRI
`http://purl.org/dc/terms/isVersionOf`) holds at least one entry with a non-blank `@id`, or when
`dcat:version` (expanded IRI `http://www.w3.org/ns/dcat#version`) holds at least one non-blank value
(e.g. `"v1.0.0"`); every other asset is a parent. An empty `isVersionOf`, a `@value` literal or a
blank `@id`, or an empty / blank `dcat:version`, does not make a child, and `dcat:hasVersion` plays
no role.

Other asset-validity rules (a child carries no category, no asset has both `hasVersion` and
`isVersionOf`) are **not** checked here either: they belong to a separate validator.

For a parent, reading the flat `dcat:theme` (expanded IRI `http://www.w3.org/ns/dcat#theme`), an
array of `{ "@id": "<internalId>" }`:

| Input | Result |
|---|---|
| Child asset (`dcat:version` or `dct:isVersionOf` with an `@id`), with or without `dcat:theme` | passes, no category check |
| No `dcat:theme`, or an empty one | **400** — `Mandatory field missing: dcat:theme (asset category)` |
| An entry that is not an object with a non-blank `@id` (e.g. a `@value` literal), even next to valid entries | **400** — `Invalid dcat:theme entry: every category must be an object with a non-blank @id` |
| A theme `@id` not on the whitelist | **400** — `Category not on the whitelist: <id>[, <id>...]` |
| Every theme `@id` on the whitelist | passes |

Every `@id` is checked **independently** against the whitelist: a listed parent category does
**not** approve a non-listed subcategory — the subcategory's own `internalId` must itself be a
whitelist entry.

The whitelist is the ConfigMap-backed one from PSMP-9104 (`CategoryWhitelist.isWhitelisted`), not a
hardcoded list.
