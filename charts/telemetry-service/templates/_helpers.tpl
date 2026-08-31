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
Telemetry Service Common labels
*/}}
{{- define "dse.telemetryservice.labels" -}}
helm.sh/chart: {{ include "dse.chart" . }}
{{ include "dse.telemetryservice.selectorLabels" . }}
{{- if .Values.telemetryservice.image.tag }}
app.kubernetes.io/version: {{ .Values.telemetryservice.image.tag | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/component: edc-telemetryservice
app.kubernetes.io/part-of: edc
{{- end }}

{{/*
Telemetry Service Selector labels
*/}}
{{- define "dse.telemetryservice.selectorLabels" -}}
app.kubernetes.io/name: {{ include "dse.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
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

{{- define "dse.telemetryservice.didWebUrl" -}}
{{- if .Values.telemetryservice.did.web.url -}}
{{- .Values.telemetryservice.did.web.url -}}
{{- else if .Values.global.identityHub.didWebUrl -}}
{{- .Values.global.identityHub.didWebUrl -}}
{{- else -}}
{{- required ".Values.telemetryservice.did.web.url or global.identityHub.didWebUrl is required" .Values.telemetryservice.did.web.url -}}
{{- end -}}
{{- end }}

{{- define "dse.telemetryservice.didWebUseHttps" -}}
{{- if kindIs "bool" .Values.telemetryservice.did.web.useHttps -}}
{{- .Values.telemetryservice.did.web.useHttps -}}
{{- else if kindIs "bool" .Values.global.useHttps -}}
{{- .Values.global.useHttps -}}
{{- else -}}
false
{{- end -}}
{{- end }}

{{- define "dse.telemetryservice.vaultProviderImageSuffix" -}}
{{- $provider := required "global.vaultProvider is required when telemetryservice.image.repository is not set" .Values.global.vaultProvider -}}
{{- if eq $provider "hashicorp" -}}hashicorpvault
{{- else if eq $provider "azure" -}}azurevault
{{- else if or (eq $provider "hashicorpvault") (eq $provider "azurevault") -}}{{ $provider }}
{{- else -}}{{- fail (printf "unsupported global.vaultProvider '%s' (expected hashicorp|azure|hashicorpvault|azurevault)" $provider) -}}
{{- end -}}
{{- end }}

{{- define "dse.telemetryservice.image.repository" -}}
{{- if .Values.telemetryservice.image.repository -}}
{{- .Values.telemetryservice.image.repository -}}
{{- else -}}
{{- printf "%s/%s-telemetry-service-postgresql-%s" (required "global.image.baseRepository is required when telemetryservice.image.repository is not set" .Values.global.image.baseRepository) (required "global.image.namePrefix is required when telemetryservice.image.repository is not set" .Values.global.image.namePrefix) (include "dse.telemetryservice.vaultProviderImageSuffix" .) -}}
{{- end -}}
{{- end }}

{{- define "dse.telemetryservice.postgresql.jdbcUrl" -}}
{{- if .Values.telemetryservice.postgresql.jdbcUrl -}}
{{- .Values.telemetryservice.postgresql.jdbcUrl -}}
{{- else -}}
{{- printf "jdbc:postgresql://%s:5432/%s" (required "global.db.serverFqdn is required when telemetryservice.postgresql.jdbcUrl is not set" .Values.global.db.serverFqdn) (required "global.db.name is required when telemetryservice.postgresql.jdbcUrl is not set" .Values.global.db.name) -}}
{{- end -}}
{{- end }}

{{- define "dse.telemetryservice.postgresql.secretName" -}}
{{- if .Values.telemetryservice.postgresql.credentials.secret.name -}}
{{- .Values.telemetryservice.postgresql.credentials.secret.name -}}
{{- else if .Values.global.db.credentials.secret.name -}}
{{- .Values.global.db.credentials.secret.name -}}
{{- else -}}
{{- printf "%s-db" (required "global.participantName is required when neither telemetryservice.postgresql.credentials.secret.name nor global.db.credentials.secret.name is set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{- define "dse.telemetryservice.vault.hashicorp.url" -}}
{{- if .Values.telemetryservice.vault.hashicorp.url -}}
{{- .Values.telemetryservice.vault.hashicorp.url -}}
{{- else -}}
{{- .Values.global.vault.url -}}
{{- end -}}
{{- end }}

{{- define "dse.telemetryservice.vault.hashicorp.tokenSecretName" -}}
{{- if .Values.telemetryservice.vault.hashicorp.token.secret.name -}}
{{- .Values.telemetryservice.vault.hashicorp.token.secret.name -}}
{{- else -}}
{{- printf "%s-vault-token" (required "global.participantName is required when telemetryservice.vault.hashicorp.token.secret.name is not set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{- define "dse.telemetryservice.vault.hashicorp.folder" -}}
{{- if .Values.telemetryservice.vault.hashicorp.paths.folder -}}
{{- .Values.telemetryservice.vault.hashicorp.paths.folder -}}
{{- else -}}
{{- .Values.global.participantName -}}
{{- end -}}
{{- end }}

{{- define "dse.telemetryservice.sts.tokenUrl" -}}
{{- if .Values.telemetryservice.sts.tokenUrl -}}
{{- .Values.telemetryservice.sts.tokenUrl -}}
{{- else -}}
{{- $scheme := ternary "https" "http" (eq (include "dse.telemetryservice.didWebUseHttps" .) "true") -}}
{{- printf "%s://%s-identityhub:8484/api/sts/token" $scheme .Release.Name -}}
{{- end -}}
{{- end }}

{{- define "dse.telemetryservice.sts.clientId" -}}
{{- if .Values.telemetryservice.sts.clientId -}}
{{- .Values.telemetryservice.sts.clientId -}}
{{- else -}}
{{- include "dse.telemetryservice.didWebUrl" . -}}
{{- end -}}
{{- end }}

{{- define "dse.telemetryservice.sts.clientSecretAlias" -}}
{{- if .Values.telemetryservice.sts.clientSecretAlias -}}
{{- .Values.telemetryservice.sts.clientSecretAlias -}}
{{- else -}}
{{- printf "%s-sts-client-secret" (include "dse.telemetryservice.sts.clientId" .) -}}
{{- end -}}
{{- end }}

{{- define "dse.telemetryservice.keys.encryption.aesKeyAlias" -}}
{{- if .Values.telemetryservice.keys.encryption.aesKeyAlias -}}
{{- .Values.telemetryservice.keys.encryption.aesKeyAlias -}}
{{- else if .Values.global.keys.aesKeyAlias -}}
{{- .Values.global.keys.aesKeyAlias -}}
{{- else -}}
{{- printf "%s-aes" (required "global.participantName is required when telemetryservice.keys.encryption.aesKeyAlias is not set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}