{{/*
Expand the name of the chart.
*/}}
{{- define "kafka-proxy.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "kafka-proxy.fullname" -}}
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
{{- define "kafka-proxy.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "kafka-proxy.labels" -}}
helm.sh/chart: {{ include "kafka-proxy.chart" . }}
{{ include "kafka-proxy.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/component: kafka-proxy-manager
{{- end }}

{{/*
Selector labels
*/}}
{{- define "kafka-proxy.selectorLabels" -}}
app.kubernetes.io/name: {{ include "kafka-proxy.name" . }}
app.kubernetes.io/instance: {{ .Values.kafkaProxy.manager.instanceOverride | default .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "kafka-proxy.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "kafka-proxy.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Generate the docker image name
*/}}
{{- define "kafka-proxy.image" -}}
{{- $repository := .Values.kafkaProxy.manager.image.repository -}}
{{- if not $repository -}}
{{- $repository = printf "%s/%s-kafka-proxy-k8s-manager" (required "global.image.baseRepository is required when kafkaProxy.manager.image.repository is not set" .Values.global.image.baseRepository) (required "global.image.namePrefix is required when kafkaProxy.manager.image.repository is not set" .Values.global.image.namePrefix) -}}
{{- end -}}
{{- $registry := default .Values.global.imageRegistry .Values.kafkaProxy.manager.image.registry }}
{{- $tag := required "kafkaProxy.manager.image.tag is required" .Values.kafkaProxy.manager.image.tag }}
{{- if $registry }}
{{- printf "%s/%s:%s" $registry $repository $tag }}
{{- else }}
{{- printf "%s:%s" $repository $tag }}
{{- end }}
{{- end }}

{{/*
Kafka Proxy - Namespace where proxy resources are managed. Defaults to the
Helm release namespace but remains explicitly overridable.
*/}}
{{- define "kafka-proxy.targetNamespace" -}}
{{- .Values.kafkaProxy.manager.namespace | default .Release.Namespace -}}
{{- end }}

{{/*
Kafka Proxy - Vault URL. Uses the explicit manager value, then the umbrella
global value, and finally preserves the legacy standalone default.
*/}}
{{- define "kafka-proxy.vaultAddr" -}}
{{- if .Values.kafkaProxy.manager.vaultAddr -}}
{{- .Values.kafkaProxy.manager.vaultAddr -}}
{{- else if .Values.global.vault.url -}}
{{- .Values.global.vault.url -}}
{{- else -}}
http://vault:8200
{{- end -}}
{{- end }}

{{/*
Kafka Proxy - Proxy/auth image ("<baseRepository>/<namePrefix>-kafka-proxy-auth:<tag>").
Uses the explicit value if set, derives it from global.image.* for umbrella
deployments, and otherwise preserves the legacy standalone image.
*/}}
{{- define "kafka-proxy.proxyImage" -}}
{{- if .Values.kafkaProxy.manager.proxyImage -}}
{{- .Values.kafkaProxy.manager.proxyImage -}}
{{- else if and .Values.global.image.baseRepository .Values.global.image.namePrefix -}}
{{- printf "%s/%s-kafka-proxy-auth:%s" (required "global.image.baseRepository is required when kafkaProxy.manager.proxyImage is not set" .Values.global.image.baseRepository) (required "global.image.namePrefix is required when kafkaProxy.manager.proxyImage is not set" .Values.global.image.namePrefix) (required "kafkaProxy.manager.image.tag is required" .Values.kafkaProxy.manager.image.tag) -}}
{{- else -}}
grepplabs/kafka-proxy:0.4.3-all
{{- end -}}
{{- end }}

{{/*
Kafka Proxy - Vault folder. Uses the explicit value if set, otherwise falls
back to global.participantName.
*/}}
{{- define "kafka-proxy.vaultFolder" -}}
{{- if .Values.kafkaProxy.manager.vaultFolder -}}
{{- .Values.kafkaProxy.manager.vaultFolder -}}
{{- else -}}
{{- .Values.global.participantName -}}
{{- end -}}
{{- end }}

{{/*
Kafka Proxy - Vault token secret name. Uses the explicit value if set,
otherwise derives it from global.participantName ("<participantName>-vault-token").
*/}}
{{- define "kafka-proxy.vaultTokenSecretName" -}}
{{- if .Values.kafkaProxy.manager.vaultTokenSecret.name -}}
{{- .Values.kafkaProxy.manager.vaultTokenSecret.name -}}
{{- else if .Values.global.participantName -}}
{{- printf "%s-vault-token" .Values.global.participantName -}}
{{- else -}}
vault-token
{{- end -}}
{{- end }}

{{/*
Kafka Proxy - Participant id. Uses the explicit value if set, otherwise falls
back to global.participantName.
*/}}
{{- define "kafka-proxy.participantId" -}}
{{- if .Values.kafkaProxy.manager.participantId -}}
{{- .Values.kafkaProxy.manager.participantId -}}
{{- else -}}
{{- .Values.global.participantName -}}
{{- end -}}
{{- end }}

{{/*
Generate environment variables
*/}}
{{- define "kafka-proxy.env" -}}
- name: EDC_FS_CONFIG
  value: /app/configuration.properties
- name: KAFKA_PROXY_VAULT_TOKEN
  valueFrom:
    secretKeyRef:
      name: {{ include "kafka-proxy.vaultTokenSecretName" . }}
      key: {{ .Values.kafkaProxy.manager.vaultTokenSecret.key }}
{{- if and .Values.kafkaProxy.manager.edc .Values.kafkaProxy.manager.edc.keystore }}
- name: EDC_KEYSTORE
  value: {{ .Values.kafkaProxy.manager.edc.keystore | quote }}
- name: EDC_KEYSTORE_PASSWORD
  value: {{ .Values.kafkaProxy.manager.edc.keystorePassword | quote }}
{{- end }}
{{- if and .Values.kafkaProxy.manager.edc .Values.kafkaProxy.manager.edc.vault .Values.kafkaProxy.manager.edc.vault.clientId }}
- name: EDC_VAULT_CLIENTID
  value: {{ .Values.kafkaProxy.manager.edc.vault.clientId | quote }}
- name: EDC_VAULT_TENANTID
  value: {{ .Values.kafkaProxy.manager.edc.vault.tenantId | quote }}
- name: EDC_VAULT_CLIENTSECRET
  value: {{ .Values.kafkaProxy.manager.edc.vault.clientSecret | quote }}
- name: EDC_VAULT_NAME
  value: {{ .Values.kafkaProxy.manager.edc.vault.name | quote }}
{{- end }}
{{- if .Values.kafkaProxy.manager.participantId }}
- name: EDC_PARTICIPANT_ID
  value: {{ .Values.kafkaProxy.manager.participantId | quote }}
{{- end }}
{{- if and .Values.kafkaProxy.manager.vaultTls.enabled .Values.kafkaProxy.manager.vaultTls.caCert.secret }}
- name: VAULT_SSL_CERT
  value: {{ .Values.kafkaProxy.manager.vaultTls.caCert.path | default "/vault-ca/ca.crt" | quote }}
{{- end }}
{{- range .Values.extraEnv }}
- name: {{ .name }}
  {{- if .value }}
  value: {{ .value | quote }}
  {{- else if .valueFrom }}
  valueFrom:
    {{- toYaml .valueFrom | nindent 4 }}
  {{- end }}
{{- end }}
{{- end }}

{{/*
Generate volume mounts
*/}}
{{- define "kafka-proxy.volumeMounts" -}}
- name: config
  mountPath: /app/configuration.properties
  subPath: configuration.properties
  readOnly: true
{{- if .Values.persistence.shared.enabled }}
- name: shared-data
  mountPath: {{ .Values.kafkaProxy.manager.sharedDir }}
{{- end }}
{{- if and .Values.kafkaProxy.manager.vaultTls.enabled .Values.kafkaProxy.manager.vaultTls.caCert.secret }}
{{- $certPath := .Values.kafkaProxy.manager.vaultTls.caCert.path | default "/vault-ca/ca.crt" }}
{{- $mountPath := dir $certPath }}
{{- $fileName := base $certPath }}
- name: vault-ca
  mountPath: {{ $certPath }}
  subPath: {{ $fileName }}
  readOnly: true
{{- end }}
{{- range .Values.extraVolumeMounts }}
- {{ toYaml . | nindent 2 }}
{{- end }}
{{- end }}

{{/*
Generate volumes
*/}}
{{- define "kafka-proxy.volumes" -}}
- name: config
  configMap:
    name: {{ include "kafka-proxy.fullname" . }}-config
{{- if .Values.persistence.shared.enabled }}
- name: shared-data
  persistentVolumeClaim:
    claimName: {{ include "kafka-proxy.fullname" . }}-shared
{{- end }}
{{- if and .Values.kafkaProxy.manager.vaultTls.enabled .Values.kafkaProxy.manager.vaultTls.caCert.secret }}
{{- $certPath := .Values.kafkaProxy.manager.vaultTls.caCert.path | default "/vault-ca/ca.crt" }}
{{- $fileName := base $certPath }}
- name: vault-ca
  secret:
    secretName: {{ .Values.kafkaProxy.manager.vaultTls.caCert.secret }}
    items:
      - key: {{ .Values.kafkaProxy.manager.vaultTls.caCert.key }}
        path: {{ $fileName }}
{{- else if and .Values.kafkaProxy.manager.vaultTls.enabled .Values.kafkaProxy.manager.vaultTls.caCert.inline }}
- name: vault-ca
  configMap:
    name: {{ include "kafka-proxy.fullname" . }}-vault-ca
{{- end }}
{{- range .Values.extraVolumes }}
- {{ toYaml . | nindent 2 }}
{{- end }}
{{- end }}