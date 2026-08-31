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
Federated Catalog Common labels
*/}}
{{- define "dse.federatedcatalogfilter.labels" -}}
helm.sh/chart: {{ include "dse.chart" . }}
{{ include "dse.federatedcatalogfilter.selectorLabels" . }}
{{- if .Values.federatedcatalogfilter.image.tag }}
app.kubernetes.io/version: {{ .Values.federatedcatalogfilter.image.tag | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/component: edc-federatedcatalogfilter
app.kubernetes.io/part-of: edc
{{- end }}

{{/*
Selector labels
*/}}
{{- define "dse.federatedcatalogfilter.selectorLabels" -}}
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
{{- define "dse.federatedcatalogfilter.didWebUrl" -}}
{{- if .Values.federatedcatalogfilter.did.web.url -}}
{{- .Values.federatedcatalogfilter.did.web.url -}}
{{- else if .Values.global.identityHub.didWebUrl -}}
{{- .Values.global.identityHub.didWebUrl -}}
{{- else -}}
{{- required ".Values.federatedcatalogfilter.did.web.url or global.identityHub.didWebUrl is required" .Values.federatedcatalogfilter.did.web.url -}}
{{- end -}}
{{- end }}

{{/* did:web useHttps with component -> global -> false precedence */}}
{{- define "dse.federatedcatalogfilter.didWebUseHttps" -}}
{{- if kindIs "bool" .Values.federatedcatalogfilter.did.web.useHttps -}}
{{- .Values.federatedcatalogfilter.did.web.useHttps -}}
{{- else if kindIs "bool" .Values.global.useHttps -}}
{{- .Values.global.useHttps -}}
{{- else -}}
false
{{- end -}}
{{- end }}

{{/* Vault provider suffix used by derived image repository */}}
{{- define "dse.federatedcatalogfilter.vaultProviderImageSuffix" -}}
{{- $provider := required "global.vaultProvider is required when federatedcatalogfilter.image.repository is not set" .Values.global.vaultProvider -}}
{{- if eq $provider "hashicorp" -}}hashicorpvault
{{- else if eq $provider "azure" -}}azurevault
{{- else if or (eq $provider "hashicorpvault") (eq $provider "azurevault") -}}{{ $provider }}
{{- else -}}{{- fail (printf "unsupported global.vaultProvider '%s' (expected hashicorp|azure|hashicorpvault|azurevault)" $provider) -}}
{{- end -}}
{{- end }}

{{/* Image repository with global fallback */}}
{{- define "dse.federatedcatalogfilter.image.repository" -}}
{{- if .Values.federatedcatalogfilter.image.repository -}}
{{- .Values.federatedcatalogfilter.image.repository -}}
{{- else -}}
{{- printf "%s/%s-federated-catalog-filter-postgresql-%s" (required "global.image.baseRepository is required when federatedcatalogfilter.image.repository is not set" .Values.global.image.baseRepository) (required "global.image.namePrefix is required when federatedcatalogfilter.image.repository is not set" .Values.global.image.namePrefix) (include "dse.federatedcatalogfilter.vaultProviderImageSuffix" .) -}}
{{- end -}}
{{- end }}

{{/* JDBC URL with global fallback */}}
{{- define "dse.federatedcatalogfilter.postgresql.jdbcUrl" -}}
{{- if .Values.federatedcatalogfilter.postgresql.jdbcUrl -}}
{{- .Values.federatedcatalogfilter.postgresql.jdbcUrl -}}
{{- else -}}
{{- printf "jdbc:postgresql://%s:5432/%s" (required "global.db.serverFqdn is required when federatedcatalogfilter.postgresql.jdbcUrl is not set" .Values.global.db.serverFqdn) (required "global.db.name is required when federatedcatalogfilter.postgresql.jdbcUrl is not set" .Values.global.db.name) -}}
{{- end -}}
{{- end }}

{{/* DB credentials secret name with global fallback */}}
{{- define "dse.federatedcatalogfilter.postgresql.secretName" -}}
{{- if .Values.federatedcatalogfilter.postgresql.credentials.secret.name -}}
{{- .Values.federatedcatalogfilter.postgresql.credentials.secret.name -}}
{{- else if .Values.global.db.credentials.secret.name -}}
{{- .Values.global.db.credentials.secret.name -}}
{{- else -}}
{{- printf "%s-db" (required "global.participantName is required when neither federatedcatalogfilter.postgresql.credentials.secret.name nor global.db.credentials.secret.name is set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/* Vault URL with global fallback */}}
{{- define "dse.federatedcatalogfilter.vault.hashicorp.url" -}}
{{- if .Values.federatedcatalogfilter.vault.hashicorp.url -}}
{{- .Values.federatedcatalogfilter.vault.hashicorp.url -}}
{{- else -}}
{{- .Values.global.vault.url -}}
{{- end -}}
{{- end }}

{{/* Vault token secret name with global participantName fallback */}}
{{- define "dse.federatedcatalogfilter.vault.hashicorp.tokenSecretName" -}}
{{- if .Values.federatedcatalogfilter.vault.hashicorp.token.secret.name -}}
{{- .Values.federatedcatalogfilter.vault.hashicorp.token.secret.name -}}
{{- else -}}
{{- printf "%s-vault-token" (required "global.participantName is required when federatedcatalogfilter.vault.hashicorp.token.secret.name is not set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/* Vault folder with global participantName fallback */}}
{{- define "dse.federatedcatalogfilter.vault.hashicorp.folder" -}}
{{- if .Values.federatedcatalogfilter.vault.hashicorp.paths.folder -}}
{{- .Values.federatedcatalogfilter.vault.hashicorp.paths.folder -}}
{{- else -}}
{{- .Values.global.participantName -}}
{{- end -}}
{{- end }}

{{/* STS token URL with global fallback */}}
{{- define "dse.federatedcatalogfilter.sts.tokenUrl" -}}
{{- if .Values.federatedcatalogfilter.sts.tokenUrl -}}
{{- .Values.federatedcatalogfilter.sts.tokenUrl -}}
{{- else -}}
{{- $scheme := ternary "https" "http" (eq (include "dse.federatedcatalogfilter.didWebUseHttps" .) "true") -}}
{{- printf "%s://%s-identityhub:8484/api/sts/token" $scheme .Release.Name -}}
{{- end -}}
{{- end }}

{{/* STS client ID with global fallback */}}
{{- define "dse.federatedcatalogfilter.sts.clientId" -}}
{{- if .Values.federatedcatalogfilter.sts.clientId -}}
{{- .Values.federatedcatalogfilter.sts.clientId -}}
{{- else -}}
{{- include "dse.federatedcatalogfilter.didWebUrl" . -}}
{{- end -}}
{{- end }}

{{/* STS client secret alias with derived fallback */}}
{{- define "dse.federatedcatalogfilter.sts.clientSecretAlias" -}}
{{- if .Values.federatedcatalogfilter.sts.clientSecretAlias -}}
{{- .Values.federatedcatalogfilter.sts.clientSecretAlias -}}
{{- else -}}
{{- printf "%s-sts-client-secret" (include "dse.federatedcatalogfilter.sts.clientId" .) -}}
{{- end -}}
{{- end }}

{{/* Authority DID with global fallback */}}
{{- define "dse.federatedcatalogfilter.authorityDid" -}}
{{- if .Values.federatedcatalogfilter.trustedIssuers.authority.did -}}
{{- .Values.federatedcatalogfilter.trustedIssuers.authority.did -}}
{{- else if .Values.global.authority.didWebUrl -}}
{{- .Values.global.authority.didWebUrl -}}
{{- else -}}
{{- include "dse.federatedcatalogfilter.didWebUrl" . -}}
{{- end -}}
{{- end }}

{{/* AES key alias with global fallback */}}
{{- define "dse.federatedcatalogfilter.keys.encryption.aesKeyAlias" -}}
{{- if .Values.federatedcatalogfilter.keys.encryption.aesKeyAlias -}}
{{- .Values.federatedcatalogfilter.keys.encryption.aesKeyAlias -}}
{{- else if .Values.global.keys.aesKeyAlias -}}
{{- .Values.global.keys.aesKeyAlias -}}
{{- else -}}
{{- printf "%s-aes" (required "global.participantName is required when federatedcatalogfilter.keys.encryption.aesKeyAlias is not set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}