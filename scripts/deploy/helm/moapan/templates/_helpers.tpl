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

{{- define "moapan.mailEnv" -}}
{{- if .Values.mail.enabled }}
- {name: MOAPAN_MAIL_ENABLED, value: "true"}
- {name: MOAPAN_MAIL_HOST, value: {{ if .Values.mail.server.enabled }}"mailbox-smtp"{{ else }}{{ required "mail.host is required" .Values.mail.host | quote }}{{ end }}}
- {name: MOAPAN_MAIL_PORT, value: {{ if .Values.mail.server.enabled }}"25"{{ else }}{{ .Values.mail.port | quote }}{{ end }}}
- {name: MOAPAN_MAIL_FROM, value: {{ required "mail.from is required" .Values.mail.from | quote }}}
- {name: MOAPAN_MAIL_TLS, value: {{ .Values.mail.tls | quote }}}
- {name: MOAPAN_MAIL_USERNAME, value: {{ .Values.mail.username | quote }}}
- {name: MOAPAN_MAIL_DATA_KEY_FILE, value: /run/secrets/email/data-key}
{{- if .Values.mail.username }}
- {name: MOAPAN_MAIL_PASSWORD_FILE, value: /run/secrets/email/smtp-password}
{{- end }}
{{- if .Values.mail.customCa }}
- {name: MOAPAN_MAIL_CA_FILE, value: /run/secrets/email/ca.crt}
{{- end }}
{{- end }}
{{- end -}}
