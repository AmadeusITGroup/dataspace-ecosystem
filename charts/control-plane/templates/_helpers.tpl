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
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Control Plane - Control URL
*/}}
{{- define "dse.controlplane.url.control" -}}
{{- printf "http://%s:%v%s" ( include "dse.fullname" $ ) $.Values.controlplane.endpoints.control.port $.Values.controlplane.endpoints.control.path -}}
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
{{- printf "http://%s:%v%s" (include "dse.fullname" $ ) $.Values.controlplane.endpoints.protocol.port $.Values.controlplane.endpoints.protocol.path -}}
{{- end }}{{/* end if ingress */}}
{{- end }}{{/* end with ingress */}}
{{- end }}{{/* end if .Values.controlplane.url.protocol */}}
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