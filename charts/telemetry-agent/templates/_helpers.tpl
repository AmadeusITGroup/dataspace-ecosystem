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
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Did Web URL
*/}}
{{- define "dse.telemetryagent.did.web.url" -}}
{{- if .Values.telemetryagent.did.web.url }}{{/* if did web url has been specified explicitly */}}
{{- .Values.telemetryagent.did.web.url }}
{{- else }}{{/* else when did api url has not been specified explicitly */}}
{{- with .Values.telemetryagent.ingress }}
{{- if and .enabled .hostname }}{{/* if ingress enabled and hostname defined */}}
{{- printf "did:web:%s:webdid" .hostname -}}
{{- else }}{{/* else when ingress not enabled */}}
{{- printf "did:web:%s%3A%v:api:did" (include "dse.fullname" $ ) $.Values.telemetryagent.endpoints.did.port -}}
{{- end }}{{/* end if ingress */}}
{{- end }}{{/* end with ingress */}}
{{- end }}{{/* end if .Values.ssi.did.web.url */}}
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