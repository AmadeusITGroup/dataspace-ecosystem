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
Data Common labels
*/}}
{{- define "dse.dataplane.labels" -}}
helm.sh/chart: {{ include "dse.chart" . }}
{{ include "dse.dataplane.selectorLabels" . }}
{{- if .Values.dataplane.image.tag }}
app.kubernetes.io/version: {{ .Values.dataplane.image.tag | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/component: edc-dataplane
app.kubernetes.io/part-of: edc
{{- end }}

{{/*
Data Selector labels
*/}}
{{- define "dse.dataplane.selectorLabels" -}}
app.kubernetes.io/name: {{ include "dse.name" . }}-dataplane
app.kubernetes.io/instance: {{ printf "%s-dataplane" (.Values.dataplane.instanceOverride | default .Release.Name) }}
{{- end }}

{{/*
Data Plane - Public URL
*/}}
{{- define "dse.dataplane.url.public" -}}
{{- if .Values.dataplane.url.public }}{{/* if data api url has been specified explicitly */}}
{{- .Values.dataplane.url.public }}
{{- else }}{{/* else when data api url has not been specified explicitly */}}
{{- with .Values.dataplane.ingress }}
{{- if and .enabled .hostname }}{{/* if ingress enabled and hostname defined */}}
{{- if .tls.enabled }}{{/* if TLS enabled */}}
{{- printf "https://%s%s" .hostname $.Values.dataplane.endpoints.public.path -}}
{{- else }}{{/* else when TLS not enabled */}}
{{- printf "http://%s%s" .hostname $.Values.dataplane.endpoints.public.path -}}
{{- end }}{{/* end if tls */}}
{{- else }}{{/* else when ingress not enabled */}}
{{- printf "http://%s:%v%s" (include "dse.fullname" $ ) $.Values.dataplane.endpoints.public.port $.Values.dataplane.endpoints.public.path -}}
{{- end }}{{/* end if ingress */}}
{{- end }}{{/* end with ingress */}}
{{- end }}{{/* end if .Values.dataplane.url.public */}}
{{- end }}

{{/*
Data Plane - Control URL
*/}}
{{- define "dse.dataplane.url.control" -}}
{{- if .Values.dataplane.internalTls.enabled -}}
{{- printf "https://%s:%v%s" ( include "dse.fullname" $ ) $.Values.dataplane.endpoints.control.port $.Values.dataplane.endpoints.control.path -}}
{{- else -}}
{{- printf "http://%s:%v%s" ( include "dse.fullname" $ ) $.Values.dataplane.endpoints.control.port $.Values.dataplane.endpoints.control.path -}}
{{- end -}}
{{- end }}

{{/*
Data Plane - image suffix for the selected Vault provider. Friendly provider
names are mapped to the suffixes used by published images; existing explicit
suffixes remain valid.
*/}}
{{- define "dse.dataplane.vaultProviderImageSuffix" -}}
{{- $provider := required "global.vaultProvider is required when dataplane.image.repository is not set" .Values.global.vaultProvider -}}
{{- if eq $provider "hashicorp" -}}
hashicorpvault
{{- else if eq $provider "azure" -}}
azurevault
{{- else -}}
{{- $provider -}}
{{- end -}}
{{- end }}

{{/*
Data Plane - Image repository. Uses the explicit value if set, otherwise
derives it from global.image.* + global.vaultProvider.
*/}}
{{- define "dse.dataplane.imageRepository" -}}
{{- if .Values.dataplane.image.repository -}}
{{- .Values.dataplane.image.repository -}}
{{- else -}}
{{- printf "%s/%s-data-plane-postgresql-%s" (required "global.image.baseRepository is required when dataplane.image.repository is not set" .Values.global.image.baseRepository) (required "global.image.namePrefix is required when dataplane.image.repository is not set" .Values.global.image.namePrefix) (include "dse.dataplane.vaultProviderImageSuffix" .) -}}
{{- end -}}
{{- end }}

{{/*
Data Plane - JDBC URL. Uses the explicit value if set, otherwise derives it
from global.db.serverFqdn/name.
*/}}
{{- define "dse.dataplane.jdbcUrl" -}}
{{- if .Values.dataplane.postgresql.jdbcUrl -}}
{{- .Values.dataplane.postgresql.jdbcUrl -}}
{{- else -}}
{{- printf "jdbc:postgresql://%s:5432/%s" (required "global.db.serverFqdn is required when dataplane.postgresql.jdbcUrl is not set" .Values.global.db.serverFqdn) (required "global.db.name is required when dataplane.postgresql.jdbcUrl is not set" .Values.global.db.name) -}}
{{- end -}}
{{- end }}

{{/*
Data Plane - this participant's Identity Hub DID Web URL. Uses the explicit
value if set, otherwise falls back to global.identityHub.didWebUrl.
*/}}
{{- define "dse.dataplane.identityHubDidWebUrl" -}}
{{- if .Values.dataplane.did.web.url -}}
{{- .Values.dataplane.did.web.url -}}
{{- else -}}
{{- required "global.identityHub.didWebUrl is required when dataplane.did.web.url is not set" .Values.global.identityHub.didWebUrl -}}
{{- end -}}
{{- end }}

{{/*
Data Plane - DPF selector URL (points at this participant's control plane).
Uses the explicit value if set, otherwise derives it from the control plane's
default release-name-based service address ("<participantName>-controlplane").
*/}}
{{- define "dse.dataplane.selectorUrl" -}}
{{- if .Values.dataplane.selector.url -}}
{{- .Values.dataplane.selector.url -}}
{{- else -}}
{{- printf "https://%s-controlplane:8383/api/control/v1/dataplanes" (required "global.participantName is required when dataplane.selector.url is not set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/*
Data Plane - OpenTelemetry resource attributes string.
*/}}
{{- define "dse.dataplane.otelResourceAttributes" -}}
{{- printf "tenant_id=%s,service.version=%s,deployment.environment=%s" .Values.global.participantName (.Values.dataplane.image.tag | default .Chart.AppVersion) .Values.global.environment -}}
{{- end }}

{{/*
Data Plane - OpenTelemetry javaagent properties. When global.telemetry is
enabled, reproduce the Terraform-managed otel block; otherwise fall back to
the chart's standalone opentelemetry value.
*/}}
{{- define "dse.dataplane.opentelemetryProperties" -}}
{{- if .Values.global.telemetry.enabled -}}
otel.javaagent.enabled=true
otel.javaagent.debug=false
otel.exporter.otlp.protocol=grpc
otel.exporter.otlp.endpoint={{ required "global.telemetry.collectorEndpoint is required when global.telemetry.enabled is true" .Values.global.telemetry.collectorEndpoint }}
otel.exporter.otlp.headers=tenant_id={{ .Values.global.participantName }}
otel.service.name={{ .Values.global.participantName }}-dataplane
otel.metrics.exporter=prometheus
otel.instrumentation.default.enabled=false
otel.instrumentation.micrometer.enabled=true
{{- else -}}
{{- .Values.dataplane.opentelemetry -}}
{{- end -}}
{{- end }}

{{/*
Data Plane - config.properties. Reproduces the static EDC blobstore endpoint
template the Terraform reference always injects, then appends any extra
component config supplied via .Values.dataplane.config.
*/}}
{{- define "dse.dataplane.configProperties" -}}
edc.blobstore.endpoint.template=https://%s.blob.core.windows.net
{{- with .Values.dataplane.config }}
{{ . }}
{{- end }}
{{- end }}

{{/*
Data Plane - Hashicorp Vault URL. Uses the explicit value if set, otherwise
falls back to global.vault.url.
*/}}
{{- define "dse.dataplane.vaultUrl" -}}
{{- if .Values.dataplane.vault.hashicorp.url -}}
{{- .Values.dataplane.vault.hashicorp.url -}}
{{- else -}}
{{- .Values.global.vault.url -}}
{{- end -}}
{{- end }}

{{/*
Data Plane - Hashicorp Vault token secret name. Derived from
global.participantName unless explicitly overridden.
*/}}
{{- define "dse.dataplane.vaultTokenSecretName" -}}
{{- if .Values.dataplane.vault.hashicorp.token.secret.name -}}
{{- .Values.dataplane.vault.hashicorp.token.secret.name -}}
{{- else -}}
{{- printf "%s-vault-token" (required "global.participantName is required when dataplane.vault.hashicorp.token.secret.name is not set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/*
Data Plane - Hashicorp Vault folder. Derived from global.participantName
unless explicitly overridden.
*/}}
{{- define "dse.dataplane.vaultFolder" -}}
{{- if .Values.dataplane.vault.hashicorp.paths.folder -}}
{{- .Values.dataplane.vault.hashicorp.paths.folder -}}
{{- else if .Values.global.participantName -}}
{{- .Values.global.participantName -}}
{{- else -}}
{{- "" -}}
{{- end -}}
{{- end }}

{{/*
Data Plane - PostgreSQL credentials secret name. Falls back to
global.db.credentials.secret.name (shared across every component using the
same DB user, matching Terraform's "<participantName>-db" convention), which
itself derives from global.participantName unless explicitly overridden.
*/}}
{{- define "dse.dataplane.postgresql.credentials.secretName" -}}
{{- if .Values.dataplane.postgresql.credentials.secret.name -}}
{{- .Values.dataplane.postgresql.credentials.secret.name -}}
{{- else if .Values.global.db.credentials.secret.name -}}
{{- .Values.global.db.credentials.secret.name -}}
{{- else -}}
{{- printf "%s-db" (required "global.participantName is required when neither dataplane.postgresql.credentials.secret.name nor global.db.credentials.secret.name is set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/*
Data Plane - private key vault alias used to sign transfer proxy tokens.
Falls back to global.keys.privateKeyAlias (shared across data-plane,
identity-hub, and telemetry-agent, matching Terraform's "<participantName>-privatekey"
convention), which itself derives from global.participantName unless
explicitly overridden.
*/}}
{{- define "dse.dataplane.keys.dataplane.privateKeyVaultAlias" -}}
{{- if .Values.dataplane.keys.dataplane.privateKeyVaultAlias -}}
{{- .Values.dataplane.keys.dataplane.privateKeyVaultAlias -}}
{{- else if .Values.global.keys.privateKeyAlias -}}
{{- .Values.global.keys.privateKeyAlias -}}
{{- else -}}
{{- printf "%s-privatekey" (required "global.participantName is required when neither dataplane.keys.dataplane.privateKeyVaultAlias nor global.keys.privateKeyAlias is set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/*
Data Plane - public key vault alias used to verify transfer proxy tokens.
Falls back to global.keys.publicKeyAlias (shared across data-plane and
identity-hub, matching Terraform's "<participantName>-publickey" convention),
which itself derives from global.participantName unless explicitly overridden.
*/}}
{{- define "dse.dataplane.keys.dataplane.publicKeyVaultAlias" -}}
{{- if .Values.dataplane.keys.dataplane.publicKeyVaultAlias -}}
{{- .Values.dataplane.keys.dataplane.publicKeyVaultAlias -}}
{{- else if .Values.global.keys.publicKeyAlias -}}
{{- .Values.global.keys.publicKeyAlias -}}
{{- else -}}
{{- printf "%s-publickey" (required "global.participantName is required when neither dataplane.keys.dataplane.publicKeyVaultAlias nor global.keys.publicKeyAlias is set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/*
Data Plane - AES key vault alias used to encrypt participant-context config
(required since EDC 0.16.0's encryption-algorithm registry). Falls back to
global.keys.aesKeyAlias (shared across control-plane, data-plane,
identity-hub, and telemetry-agent, matching Terraform's "aes_key_alias",
"<participantName>-aes" convention), which itself derives from
global.participantName unless explicitly overridden.
*/}}
{{- define "dse.dataplane.keys.encryption.aesKeyAlias" -}}
{{- if .Values.dataplane.keys.encryption.aesKeyAlias -}}
{{- .Values.dataplane.keys.encryption.aesKeyAlias -}}
{{- else if .Values.global.keys.aesKeyAlias -}}
{{- .Values.global.keys.aesKeyAlias -}}
{{- else -}}
{{- printf "%s-aes" (required "global.participantName is required when neither dataplane.keys.encryption.aesKeyAlias nor global.keys.aesKeyAlias is set" .Values.global.participantName) -}}
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