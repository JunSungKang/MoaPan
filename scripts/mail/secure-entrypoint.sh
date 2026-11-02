#!/bin/sh
set -eu
# Changes are in this derivative container only; original MailBox files remain untouched.
[ -r /run/secrets/smtp-cert ] && [ -r /run/secrets/smtp-key ] || exit 1
cp -a /config/postfix/. /etc/postfix/
cp -a /config/spool/. /var/spool/postfix/
install -d -m 0755 -o postfix -g postfix /var/lib/postfix
# SMTP daemons keep the package chroot policy. Stage its TLS files in a dedicated tmpfs inside the jail.
install -d -m 0750 -o root -g postfix /var/spool/postfix/run/secrets
install -m 0444 /run/secrets/smtp-cert /var/spool/postfix/run/secrets/smtp-cert
install -m 0640 -o root -g postfix /run/secrets/smtp-key /var/spool/postfix/run/secrets/smtp-key
install -d -m 0755 /var/spool/postfix/etc/ssl/certs
install -m 0644 /etc/ssl/certs/ca-certificates.crt /var/spool/postfix/etc/ssl/certs/ca-certificates.crt
for config in resolv.conf hosts services nsswitch.conf; do
 [ ! -r "/etc/$config" ] || install -m 0644 "/etc/$config" "/var/spool/postfix/etc/$config"
done
export MYNETWORKS="${MYNETWORKS:-127.0.0.0/8 172.31.252.0/28}"
postconf -e "virtual_minimum_uid=$(id -u vmail)" "virtual_uid_maps=static:$(id -u vmail)" "virtual_gid_maps=static:$(getent group vmail | cut -d : -f 3)"
postconf -e 'default_database_type=lmdb' 'virtual_mailbox_maps=lmdb:/etc/postfix/maps/virtual-mailbox-accounts'
postconf -e 'smtpd_tls_security_level=encrypt' 'smtpd_tls_cert_file=/run/secrets/smtp-cert' 'smtpd_tls_key_file=/run/secrets/smtp-key'
postconf -e 'smtpd_tls_protocols=>=TLSv1.2' 'smtpd_tls_mandatory_protocols=>=TLSv1.2' 'smtpd_tls_mandatory_ciphers=high'
postconf -e 'smtp_tls_security_level=verify' 'smtp_tls_CAfile=/etc/ssl/certs/ca-certificates.crt' 'smtp_tls_verify_cert_match=hostname' 'smtp_tls_protocols=>=TLSv1.2'
postconf -e 'maillog_file_prefixes=/dev/null /var /dev/stdout' 'maillog_file=/dev/null' 'message_size_limit=32768' 'maximal_queue_lifetime=5m' 'bounce_queue_lifetime=5m'
postconf -e 'smtpd_relay_restrictions=permit_mynetworks,reject' 'smtpd_recipient_restrictions=permit_mynetworks,reject'
postconf -e 'smtpd_client_connection_count_limit=5' 'smtpd_client_message_rate_limit=30' 'smtpd_recipient_limit=1'
exec /usr/local/bin/mailbox-smtp-receiver-entrypoint
