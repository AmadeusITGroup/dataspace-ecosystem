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
Control Common labels
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
{{- define "dse.identityhub.labels" -}}
helm.sh/chart: {{ include "dse.chart" . }}
{{ include "dse.identityhub.selectorLabels" . }}
{{- if .Values.identityhub.image.tag }}
app.kubernetes.io/version: {{ .Values.identityhub.image.tag | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/component: edc-identityhub
app.kubernetes.io/part-of: edc
{{- end }}

{{/*
Control Selector labels
*/}}
{{- define "dse.identityhub.selectorLabels" -}}
app.kubernetes.io/name: {{ include "dse.name" . }}
app.kubernetes.io/instance: {{ .Values.identityhub.instanceOverride | default .Release.Name }}
{{- end }}

{{/*
Did Web URL
*/}}
{{- define "dse.identityhub.did.web.url" -}}
{{- if .Values.identityhub.did.web.url }}{{/* if did web url has been specified explicitly */}}
{{- .Values.identityhub.did.web.url }}
{{- else if .Values.global.identityHub.didWebUrl }}{{/* else if the umbrella/global value has been supplied */}}
{{- .Values.global.identityHub.didWebUrl }}
{{- else }}{{/* else when did api url has not been specified explicitly */}}
{{- with .Values.identityhub.ingress }}
{{- if and .enabled .hostname }}{{/* if ingress enabled and hostname defined */}}
{{- printf "did:web:%s:webdid" .hostname -}}
{{- else }}{{/* else when ingress not enabled */}}
{{- printf "did:web:%s%3A%v:api:did" (include "dse.fullname" $ ) $.Values.identityhub.endpoints.did.port -}}
{{- end }}{{/* end if ingress */}}
{{- end }}{{/* end with ingress */}}
{{- end }}{{/* end if .Values.ssi.did.web.url */}}
{{- end }}

{{/*
Identity Hub - DID Web HTTPS setting. A component override (including an
explicit false) wins, followed by global.useHttps, then the legacy false
standalone default.
*/}}
{{- define "dse.identityhub.did.web.useHttps" -}}
{{- if kindIs "bool" .Values.identityhub.did.web.useHttps -}}
{{- .Values.identityhub.did.web.useHttps -}}
{{- else if kindIs "bool" .Values.global.useHttps -}}
{{- .Values.global.useHttps -}}
{{- else -}}
false
{{- end -}}
{{- end }}

{{/*
Identity Hub - image suffix for the selected Vault provider. Friendly
provider names are mapped to the suffixes used by published images; existing
explicit suffixes remain valid.
*/}}
{{- define "dse.identityhub.vaultProviderImageSuffix" -}}
{{- $provider := required "global.vaultProvider is required when identityhub.image.repository is not set" .Values.global.vaultProvider -}}
{{- if eq $provider "hashicorp" -}}
hashicorpvault
{{- else if eq $provider "azure" -}}
azurevault
{{- else -}}
{{- $provider -}}
{{- end -}}
{{- end }}

{{/*
Identity Hub - Image repository. Uses the explicit value if set, otherwise
derives it from global.image.* + global.vaultProvider.
*/}}
{{- define "dse.identityhub.imageRepository" -}}
{{- if .Values.identityhub.image.repository -}}
{{- .Values.identityhub.image.repository -}}
{{- else -}}
{{- printf "%s/%s-identity-hub-postgresql-%s" (required "global.image.baseRepository is required when identityhub.image.repository is not set" .Values.global.image.baseRepository) (required "global.image.namePrefix is required when identityhub.image.repository is not set" .Values.global.image.namePrefix) (include "dse.identityhub.vaultProviderImageSuffix" .) -}}
{{- end -}}
{{- end }}

{{/*
Identity Hub - JDBC URL. Uses the explicit value if set, otherwise derives it
from global.db.serverFqdn/name.
*/}}
{{- define "dse.identityhub.jdbcUrl" -}}
{{- if .Values.identityhub.postgresql.jdbcUrl -}}
{{- .Values.identityhub.postgresql.jdbcUrl -}}
{{- else -}}
{{- printf "jdbc:postgresql://%s:5432/%s" (required "global.db.serverFqdn is required when identityhub.postgresql.jdbcUrl is not set" .Values.global.db.serverFqdn) (required "global.db.name is required when identityhub.postgresql.jdbcUrl is not set" .Values.global.db.name) -}}
{{- end -}}
{{- end }}

{{/*
Identity Hub - STS public key id. Uses the explicit value if set, otherwise
derives it from this identity hub's own DID Web URL ("<didWebUrl>#my-key").
*/}}
{{- define "dse.identityhub.stsPublicKeyId" -}}
{{- if .Values.identityhub.keys.sts.publicKeyId -}}
{{- .Values.identityhub.keys.sts.publicKeyId -}}
{{- else -}}
{{- printf "%s#my-key" (include "dse.identityhub.did.web.url" .) -}}
{{- end -}}
{{- end }}

{{/*
Identity Hub - base64 url-safe (no padding) encoding of this identity hub's
own DID Web URL. Reusable wherever Terraform previously computed
`did_url_base64_url` (e.g. participant-context key/service URLs).
*/}}
{{- define "dse.identityhub.didWebUrlBase64" -}}
{{- (include "dse.identityhub.did.web.url" .) | b64enc | replace "+" "-" | replace "/" "_" | replace "=" "" -}}
{{- end }}

{{/*
Identity Hub - services seeded into a participant context. An explicit JSON
array wins; otherwise derive the participant DSP and Credential Service
endpoints using the same shape as the Terraform deployment.
*/}}
{{- define "dse.identityhub.participantContextServices" -}}
{{- if .Values.identityhub.participantcontext.superuser.services -}}
{{- if kindIs "string" .Values.identityhub.participantcontext.superuser.services -}}
{{- .Values.identityhub.participantcontext.superuser.services -}}
{{- else -}}
{{- .Values.identityhub.participantcontext.superuser.services | toJson -}}
{{- end -}}
{{- else -}}
{{- $scheme := ternary "https" "http" .Values.identityhub.internalTls.enabled -}}
{{- $participantName := default (trimSuffix "-identityhub" .Release.Name) .Values.global.participantName -}}
{{- $dspEndpoint := printf "%s://%s-controlplane:8282/api/dsp/2025-1" $scheme $participantName -}}
{{- $credentialsEndpoint := printf "%s://%s:%v%s/v1/participants/%s" $scheme (include "dse.fullname" .) .Values.identityhub.endpoints.credentials.port (trimSuffix "/" .Values.identityhub.endpoints.credentials.path) (include "dse.identityhub.didWebUrlBase64" .) -}}
{{- list (dict "id" "dsp-url" "type" "DSPMessaging" "serviceEndpoint" $dspEndpoint) (dict "id" "credential-service-url" "type" "CredentialService" "serviceEndpoint" $credentialsEndpoint) | toJson -}}
{{- end -}}
{{- end }}

{{/*
Identity Hub - OpenTelemetry resource attributes string.
*/}}
{{- define "dse.identityhub.otelResourceAttributes" -}}
{{- printf "tenant_id=%s,service.version=%s,deployment.environment=%s" .Values.global.participantName (.Values.identityhub.image.tag | default .Chart.AppVersion) .Values.global.environment -}}
{{- end }}

{{/*
Identity Hub - OpenTelemetry javaagent properties. When global.telemetry is
enabled, reproduce the Terraform-managed otel block; otherwise fall back to
the chart's standalone opentelemetry value.
*/}}
{{- define "dse.identityhub.opentelemetryProperties" -}}
{{- if .Values.global.telemetry.enabled -}}
otel.javaagent.enabled=true
otel.javaagent.debug=false
otel.exporter.otlp.protocol=grpc
otel.exporter.otlp.endpoint={{ required "global.telemetry.collectorEndpoint is required when global.telemetry.enabled is true" .Values.global.telemetry.collectorEndpoint }}
otel.exporter.otlp.headers=tenant_id={{ .Values.global.participantName }}
otel.service.name={{ .Values.global.participantName }}-identityhub
otel.metrics.exporter=prometheus
otel.instrumentation.default.enabled=false
otel.instrumentation.micrometer.enabled=true
{{- else -}}
{{- .Values.identityhub.opentelemetry -}}
{{- end -}}
{{- end }}

{{/*
Identity Hub - Hashicorp Vault URL. Uses the explicit value if set, otherwise
falls back to global.vault.url.
*/}}
{{- define "dse.identityhub.vaultUrl" -}}
{{- if .Values.identityhub.vault.hashicorp.url -}}
{{- .Values.identityhub.vault.hashicorp.url -}}
{{- else -}}
{{- .Values.global.vault.url -}}
{{- end -}}
{{- end }}

{{/*
Identity Hub - Hashicorp Vault token secret name. Derived from
global.participantName unless explicitly overridden.
*/}}
{{- define "dse.identityhub.vaultTokenSecretName" -}}
{{- if .Values.identityhub.vault.hashicorp.token.secret.name -}}
{{- .Values.identityhub.vault.hashicorp.token.secret.name -}}
{{- else -}}
{{- printf "%s-vault-token" (required "global.participantName is required when identityhub.vault.hashicorp.token.secret.name is not set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/*
Identity Hub - Hashicorp Vault folder. Derived from global.participantName
unless explicitly overridden.
*/}}
{{- define "dse.identityhub.vaultFolder" -}}
{{- if .Values.identityhub.vault.hashicorp.paths.folder -}}
{{- .Values.identityhub.vault.hashicorp.paths.folder -}}
{{- else if .Values.global.participantName -}}
{{- .Values.global.participantName -}}
{{- else -}}
{{- "" -}}
{{- end -}}
{{- end }}

{{/*
Identity Hub - PostgreSQL credentials secret name. Falls back to
global.db.credentials.secret.name (shared across every component using the
same DB user, matching Terraform's "<participantName>-db" convention), which
itself derives from global.participantName unless explicitly overridden.
*/}}
{{- define "dse.identityhub.postgresql.credentials.secretName" -}}
{{- if .Values.identityhub.postgresql.credentials.secret.name -}}
{{- .Values.identityhub.postgresql.credentials.secret.name -}}
{{- else if .Values.global.db.credentials.secret.name -}}
{{- .Values.global.db.credentials.secret.name -}}
{{- else -}}
{{- printf "%s-db" (required "global.participantName is required when neither identityhub.postgresql.credentials.secret.name nor global.db.credentials.secret.name is set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/*
Identity Hub - STS private key vault alias. Falls back to
global.keys.privateKeyAlias (shared across data-plane, identity-hub, and
telemetry-agent, matching Terraform's "<participantName>-privatekey"
convention), which itself derives from global.participantName unless
explicitly overridden.
*/}}
{{- define "dse.identityhub.keys.sts.privateKeyAlias" -}}
{{- if .Values.identityhub.keys.sts.privateKeyAlias -}}
{{- .Values.identityhub.keys.sts.privateKeyAlias -}}
{{- else if .Values.global.keys.privateKeyAlias -}}
{{- .Values.global.keys.privateKeyAlias -}}
{{- else -}}
{{- printf "%s-privatekey" (required "global.participantName is required when neither identityhub.keys.sts.privateKeyAlias nor global.keys.privateKeyAlias is set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/*
Identity Hub - STS public key vault alias. Falls back to
global.keys.publicKeyAlias (shared across data-plane and identity-hub,
matching Terraform's "<participantName>-publickey" convention), which itself
derives from global.participantName unless explicitly overridden.
*/}}
{{- define "dse.identityhub.keys.sts.publicKeyAlias" -}}
{{- if .Values.identityhub.keys.sts.publicKeyAlias -}}
{{- .Values.identityhub.keys.sts.publicKeyAlias -}}
{{- else if .Values.global.keys.publicKeyAlias -}}
{{- .Values.global.keys.publicKeyAlias -}}
{{- else -}}
{{- printf "%s-publickey" (required "global.participantName is required when neither identityhub.keys.sts.publicKeyAlias nor global.keys.publicKeyAlias is set" .Values.global.participantName) -}}
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