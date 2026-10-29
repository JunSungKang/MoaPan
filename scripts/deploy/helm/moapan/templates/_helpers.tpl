{{- define "moapan.image" -}}
{{- if not (regexMatch "^.+@sha256:[a-f0-9]{64}$" .) -}}{{ fail "All images must be registry references pinned with @sha256 digest" }}{{- end -}}
{{ . | quote }}
{{- end -}}
{{- define "moapan.secretEnv" -}}
- name: DATABASE_PASSWORD
  valueFrom: {secretKeyRef: {name: {{ required "existingSecret is required" .Values.existingSecret | quote }}, key: DATABASE_PASSWORD}}
- name: MOAPAN_SECRET
  valueFrom: {secretKeyRef: {name: {{ .Values.existingSecret | quote }}, key: MOAPAN_SECRET}}
{{- end -}}
