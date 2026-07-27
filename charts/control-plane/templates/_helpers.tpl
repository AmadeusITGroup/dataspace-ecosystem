{{/*
Expand the name of the chart.
*/}}
{{- define "dse.name" -}}
{{- default .Chart.Name .Values.nameOverride | replace "+" "_"  | trunc 63 | trimSuffix "-" -}}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "dse.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "dse.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "dse.labels" -}}
helm.sh/chart: {{ include "dse.chart" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Control Common labels
*/}}
{{- define "dse.controlplane.labels" -}}
helm.sh/chart: {{ include "dse.chart" . }}
{{ include "dse.controlplane.selectorLabels" . }}
{{- if .Values.controlplane.image.tag }}
app.kubernetes.io/version: {{ .Values.controlplane.image.tag | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/component: edc-controlplane
app.kubernetes.io/part-of: edc
{{- end }}

{{/*
Control Plane Selector labels
*/}}
{{- define "dse.controlplane.selectorLabels" -}}
app.kubernetes.io/name: {{ include "dse.name" . }}
app.kubernetes.io/instance: {{ .Values.controlplane.instanceOverride | default .Release.Name }}
{{- end }}

{{/*
Control Plane - Control URL
*/}}
{{- define "dse.controlplane.url.control" -}}
{{- if .Values.controlplane.internalTls.enabled -}}
{{- printf "https://%s:%v%s" ( include "dse.fullname" $ ) $.Values.controlplane.endpoints.control.port $.Values.controlplane.endpoints.control.path -}}
{{- else -}}
{{- printf "http://%s:%v%s" ( include "dse.fullname" $ ) $.Values.controlplane.endpoints.control.port $.Values.controlplane.endpoints.control.path -}}
{{- end -}}
{{- end }}

{{/*
Control Plane - Management URL
*/}}
{{- define "dse.controlplane.url.management" -}}
{{- if .Values.controlplane.url.management }}{{/* if management api url has been specified explicitly */}}
{{- .Values.controlplane.url.management }}
{{- else }}{{/* else when management api url has not been specified explicitly */}}
{{- with .Values.controlplane.ingress }}
{{- if and .enabled .hostname }}{{/* if ingress enabled and hostname defined */}}
{{- if .tls.enabled }}{{/* if TLS enabled */}}
{{- printf "https://%s%s" .hostname $.Values.controlplane.endpoints.management.path -}}
{{- else }}{{/* else when TLS not enabled */}}
{{- printf "http://%s%s" .hostname $.Values.controlplane.endpoints.management.path -}}
{{- end }}{{/* end if tls */}}
{{- else }}{{/* else when ingress not enabled */}}
{{- printf "http://%s:%v%s" (include "dse.fullname" $ ) $.Values.controlplane.endpoints.management.port $.Values.controlplane.endpoints.management.path -}}
{{- end }}{{/* end if ingress */}}
{{- end }}{{/* end with ingress */}}
{{- end }}{{/* end if .Values.controlplane.url.management */}}
{{- end }}

{{/*
Control Plane - Protocol URL
*/}}
{{- define "dse.controlplane.url.protocol" -}}
{{- if .Values.controlplane.url.protocol }}{{/* if protocol api url has been specified explicitly */}}
{{- .Values.controlplane.url.protocol }}
{{- else }}{{/* else when protocol api url has not been specified explicitly */}}
{{- with .Values.controlplane.ingress }}
{{- if and .enabled .hostname }}{{/* if ingress enabled and hostname defined */}}
{{- if .tls.enabled }}{{/* if TLS enabled */}}
{{- printf "https://%s%s" .hostname $.Values.controlplane.endpoints.protocol.path -}}
{{- else }}{{/* else when TLS not enabled */}}
{{- printf "http://%s%s" .hostname $.Values.controlplane.endpoints.protocol.path -}}
{{- end }}{{/* end if tls */}}
{{- else }}{{/* else when ingress not enabled */}}
{{- /* The in-cluster Service serves plain HTTP; TLS/https is only terminated for externally exposed (ingress/route) URLs. Do NOT derive the internal scheme from did.web.useHttps / global.useHttps (those describe DID resolution / public-URL preferences), otherwise EDC_DSP_CALLBACK_ADDRESS advertises https against a Service that only speaks http, breaking inter-component calls. */ -}}
{{- printf "http://%s:%v%s" (include "dse.fullname" $ ) $.Values.controlplane.endpoints.protocol.port $.Values.controlplane.endpoints.protocol.path -}}
{{- end }}{{/* end if ingress */}}
{{- end }}{{/* end with ingress */}}
{{- end }}{{/* end if .Values.controlplane.url.protocol */}}
{{- end }}

{{/*
Control Plane - image suffix for the selected Vault provider. Friendly
provider names are mapped to the suffixes used by published images; existing
explicit suffixes remain valid.
*/}}
{{- define "dse.controlplane.vaultProviderImageSuffix" -}}
{{- $provider := required "global.vaultProvider is required when controlplane.image.repository is not set" .Values.global.vaultProvider -}}
{{- if eq $provider "hashicorp" -}}
hashicorpvault
{{- else if eq $provider "azure" -}}
azurevault
{{- else -}}
{{- $provider -}}
{{- end -}}
{{- end }}

{{/*
Control Plane - Image repository. Uses the explicit value if set, otherwise
derives it from global.image.* + global.vaultProvider, mirroring the
"${base_docker_image}/${image_name_prefix}-control-plane-postgresql-${vault_provider}"
pattern computed by Terraform today.
*/}}
{{- define "dse.controlplane.imageRepository" -}}
{{- if .Values.controlplane.image.repository -}}
{{- .Values.controlplane.image.repository -}}
{{- else -}}
{{- printf "%s/%s-control-plane-postgresql-%s" (required "global.image.baseRepository is required when controlplane.image.repository is not set" .Values.global.image.baseRepository) (required "global.image.namePrefix is required when controlplane.image.repository is not set" .Values.global.image.namePrefix) (include "dse.controlplane.vaultProviderImageSuffix" .) -}}
{{- end -}}
{{- end }}

{{/*
Control Plane - JDBC URL. Uses the explicit value if set, otherwise derives it
from global.db.serverFqdn/name.
*/}}
{{- define "dse.controlplane.jdbcUrl" -}}
{{- if .Values.controlplane.postgresql.jdbcUrl -}}
{{- .Values.controlplane.postgresql.jdbcUrl -}}
{{- else -}}
{{- printf "jdbc:postgresql://%s:5432/%s" (required "global.db.serverFqdn is required when controlplane.postgresql.jdbcUrl is not set" .Values.global.db.serverFqdn) (required "global.db.name is required when controlplane.postgresql.jdbcUrl is not set" .Values.global.db.name) -}}
{{- end -}}
{{- end }}

{{/*
Control Plane - this participant's own Identity Hub DID Web URL (used as the
control plane's DID/issuer id, STS client id, etc). Uses the explicit value if
set, otherwise falls back to global.identityHub.didWebUrl.
*/}}
{{- define "dse.controlplane.identityHubDidWebUrl" -}}
{{- if .Values.controlplane.did.web.url -}}
{{- .Values.controlplane.did.web.url -}}
{{- else -}}
{{- required "global.identityHub.didWebUrl is required when controlplane.did.web.url is not set" .Values.global.identityHub.didWebUrl -}}
{{- end -}}
{{- end }}

{{/*
Control Plane - DID Web HTTPS setting. A component override (including an
explicit false) wins, followed by global.useHttps, then the legacy false
standalone default.
*/}}
{{- define "dse.controlplane.did.web.useHttps" -}}
{{- if kindIs "bool" .Values.controlplane.did.web.useHttps -}}
{{- .Values.controlplane.did.web.useHttps -}}
{{- else if kindIs "bool" .Values.global.useHttps -}}
{{- .Values.global.useHttps -}}
{{- else -}}
false
{{- end -}}
{{- end }}

{{/*
Control Plane - STS token URL. Derived from the identity hub's default
release-name-based service address ("<participantName>-identityhub") unless
explicitly overridden.
*/}}
{{- define "dse.controlplane.sts.tokenUrl" -}}
{{- if .Values.controlplane.sts.tokenUrl -}}
{{- .Values.controlplane.sts.tokenUrl -}}
{{- else -}}
{{- printf "https://%s-identityhub:8484/api/sts/token" (required "global.participantName is required when controlplane.sts.tokenUrl is not set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/*
Control Plane - STS client id. Uses the explicit value if set, otherwise
falls back to this participant's Identity Hub DID Web URL.
*/}}
{{- define "dse.controlplane.sts.clientId" -}}
{{- if .Values.controlplane.sts.clientId -}}
{{- .Values.controlplane.sts.clientId -}}
{{- else -}}
{{- include "dse.controlplane.identityHubDidWebUrl" . -}}
{{- end -}}
{{- end }}

{{/*
Control Plane - STS client secret alias. Derived from the identity hub DID web
URL unless explicitly overridden.
*/}}
{{- define "dse.controlplane.sts.clientSecretAlias" -}}
{{- if .Values.controlplane.sts.clientSecretAlias -}}
{{- .Values.controlplane.sts.clientSecretAlias -}}
{{- else -}}
{{- printf "%s-sts-client-secret" (include "dse.controlplane.identityHubDidWebUrl" .) -}}
{{- end -}}
{{- end }}

{{/*
Control Plane - Authority (trusted issuer) DID. Uses the explicit value if
set, otherwise falls back to global.authority.didWebUrl.
*/}}
{{- define "dse.controlplane.authorityDid" -}}
{{- if .Values.controlplane.trustedIssuers.authority.did -}}
{{- .Values.controlplane.trustedIssuers.authority.did -}}
{{- else -}}
{{- required "global.authority.didWebUrl is required when controlplane.trustedIssuers.authority.did is not set" .Values.global.authority.didWebUrl -}}
{{- end -}}
{{- end }}

{{/*
Control Plane - OpenTelemetry resource attributes string, matching the
"tenant_id=...,service.version=...,deployment.environment=..." pattern
computed by Terraform today.
*/}}
{{- define "dse.controlplane.otelResourceAttributes" -}}
{{- printf "tenant_id=%s,service.version=%s,deployment.environment=%s" .Values.global.participantName (.Values.controlplane.image.tag | default .Chart.AppVersion) .Values.global.environment -}}
{{- end }}

{{/*
Control Plane - OpenTelemetry javaagent properties. When global.telemetry is
enabled, reproduce the Terraform-managed otel block (javaagent + OTLP exporter
+ prometheus metrics + derived service.name/tenant_id). Otherwise fall back to
the chart's standalone opentelemetry value.
*/}}
{{- define "dse.controlplane.opentelemetryProperties" -}}
{{- if .Values.global.telemetry.enabled -}}
otel.javaagent.enabled=true
otel.javaagent.debug=false
otel.exporter.otlp.protocol=grpc
otel.exporter.otlp.endpoint={{ required "global.telemetry.collectorEndpoint is required when global.telemetry.enabled is true" .Values.global.telemetry.collectorEndpoint }}
otel.exporter.otlp.headers=tenant_id={{ .Values.global.participantName }}
otel.service.name={{ .Values.global.participantName }}-controlplane
otel.metrics.exporter=prometheus
otel.instrumentation.default.enabled=false
otel.instrumentation.micrometer.enabled=true
{{- else -}}
{{- .Values.controlplane.opentelemetry -}}
{{- end -}}
{{- end }}

{{/*
Control Plane - config.properties. Reproduces the static/derivable EDC
settings the Terraform reference always injects (trusted-issuer authority id
+ credential revocation mimetype), then appends any extra component config
supplied via .Values.controlplane.config (e.g. state-machine tuning, keystore
paths — the environment-specific lines still supplied through values).
*/}}
{{- define "dse.controlplane.configProperties" -}}
edc.iam.trusted-issuer.authority.id={{ include "dse.controlplane.authorityDid" . }}
edc.iam.credential.revocation.mimetype=application/json
{{- with .Values.controlplane.config }}
{{ . }}
{{- end }}
{{- end }}

{{/*
Control Plane - Hashicorp Vault token secret name. Derived from
global.participantName unless explicitly overridden.
*/}}
{{- define "dse.controlplane.vaultTokenSecretName" -}}
{{- if .Values.controlplane.vault.hashicorp.token.secret.name -}}
{{- .Values.controlplane.vault.hashicorp.token.secret.name -}}
{{- else -}}
{{- printf "%s-vault-token" (required "global.participantName is required when controlplane.vault.hashicorp.token.secret.name is not set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/*
Control Plane - Hashicorp Vault folder. Derived from global.participantName
unless explicitly overridden.
*/}}
{{- define "dse.controlplane.vaultFolder" -}}
{{- if .Values.controlplane.vault.hashicorp.paths.folder -}}
{{- .Values.controlplane.vault.hashicorp.paths.folder -}}
{{- else if .Values.global.participantName -}}
{{- .Values.global.participantName -}}
{{- else -}}
{{- "" -}}
{{- end -}}
{{- end }}

{{/*
Control Plane - PostgreSQL credentials secret name. Falls back to
global.db.credentials.secret.name (shared across every component using the
same DB user, matching Terraform's "<participantName>-db" convention), which
itself derives from global.participantName unless explicitly overridden.
*/}}
{{- define "dse.controlplane.postgresql.credentials.secretName" -}}
{{- if .Values.controlplane.postgresql.credentials.secret.name -}}
{{- .Values.controlplane.postgresql.credentials.secret.name -}}
{{- else if .Values.global.db.credentials.secret.name -}}
{{- .Values.global.db.credentials.secret.name -}}
{{- else -}}
{{- printf "%s-db" (required "global.participantName is required when neither controlplane.postgresql.credentials.secret.name nor global.db.credentials.secret.name is set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/*
Control Plane - Hashicorp Vault URL. Uses the explicit value if set,
otherwise falls back to global.vault.url (defaults to the common in-cluster
Vault sidecar convention "https://vault:8200").
*/}}
{{- define "dse.controlplane.vaultUrl" -}}
{{- if .Values.controlplane.vault.hashicorp.url -}}
{{- .Values.controlplane.vault.hashicorp.url -}}
{{- else -}}
{{- .Values.global.vault.url -}}
{{- end -}}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "dse.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "dse.fullname" . ) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}