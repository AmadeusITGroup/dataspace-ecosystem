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
Telemetry Storage Common labels
*/}}
{{- define "dse.telemetrystorage.labels" -}}
helm.sh/chart: {{ include "dse.chart" . }}
{{ include "dse.telemetrystorage.selectorLabels" . }}
{{- if .Values.telemetrystorage.image.tag }}
app.kubernetes.io/version: {{ .Values.telemetrystorage.image.tag | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/component: edc-telemetrystorage
app.kubernetes.io/part-of: edc
{{- end }}

{{/*
Telemetry Storage Selector labels
*/}}
{{- define "dse.telemetrystorage.selectorLabels" -}}
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

{{- define "dse.telemetrystorage.didWebUrl" -}}
{{- if .Values.telemetrystorage.did.web.url -}}
{{- .Values.telemetrystorage.did.web.url -}}
{{- else if .Values.global.identityHub.didWebUrl -}}
{{- .Values.global.identityHub.didWebUrl -}}
{{- else -}}
{{- required ".Values.telemetrystorage.did.web.url or global.identityHub.didWebUrl is required" .Values.telemetrystorage.did.web.url -}}
{{- end -}}
{{- end }}

{{- define "dse.telemetrystorage.didWebUseHttps" -}}
{{- if kindIs "bool" .Values.telemetrystorage.did.web.useHttps -}}
{{- .Values.telemetrystorage.did.web.useHttps -}}
{{- else if kindIs "bool" .Values.global.useHttps -}}
{{- .Values.global.useHttps -}}
{{- else -}}
false
{{- end -}}
{{- end }}

{{- define "dse.telemetrystorage.image.repository" -}}
{{- if .Values.telemetrystorage.image.repository -}}
{{- .Values.telemetrystorage.image.repository -}}
{{- else -}}
{{- printf "%s/%s-telemetry-storage" (required "global.image.baseRepository is required when telemetrystorage.image.repository is not set" .Values.global.image.baseRepository) (required "global.image.namePrefix is required when telemetrystorage.image.repository is not set" .Values.global.image.namePrefix) -}}
{{- end -}}
{{- end }}

{{- define "dse.telemetrystorage.postgresql.jdbcUrl" -}}
{{- if .Values.telemetrystorage.postgresql.jdbcUrl -}}
{{- .Values.telemetrystorage.postgresql.jdbcUrl -}}
{{- else -}}
{{- printf "jdbc:postgresql://%s:5432/%s" (required "global.db.serverFqdn is required when telemetrystorage.postgresql.jdbcUrl is not set" .Values.global.db.serverFqdn) (required "global.db.name is required when telemetrystorage.postgresql.jdbcUrl is not set" .Values.global.db.name) -}}
{{- end -}}
{{- end }}

{{- define "dse.telemetrystorage.postgresql.secretName" -}}
{{- if .Values.telemetrystorage.postgresql.credentials.secret.name -}}
{{- .Values.telemetrystorage.postgresql.credentials.secret.name -}}
{{- else if .Values.global.db.credentials.secret.name -}}
{{- .Values.global.db.credentials.secret.name -}}
{{- else -}}
{{- printf "%s-db" (required "global.participantName is required when neither telemetrystorage.postgresql.credentials.secret.name nor global.db.credentials.secret.name is set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}

{{- define "dse.telemetrystorage.keys.encryption.aesKeyAlias" -}}
{{- if .Values.telemetrystorage.keys.encryption.aesKeyAlias -}}
{{- .Values.telemetrystorage.keys.encryption.aesKeyAlias -}}
{{- else if .Values.global.keys.aesKeyAlias -}}
{{- .Values.global.keys.aesKeyAlias -}}
{{- else -}}
{{- printf "%s-aes" (required "global.participantName is required when telemetrystorage.keys.encryption.aesKeyAlias is not set" .Values.global.participantName) -}}
{{- end -}}
{{- end }}