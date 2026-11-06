{{- define "damgox2.image" -}}
{{- if not (regexMatch "^.+@sha256:[a-f0-9]{64}$" .) -}}{{ fail "All images must be registry references pinned with @sha256 digest" }}{{- end -}}
{{ . | quote }}
{{- end -}}
{{- define "damgox2.secretEnv" -}}
- name: DATABASE_PASSWORD
  valueFrom: {secretKeyRef: {name: {{ required "existingSecret is required" .Values.existingSecret | quote }}, key: DATABASE_PASSWORD}}
- name: DAMGOX2_SECRET
  valueFrom: {secretKeyRef: {name: {{ .Values.existingSecret | quote }}, key: DAMGOX2_SECRET}}
{{- end -}}

{{- define "damgox2.mailEnv" -}}
{{- if .Values.mail.enabled }}
- {name: DAMGOX2_MAIL_ENABLED, value: "true"}
- {name: DAMGOX2_MAIL_HOST, value: {{ required "mail.host is required" .Values.mail.host | quote }}}
- {name: DAMGOX2_MAIL_PORT, value: {{ .Values.mail.port | quote }}}
- {name: DAMGOX2_MAIL_FROM, value: {{ required "mail.from is required" .Values.mail.from | quote }}}
- {name: DAMGOX2_MAIL_TLS, value: {{ .Values.mail.tls | quote }}}
- {name: DAMGOX2_MAIL_USERNAME, value: {{ .Values.mail.username | quote }}}
- {name: DAMGOX2_MAIL_DATA_KEY_FILE, value: /run/secrets/email/data-key}
{{- if .Values.mail.username }}
- {name: DAMGOX2_MAIL_PASSWORD_FILE, value: /run/secrets/email/smtp-password}
{{- end }}
{{- if .Values.mail.customCa }}
- {name: DAMGOX2_MAIL_CA_FILE, value: /run/secrets/email/ca.crt}
{{- end }}
{{- end }}
{{- end -}}
