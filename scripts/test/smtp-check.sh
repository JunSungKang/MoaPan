#!/bin/sh
# Explicit local fixture. No real mailbox, public port, live DB or existing service changes.
set -eu
cd "$(dirname "$0")/../.."
root=$(pwd -P)
export TMPDIR="$root/.local/tmp"
export BUILDX_CONFIG="${BUILDX_CONFIG:-$root/.local/buildx-smtp-test}"
case "$BUILDX_CONFIG" in "$root"/*) ;; *) echo 'Buildx output must stay in the workspace' >&2; exit 1;; esac
mkdir -p "$TMPDIR" "$BUILDX_CONFIG"
project="damgox2-smtp-test-$$"
export MAIL_FROM=no-reply@example.test MAIL_HOSTNAME=mailbox-smtp
set -- --env-file .env.example -p "$project" -f compose.yaml -f scripts/deploy/compose-mail.yaml -f scripts/test/compose-mail-smoke.yaml
cleanup(){ docker compose "$@" down --volumes --remove-orphans >/dev/null 2>&1; }
trap 'status=$?; if [ "$status" -ne 0 ]; then docker compose "$@" logs --tail 15 mailbox-smtp; fi; cleanup "$@"; exit "$status"' EXIT HUP INT TERM
sh scripts/mail/prepare.sh
docker build -t damgox2-mailbox-smtp:1.0.0 .local/mail/source
docker compose "$@" up -d --no-deps --no-build --wait --wait-timeout 60 mailbox-smtp
sh scripts/build/gradle-local.sh --no-daemon smtpSmoke
# SMTP acceptance is separate from delivery into this local synthetic Maildir.
count=0; attempt=0
while [ "$count" -lt 1 ] && [ "$attempt" -lt 10 ]; do
 count=$(docker compose "$@" exec -T mailbox-smtp sh -c 'find /var/mail/vhosts/mailbox/Maildir/new -type f | wc -l')
 attempt=$((attempt+1)); [ "$count" -ge 1 ] || sleep 0.5
done
[ "$count" -ge 1 ] || { echo 'SMTP accepted but local fixture was not delivered' >&2; exit 1; }
docker compose "$@" exec -T mailbox-smtp postconf mail_version smtp_tls_security_level smtp_tls_verify_cert_match smtpd_tls_security_level
printf 'Verified TLS SMTP and synthetic local mailbox delivery passed; no external recipient used.\n'
