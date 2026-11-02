#!/bin/sh
# All writes stay in this repository. The MailBox source is read-only input.
set -eu
cd "$(dirname "$0")/../.."
root=$(pwd -P)
source_dir=${MAILBOX_SOURCE_PATH:-"$root/scripts/mail/upstream"}
source_dir=$(cd "$source_dir" && pwd -P)
work="$root/.local/mail"; mkdir -p "$work/source" "$work/tls"
chmod 700 "$work" "$work/tls"
for file in main.cf entrypoint.sh; do [ -f "$source_dir/$file" ] || { echo 'MailBox SMTP source unavailable' >&2; exit 1; }; done
cp "$source_dir/main.cf" "$work/source/main.cf"
cp "$source_dir/entrypoint.sh" "$work/source/upstream-entrypoint.sh"
cp scripts/mail/Dockerfile scripts/mail/secure-entrypoint.sh scripts/security/patch-os-trust.sh "$work/source/"
# Synthetic local sink only; never import existing recipients or run the Webmail/IMAP stack.
printf '%s\n' 'probe@example.test mailbox/Maildir/' > "$work/source/virtual-mailbox-accounts"
(cd "$work/source" && shasum -a 256 main.cf upstream-entrypoint.sh > source.sha256)
umask 077
if [ ! -f "$work/data.key" ]; then openssl rand -base64 32 > "$work/data.key"; chmod 444 "$work/data.key"; fi
if [ ! -f "$work/tls/server.crt" ]; then
 if [ ! -f "$work/tls/ca.crt" ]; then
 openssl req -x509 -newkey rsa:3072 -nodes -keyout "$work/tls/ca.key" -out "$work/tls/ca.crt" -days 365 -subj /CN=DamGoX2-SMTP-Local-CA >/dev/null 2>&1
 fi
 openssl req -newkey rsa:2048 -nodes -keyout "$work/tls/server.key" -out "$work/tls/server.csr" -subj /CN=mailbox-smtp >/dev/null 2>&1
 printf '%s\n' 'subjectAltName=DNS:mailbox-smtp,DNS:localhost' 'extendedKeyUsage=serverAuth' 'basicConstraints=CA:FALSE' > "$work/tls/server.ext"
 openssl x509 -req -in "$work/tls/server.csr" -CA "$work/tls/ca.crt" -CAkey "$work/tls/ca.key" -CAcreateserial -out "$work/tls/server.crt" -days 90 -extfile "$work/tls/server.ext" >/dev/null 2>&1
 chmod 444 "$work/tls/ca.crt" "$work/tls/server.crt"
fi
openssl verify -CAfile "$work/tls/ca.crt" "$work/tls/server.crt" >/dev/null
# JavaMail performs full CA/hostname validation on every connection; this also catches bad preparation.
openssl x509 -noout -text -in "$work/tls/server.crt" | grep -Eq 'DNS:mailbox-smtp([,[:space:]]|$)'
openssl x509 -checkend 86400 -noout -in "$work/tls/server.crt" >/dev/null || { echo 'Renew SMTP server certificate before starting' >&2; exit 1; }
echo 'MailBox SMTP build context, private key and CA prepared inside .local/mail; external delivery is not verified.'
