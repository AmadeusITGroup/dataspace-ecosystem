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
Issuer Service Common labels
*/}}
{{- define "dse.issuerservice.labels" -}}
helm.sh/chart: {{ include "dse.chart" . }}
{{ include "dse.issuerservice.selectorLabels" . }}
{{- if .Values.issuerservice.image.tag }}
app.kubernetes.io/version: {{ .Values.issuerservice.image.tag | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/component: edc-issuerservice
app.kubernetes.io/part-of: edc
{{- end }}

{{/*
Issuer Service Selector labels
*/}}
{{- define "dse.issuerservice.selectorLabels" -}}
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

{{/* DID Web URL with global fallback */}}
{{- define "dse.issuerservice.didWebUrl" -}}
{{- if .Values.issuerservice.did.web.url -}}
{{- .Values.issuerservice.did.web.url -}}
{{- else if .Values.global.identityHub.didWebUrl -}}
{{- .Values.global.identityHub.didWebUrl -}}
{{- else -}}
{{- required ".Values.issuerservice.did.web.url or global.identityHub.didWebUrl is required" .Values.issuerservice.did.web.url -}}
{{- end -}}
{{- end }}

{{/* did:web useHttps with component -> global -> false precedence */}}
{{- define "dse.issuerservice.didWebUseHttps" -}}
{{- if kindIs "bool" .Values.issuerservice.did.web.useHttps -}}
{{- .Values.issuerservice.did.web.useHttps -}}
{{- else if kindIs "bool" .Values.global.useHttps -}}
{{- .Values.global.useHttps -}}
{{- else -}}
false
{{- end -}}
{{- end }}

{{/* Vault provider suffix used by derived image repository */}}
{{- define "dse.issuerservice.vaultProviderImageSuffix" -}}
{{- $provider := required "global.vaultProvider is required when issuerservice.image.repository is not set" .Values.global.vaultProvider -}}
{{- if eq $provider "hashicorp" -}}hashicorpvault
{{- else if eq $provider "azure" -}}azurevault
{{- else if or (eq $provider "hashicorpvault") (eq $provider "azurevault") -}}{{ $provider }}
{{- else -}}{{- fail (printf "unsupported global.vaultProvider '%s' (expected hashicorp|azure|hashicorpvault|azurevault)" $provider) -}}
{{- end -}}
{{- end }}

{{/* Image repository with global fallback */}}
{{- define "dse.issuerservice.image.repository" -}}
{{- if .Values.issuerservice.image.repository -}}
{{- .Values.issuerservice.image.repository -}}
{{- else -}}
{{- printf "%s/%s-issuer-service-postgresql-%s" (required "global.image.baseRepository is required when issuerservice.image.repository is not set" .Values.global.image.baseRepository) (required "global.image.namePrefix is required when issuerservice.image.repository is not set" .Values.global.image.namePrefix) (include "dse.issuerservice.vaultProviderImageSuffix" .) -}}
{{- end -}}
{{- end }}

{{/* JDBC URL with global fallback */}}
{{- define "dse.issuerservice.postgresql.jdbcUrl" -}}
{{- if .Values.issuerservice.postgresql.jdbcUrl -}}
{{- .Values.issuerservice.postgresql.jdbcUrl -}}
{{- else -}}
{{- printf "jdbc:postgresql://%s:5432/%s" (required "global.db.serverFqdn is required when issuerservice.postgresql.jdbcUrl is not set" .Values.global.db.serverFqdn) (required "global.db.name is required when issuerservice.postgresql.jdbcUrl is not set" .Values.global.db.name) -}}
{{- end -}}
{{- end }}

{{/* DB credentials secret name with global fallback */}}
{{- define "dse.issuerservice.postgresql.secretName" -}}
{{- if .Values.issuerservice.postgresql.credentials.secret.name -}}
{{- .Values.issuerservice.postgresql.credentials.secret.name -}}
{{- else if .Values.global.db.credentials.secret.name -}}
{{- .Values.global.db.credentials.secret.name -}}
{{- else -}}
{{- printf "%s-db" (required "global.participantName is required when neither issuerservice.postgresql.credentials.secret.name nor global.db.credentials.secret.name is set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/* Vault URL with global fallback */}}
{{- define "dse.issuerservice.vault.hashicorp.url" -}}
{{- if .Values.issuerservice.vault.hashicorp.url -}}
{{- .Values.issuerservice.vault.hashicorp.url -}}
{{- else -}}
{{- .Values.global.vault.url -}}
{{- end -}}
{{- end }}

{{/* Vault token secret name with global participantName fallback */}}
{{- define "dse.issuerservice.vault.hashicorp.tokenSecretName" -}}
{{- if .Values.issuerservice.vault.hashicorp.token.secret.name -}}
{{- .Values.issuerservice.vault.hashicorp.token.secret.name -}}
{{- else -}}
{{- printf "%s-vault-token" (required "global.participantName is required when issuerservice.vault.hashicorp.token.secret.name is not set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/* Vault folder with global participantName fallback */}}
{{- define "dse.issuerservice.vault.hashicorp.folder" -}}
{{- if .Values.issuerservice.vault.hashicorp.paths.folder -}}
{{- .Values.issuerservice.vault.hashicorp.paths.folder -}}
{{- else -}}
{{- .Values.global.participantName -}}
{{- end -}}
{{- end }}

{{/* Status list private key alias with global fallback */}}
{{- define "dse.issuerservice.keys.statuslist.privateKeyAlias" -}}
{{- if .Values.issuerservice.keys.statuslist.privateKeyAlias -}}
{{- .Values.issuerservice.keys.statuslist.privateKeyAlias -}}
{{- else if .Values.global.keys.privateKeyAlias -}}
{{- .Values.global.keys.privateKeyAlias -}}
{{- else -}}
{{- required "issuerservice.keys.statuslist.privateKeyAlias or global.keys.privateKeyAlias is required" .Values.issuerservice.keys.statuslist.privateKeyAlias -}}
{{- end -}}
{{- end }}

{{/* AES key alias with global fallback */}}
{{- define "dse.issuerservice.keys.encryption.aesKeyAlias" -}}
{{- if .Values.issuerservice.keys.encryption.aesKeyAlias -}}
{{- .Values.issuerservice.keys.encryption.aesKeyAlias -}}
{{- else if .Values.global.keys.aesKeyAlias -}}
{{- .Values.global.keys.aesKeyAlias -}}
{{- else -}}
{{- printf "%s-aes" (required "global.participantName is required when issuerservice.keys.encryption.aesKeyAlias is not set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}