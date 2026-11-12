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
{{- define "dse.federatedcatalog.labels" -}}
helm.sh/chart: {{ include "dse.chart" . }}
{{ include "dse.federatedcatalog.selectorLabels" . }}
{{- if .Values.federatedcatalog.image.tag }}
app.kubernetes.io/version: {{ .Values.federatedcatalog.image.tag | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/component: edc-federatedcatalog
app.kubernetes.io/part-of: edc
{{- end }}

{{/*
Selector labels
*/}}
{{- define "dse.federatedcatalog.selectorLabels" -}}
app.kubernetes.io/name: {{ include "dse.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Control Plane - Management URL
*/}}
{{- define "dse.federatedcatalog.url.management" -}}
{{- if .Values.federatedcatalog.url.management }}{{/* if management api url has been specified explicitly */}}
{{- .Values.federatedcatalog.url.management }}
{{- else }}{{/* else when management api url has not been specified explicitly */}}
{{- with .Values.federatedcatalog.ingress }}
{{- if and .enabled .hostname }}{{/* if ingress enabled and hostname defined */}}
{{- if .tls.enabled }}{{/* if TLS enabled */}}
{{- printf "https://%s%s" .hostname $.Values.federatedcatalog.endpoints.management.path -}}
{{- else }}{{/* else when TLS not enabled */}}
{{- printf "http://%s%s" .hostname $.Values.federatedcatalog.endpoints.management.path -}}
{{- end }}{{/* end if tls */}}
{{- else }}{{/* else when ingress not enabled */}}
{{- printf "http://%s:%v%s" (include "dse.fullname" $ ) $.Values.federatedcatalog.endpoints.management.port $.Values.federatedcatalog.endpoints.management.path -}}
{{- end }}{{/* end if ingress */}}
{{- end }}{{/* end with ingress */}}
{{- end }}{{/* end if .Values.federatedcatalog.url.management */}}
{{- end }}

{{/*
Federated Catalog - Protocol URL
*/}}
{{- define "dse.federatedcatalog.url.protocol" -}}
{{- if .Values.federatedcatalog.url.protocol }}{{/* if protocol api url has been specified explicitly */}}
{{- .Values.federatedcatalog.url.protocol }}
{{- else }}{{/* else when protocol api url has not been specified explicitly */}}
{{- with .Values.federatedcatalog.ingress }}
{{- if and .enabled .hostname }}{{/* if ingress enabled and hostname defined */}}
{{- if .tls.enabled }}{{/* if TLS enabled */}}
{{- printf "https://%s%s" .hostname $.Values.federatedcatalog.endpoints.protocol.path -}}
{{- else }}{{/* else when TLS not enabled */}}
{{- printf "http://%s%s" .hostname $.Values.federatedcatalog.endpoints.protocol.path -}}
{{- end }}{{/* end if tls */}}
{{- else }}{{/* else when ingress not enabled */}}
{{- printf "http://%s:%v%s" (include "dse.fullname" $ ) $.Values.federatedcatalog.endpoints.protocol.port $.Values.federatedcatalog.endpoints.protocol.path -}}
{{- end }}{{/* end if ingress */}}
{{- end }}{{/* end with ingress */}}
{{- end }}{{/* end if .Values.federatedcatalog.url.protocol */}}
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
{{- define "dse.federatedcatalog.didWebUrl" -}}
{{- if .Values.federatedcatalog.did.web.url -}}
{{- .Values.federatedcatalog.did.web.url -}}
{{- else if .Values.global.identityHub.didWebUrl -}}
{{- .Values.global.identityHub.didWebUrl -}}
{{- else -}}
{{- required ".Values.federatedcatalog.did.web.url or global.identityHub.didWebUrl is required" .Values.federatedcatalog.did.web.url -}}
{{- end -}}
{{- end }}

{{/* did:web useHttps with component -> global -> false precedence */}}
{{- define "dse.federatedcatalog.didWebUseHttps" -}}
{{- if kindIs "bool" .Values.federatedcatalog.did.web.useHttps -}}
{{- .Values.federatedcatalog.did.web.useHttps -}}
{{- else if kindIs "bool" .Values.global.useHttps -}}
{{- .Values.global.useHttps -}}
{{- else -}}
false
{{- end -}}
{{- end }}

{{/* Vault provider suffix used by derived image repository */}}
{{- define "dse.federatedcatalog.vaultProviderImageSuffix" -}}
{{- $provider := required "global.vaultProvider is required when federatedcatalog.image.repository is not set" .Values.global.vaultProvider -}}
{{- if eq $provider "hashicorp" -}}hashicorpvault
{{- else if eq $provider "azure" -}}azurevault
{{- else if or (eq $provider "hashicorpvault") (eq $provider "azurevault") -}}{{ $provider }}
{{- else -}}{{- fail (printf "unsupported global.vaultProvider '%s' (expected hashicorp|azure|hashicorpvault|azurevault)" $provider) -}}
{{- end -}}
{{- end }}

{{/* Image repository with global fallback */}}
{{- define "dse.federatedcatalog.image.repository" -}}
{{- if .Values.federatedcatalog.image.repository -}}
{{- .Values.federatedcatalog.image.repository -}}
{{- else -}}
{{- printf "%s/%s-federated-catalog-postgresql-%s" (required "global.image.baseRepository is required when federatedcatalog.image.repository is not set" .Values.global.image.baseRepository) (required "global.image.namePrefix is required when federatedcatalog.image.repository is not set" .Values.global.image.namePrefix) (include "dse.federatedcatalog.vaultProviderImageSuffix" .) -}}
{{- end -}}
{{- end }}

{{/* JDBC URL with global fallback */}}
{{- define "dse.federatedcatalog.postgresql.jdbcUrl" -}}
{{- if .Values.federatedcatalog.postgresql.jdbcUrl -}}
{{- .Values.federatedcatalog.postgresql.jdbcUrl -}}
{{- else -}}
{{- printf "jdbc:postgresql://%s:5432/%s" (required "global.db.serverFqdn is required when federatedcatalog.postgresql.jdbcUrl is not set" .Values.global.db.serverFqdn) (required "global.db.name is required when federatedcatalog.postgresql.jdbcUrl is not set" .Values.global.db.name) -}}
{{- end -}}
{{- end }}

{{/* DB credentials secret name with global fallback */}}
{{- define "dse.federatedcatalog.postgresql.secretName" -}}
{{- if .Values.federatedcatalog.postgresql.credentials.secret.name -}}
{{- .Values.federatedcatalog.postgresql.credentials.secret.name -}}
{{- else if .Values.global.db.credentials.secret.name -}}
{{- .Values.global.db.credentials.secret.name -}}
{{- else -}}
{{- printf "%s-db" (required "global.participantName is required when neither federatedcatalog.postgresql.credentials.secret.name nor global.db.credentials.secret.name is set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/* Vault URL with global fallback */}}
{{- define "dse.federatedcatalog.vault.hashicorp.url" -}}
{{- if .Values.federatedcatalog.vault.hashicorp.url -}}
{{- .Values.federatedcatalog.vault.hashicorp.url -}}
{{- else -}}
{{- .Values.global.vault.url -}}
{{- end -}}
{{- end }}

{{/* Vault token secret name with global participantName fallback */}}
{{- define "dse.federatedcatalog.vault.hashicorp.tokenSecretName" -}}
{{- if .Values.federatedcatalog.vault.hashicorp.token.secret.name -}}
{{- .Values.federatedcatalog.vault.hashicorp.token.secret.name -}}
{{- else -}}
{{- printf "%s-vault-token" (required "global.participantName is required when federatedcatalog.vault.hashicorp.token.secret.name is not set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/* Vault folder with global participantName fallback */}}
{{- define "dse.federatedcatalog.vault.hashicorp.folder" -}}
{{- if .Values.federatedcatalog.vault.hashicorp.paths.folder -}}
{{- .Values.federatedcatalog.vault.hashicorp.paths.folder -}}
{{- else -}}
{{- .Values.global.participantName -}}
{{- end -}}
{{- end }}

{{/* STS token URL with global fallback */}}
{{- define "dse.federatedcatalog.sts.tokenUrl" -}}
{{- if .Values.federatedcatalog.sts.tokenUrl -}}
{{- .Values.federatedcatalog.sts.tokenUrl -}}
{{- else -}}
{{- $scheme := ternary "https" "http" (eq (include "dse.federatedcatalog.didWebUseHttps" .) "true") -}}
{{- printf "%s://%s-identityhub:8484/api/sts/token" $scheme .Release.Name -}}
{{- end -}}
{{- end }}

{{/* STS client ID with global fallback */}}
{{- define "dse.federatedcatalog.sts.clientId" -}}
{{- if .Values.federatedcatalog.sts.clientId -}}
{{- .Values.federatedcatalog.sts.clientId -}}
{{- else -}}
{{- include "dse.federatedcatalog.didWebUrl" . -}}
{{- end -}}
{{- end }}

{{/* STS client secret alias with derived fallback */}}
{{- define "dse.federatedcatalog.sts.clientSecretAlias" -}}
{{- if .Values.federatedcatalog.sts.clientSecretAlias -}}
{{- .Values.federatedcatalog.sts.clientSecretAlias -}}
{{- else -}}
{{- printf "%s-sts-client-secret" (include "dse.federatedcatalog.sts.clientId" .) -}}
{{- end -}}
{{- end }}

{{/* AES key alias with global fallback */}}
{{- define "dse.federatedcatalog.keys.encryption.aesKeyAlias" -}}
{{- if .Values.federatedcatalog.keys.encryption.aesKeyAlias -}}
{{- .Values.federatedcatalog.keys.encryption.aesKeyAlias -}}
{{- else if .Values.global.keys.aesKeyAlias -}}
{{- .Values.global.keys.aesKeyAlias -}}
{{- else -}}
{{- printf "%s-aes" (required "global.participantName is required when federatedcatalog.keys.encryption.aesKeyAlias is not set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}
{{/*
Federated Catalog - Azure Key Vault name. Uses the explicit value if set, otherwise
falls back to global.vault.azure.name (shared across every component of the
participant).
*/}}
{{- define "dse.federatedcatalog.vaultAzureName" -}}
{{- if .Values.federatedcatalog.vault.azure.name -}}
{{- .Values.federatedcatalog.vault.azure.name -}}
{{- else -}}
{{- .Values.global.vault.azure.name -}}
{{- end -}}
{{- end }}

{{/*
Federated Catalog - Azure Key Vault URL override. Uses the explicit value if set,
otherwise falls back to global.vault.azure.url.
*/}}
{{- define "dse.federatedcatalog.vaultAzureUrl" -}}
{{- if .Values.federatedcatalog.vault.azure.url -}}
{{- .Values.federatedcatalog.vault.azure.url -}}
{{- else -}}
{{- .Values.global.vault.azure.url -}}
{{- end -}}
{{- end }}

{{/*
Federated Catalog - Azure Key Vault URL override "unsafe" flag. Uses the explicit
value if set, otherwise falls back to global.vault.azure.unsafe.
*/}}
{{- define "dse.federatedcatalog.vaultAzureUnsafe" -}}
{{- if hasKey .Values.federatedcatalog.vault.azure "unsafe" -}}
{{- .Values.federatedcatalog.vault.azure.unsafe -}}
{{- else -}}
{{- .Values.global.vault.azure.unsafe -}}
{{- end -}}
{{- end }}

{{/*
Federated Catalog - Azure Key Vault credentials secret name. Uses the explicit value
if set, otherwise falls back to global.vault.azure.credentials.secret.name
(shared across every component using the same Service Principal).
*/}}
{{- define "dse.federatedcatalog.vaultAzureCredentialsSecretName" -}}
{{- if .Values.federatedcatalog.vault.azure.credentials.secret.name -}}
{{- .Values.federatedcatalog.vault.azure.credentials.secret.name -}}
{{- else -}}
{{- .Values.global.vault.azure.credentials.secret.name -}}
{{- end -}}
{{- end }}

{{/*
Federated Catalog - Azure Key Vault credentials secret key holding the Service
Principal client ID. Uses the explicit value if set, otherwise falls back
to global.vault.azure.credentials.secret.clientIdKey.
*/}}
{{- define "dse.federatedcatalog.vaultAzureCredentialsClientIdKey" -}}
{{- if .Values.federatedcatalog.vault.azure.credentials.secret.clientIdKey -}}
{{- .Values.federatedcatalog.vault.azure.credentials.secret.clientIdKey -}}
{{- else -}}
{{- .Values.global.vault.azure.credentials.secret.clientIdKey -}}
{{- end -}}
{{- end }}

{{/*
Federated Catalog - Azure Key Vault credentials secret key holding the Tenant ID.
Uses the explicit value if set, otherwise falls back to
global.vault.azure.credentials.secret.tenantIdKey.
*/}}
{{- define "dse.federatedcatalog.vaultAzureCredentialsTenantIdKey" -}}
{{- if .Values.federatedcatalog.vault.azure.credentials.secret.tenantIdKey -}}
{{- .Values.federatedcatalog.vault.azure.credentials.secret.tenantIdKey -}}
{{- else -}}
{{- .Values.global.vault.azure.credentials.secret.tenantIdKey -}}
{{- end -}}
{{- end }}

{{/*
Federated Catalog - Azure Key Vault credentials secret key holding the Service
Principal client secret. Uses the explicit value if set, otherwise falls
back to global.vault.azure.credentials.secret.clientSecretKey.
*/}}
{{- define "dse.federatedcatalog.vaultAzureCredentialsClientSecretKey" -}}
{{- if .Values.federatedcatalog.vault.azure.credentials.secret.clientSecretKey -}}
{{- .Values.federatedcatalog.vault.azure.credentials.secret.clientSecretKey -}}
{{- else -}}
{{- .Values.global.vault.azure.credentials.secret.clientSecretKey -}}
{{- end -}}
{{- end }}

{{/*
Federated Catalog - Azure Key Vault credentials secret key holding the Service
Principal client certificate. Uses the explicit value if set, otherwise
falls back to global.vault.azure.credentials.secret.clientCertificateKey.
*/}}
{{- define "dse.federatedcatalog.vaultAzureCredentialsClientCertificateKey" -}}
{{- if .Values.federatedcatalog.vault.azure.credentials.secret.clientCertificateKey -}}
{{- .Values.federatedcatalog.vault.azure.credentials.secret.clientCertificateKey -}}
{{- else -}}
{{- .Values.global.vault.azure.credentials.secret.clientCertificateKey -}}
{{- end -}}
{{- end }}

{{/*
Federated Catalog - whether Azure Key Vault is enabled: either the component sets
its own vault.azure.name explicitly, or global.vault.azure.name is set
and global.vaultProvider selects Azure. Centralizes the enablement
condition so the env-var block and the client-certificate volume/mount
stay consistent (this is the single source of truth for "is Azure Vault
active for this component").
*/}}
{{- define "dse.federatedcatalog.vaultAzureEnabled" -}}
{{- if or .Values.federatedcatalog.vault.azure.name (and .Values.global.vault.azure.name (or (eq .Values.global.vaultProvider "azure") (eq .Values.global.vaultProvider "azurevault"))) -}}true{{- end -}}
{{- end }}
