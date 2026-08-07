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
Telemetry Agent Common labels
*/}}
{{- define "dse.telemetryagent.labels" -}}
helm.sh/chart: {{ include "dse.chart" . }}
{{ include "dse.telemetryagent.selectorLabels" . }}
{{- if .Values.telemetryagent.image.tag }}
app.kubernetes.io/version: {{ .Values.telemetryagent.image.tag | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/component: edc-telemetryagent
app.kubernetes.io/part-of: edc
{{- end }}

{{/*
Telemetry Agent Selector labels
*/}}
{{- define "dse.telemetryagent.selectorLabels" -}}
app.kubernetes.io/name: {{ include "dse.name" . }}
app.kubernetes.io/instance: {{ .Values.telemetryagent.instanceOverride | default .Release.Name }}
{{- end }}

{{/*
Did Web URL
*/}}
{{- define "dse.telemetryagent.did.web.url" -}}
{{- if .Values.telemetryagent.did.web.url }}{{/* if did web url has been specified explicitly */}}
{{- .Values.telemetryagent.did.web.url }}
{{- else if .Values.global.identityHub.didWebUrl }}{{/* else if the umbrella/global identity hub DID has been supplied - this component authenticates as the identity hub's participant identity via STS */}}
{{- .Values.global.identityHub.didWebUrl }}
{{- else }}{{/* else when did api url has not been specified explicitly */}}
{{- with .Values.telemetryagent.ingress }}
{{- if and .enabled .hostname }}{{/* if ingress enabled and hostname defined */}}
{{- printf "did:web:%s:webdid" .hostname -}}
{{- else }}{{/* else when ingress not enabled */}}
{{- printf "did:web:%s%3A%v:api:did" (include "dse.fullname" $ ) (required "telemetryagent.did.web.port is required when did.web.url/global.identityHub.didWebUrl are unset and ingress is disabled" $.Values.telemetryagent.did.web.port) -}}
{{- end }}{{/* end if ingress */}}
{{- end }}{{/* end with ingress */}}
{{- end }}{{/* end if .Values.ssi.did.web.url */}}
{{- end }}

{{/*
Telemetry Agent - DID Web HTTPS setting. A component override (including an
explicit false) wins, followed by global.useHttps, then the legacy false
standalone default.
*/}}
{{- define "dse.telemetryagent.did.web.useHttps" -}}
{{- if kindIs "bool" .Values.telemetryagent.did.web.useHttps -}}
{{- .Values.telemetryagent.did.web.useHttps -}}
{{- else if kindIs "bool" .Values.global.useHttps -}}
{{- .Values.global.useHttps -}}
{{- else -}}
false
{{- end -}}
{{- end }}

{{/*
Telemetry Agent - image suffix for the selected Vault provider. Friendly
provider names are mapped to the suffixes used by published images; existing
explicit suffixes remain valid.
*/}}
{{- define "dse.telemetryagent.vaultProviderImageSuffix" -}}
{{- $provider := required "global.vaultProvider is required when telemetryagent.image.repository is not set" .Values.global.vaultProvider -}}
{{- if eq $provider "hashicorp" -}}
hashicorpvault
{{- else if eq $provider "azure" -}}
azurevault
{{- else -}}
{{- $provider -}}
{{- end -}}
{{- end }}

{{/*
Telemetry Agent - Image repository. Uses the explicit value if set, otherwise
derives it from global.image.* + global.vaultProvider.
*/}}
{{- define "dse.telemetryagent.imageRepository" -}}
{{- if .Values.telemetryagent.image.repository -}}
{{- .Values.telemetryagent.image.repository -}}
{{- else -}}
{{- printf "%s/%s-telemetry-agent-postgresql-%s" (required "global.image.baseRepository is required when telemetryagent.image.repository is not set" .Values.global.image.baseRepository) (required "global.image.namePrefix is required when telemetryagent.image.repository is not set" .Values.global.image.namePrefix) (include "dse.telemetryagent.vaultProviderImageSuffix" .) -}}
{{- end -}}
{{- end }}

{{/*
Telemetry Agent - JDBC URL. Uses the explicit value if set, otherwise derives
it from global.db.serverFqdn/name.
*/}}
{{- define "dse.telemetryagent.jdbcUrl" -}}
{{- if .Values.telemetryagent.postgresql.jdbcUrl -}}
{{- .Values.telemetryagent.postgresql.jdbcUrl -}}
{{- else -}}
{{- printf "jdbc:postgresql://%s/%s" (required "global.db.serverFqdn is required when telemetryagent.postgresql.jdbcUrl is not set" .Values.global.db.serverFqdn) (required "global.db.name is required when telemetryagent.postgresql.jdbcUrl is not set" .Values.global.db.name) -}}
{{- end -}}
{{- end }}

{{/*
Telemetry Agent - STS token URL (this participant's identity hub). Uses the
explicit value if set, otherwise derives it from the identity hub's default
release-name-based service address ("<participantName>-identityhub").
*/}}
{{- define "dse.telemetryagent.sts.tokenUrl" -}}
{{- if .Values.telemetryagent.sts.tokenUrl -}}
{{- .Values.telemetryagent.sts.tokenUrl -}}
{{- else -}}
{{- printf "https://%s-identityhub:8484/api/sts/token" (required "global.participantName is required when telemetryagent.sts.tokenUrl is not set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/*
Telemetry Agent - STS client secret alias. Uses the explicit value if set,
otherwise derives it from this component's DID Web URL
("<didWebUrl>-sts-client-secret").
*/}}
{{- define "dse.telemetryagent.sts.clientSecretAlias" -}}
{{- if .Values.telemetryagent.sts.clientSecretAlias -}}
{{- .Values.telemetryagent.sts.clientSecretAlias -}}
{{- else -}}
{{- printf "%s-sts-client-secret" (include "dse.telemetryagent.did.web.url" .) -}}
{{- end -}}
{{- end }}

{{/*
Telemetry Agent - STS client id. Uses the explicit value if set, otherwise
falls back to this component's DID Web URL.
*/}}
{{- define "dse.telemetryagent.sts.clientId" -}}
{{- if .Values.telemetryagent.sts.clientId -}}
{{- .Values.telemetryagent.sts.clientId -}}
{{- else -}}
{{- include "dse.telemetryagent.did.web.url" . -}}
{{- end -}}
{{- end }}

{{/*
Telemetry Agent - STS public key vault alias. Uses the explicit value if set,
otherwise derives it from this component's DID Web URL ("<didWebUrl>#my-key").
*/}}
{{- define "dse.telemetryagent.keys.sts.publicKeyVaultAlias" -}}
{{- if .Values.telemetryagent.keys.sts.publicKeyVaultAlias -}}
{{- .Values.telemetryagent.keys.sts.publicKeyVaultAlias -}}
{{- else -}}
{{- printf "%s#my-key" (include "dse.telemetryagent.did.web.url" .) -}}
{{- end -}}
{{- end }}

{{/*
Telemetry Agent - Authority DID. Uses the explicit value if set, otherwise
falls back to global.authority.didWebUrl.
*/}}
{{- define "dse.telemetryagent.authorityDid" -}}
{{- if .Values.telemetryagent.authority.did -}}
{{- .Values.telemetryagent.authority.did -}}
{{- else -}}
{{- required "global.authority.didWebUrl is required when telemetryagent.authority.did is not set" .Values.global.authority.didWebUrl -}}
{{- end -}}
{{- end }}

{{/*
Telemetry Agent - Hashicorp Vault URL. Uses the explicit value if set,
otherwise falls back to global.vault.url.
*/}}
{{- define "dse.telemetryagent.vaultUrl" -}}
{{- if .Values.telemetryagent.vault.hashicorp.url -}}
{{- .Values.telemetryagent.vault.hashicorp.url -}}
{{- else -}}
{{- .Values.global.vault.url -}}
{{- end -}}
{{- end }}

{{/*
Telemetry Agent - Hashicorp Vault token secret name. Derived from
global.participantName unless explicitly overridden.
*/}}
{{- define "dse.telemetryagent.vaultTokenSecretName" -}}
{{- if .Values.telemetryagent.vault.hashicorp.token.secret.name -}}
{{- .Values.telemetryagent.vault.hashicorp.token.secret.name -}}
{{- else -}}
{{- printf "%s-vault-token" (required "global.participantName is required when telemetryagent.vault.hashicorp.token.secret.name is not set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/*
Telemetry Agent - Hashicorp Vault folder. Derived from global.participantName
unless explicitly overridden.
*/}}
{{- define "dse.telemetryagent.vaultFolder" -}}
{{- if .Values.telemetryagent.vault.hashicorp.paths.folder -}}
{{- .Values.telemetryagent.vault.hashicorp.paths.folder -}}
{{- else if .Values.global.participantName -}}
{{- .Values.global.participantName -}}
{{- else -}}
{{- "" -}}
{{- end -}}
{{- end }}

{{/*
Telemetry Agent - PostgreSQL credentials secret name. Falls back to
global.db.credentials.secret.name (shared across every component using the
same DB user, matching Terraform's "<participantName>-db" convention), which
itself derives from global.participantName unless explicitly overridden.
*/}}
{{- define "dse.telemetryagent.postgresql.credentials.secretName" -}}
{{- if .Values.telemetryagent.postgresql.credentials.secret.name -}}
{{- .Values.telemetryagent.postgresql.credentials.secret.name -}}
{{- else if .Values.global.db.credentials.secret.name -}}
{{- .Values.global.db.credentials.secret.name -}}
{{- else -}}
{{- printf "%s-db" (required "global.participantName is required when neither telemetryagent.postgresql.credentials.secret.name nor global.db.credentials.secret.name is set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/*
Telemetry Agent - Credential Manager private key vault alias. Falls back to
global.keys.privateKeyAlias (shared across data-plane, identity-hub, and
telemetry-agent, matching Terraform's "<participantName>-privatekey"
convention), which itself derives from global.participantName unless
explicitly overridden.
*/}}
{{- define "dse.telemetryagent.credentialmanager.privatekey.alias" -}}
{{- if .Values.telemetryagent.credentialmanager.privatekey.alias -}}
{{- .Values.telemetryagent.credentialmanager.privatekey.alias -}}
{{- else if .Values.global.keys.privateKeyAlias -}}
{{- .Values.global.keys.privateKeyAlias -}}
{{- else -}}
{{- printf "%s-privatekey" (required "global.participantName is required when neither telemetryagent.credentialmanager.privatekey.alias nor global.keys.privateKeyAlias is set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{/*
Telemetry Agent - AES key vault alias used to encrypt participant-context
config (required since EDC 0.16.0's encryption-algorithm registry). Falls
back to global.keys.aesKeyAlias (shared across control-plane, data-plane,
identity-hub, and telemetry-agent, matching Terraform's "aes_key_alias",
"<participantName>-aes" convention), which itself derives from
global.participantName unless explicitly overridden.
*/}}
{{- define "dse.telemetryagent.keys.encryption.aesKeyAlias" -}}
{{- if .Values.telemetryagent.keys.encryption.aesKeyAlias -}}
{{- .Values.telemetryagent.keys.encryption.aesKeyAlias -}}
{{- else if .Values.global.keys.aesKeyAlias -}}
{{- .Values.global.keys.aesKeyAlias -}}
{{- else -}}
{{- printf "%s-aes" (required "global.participantName is required when neither telemetryagent.keys.encryption.aesKeyAlias nor global.keys.aesKeyAlias is set" .Values.global.participantName) -}}
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