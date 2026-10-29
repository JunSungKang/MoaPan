#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
MOAPAN_ROOT=$(pwd -P)
export PATH="$MOAPAN_ROOT/.local/bin:$PATH"
[ "$#" -eq 1 ] || { echo 'Usage: sh scripts/backup/backup.sh <age-recipient-public-key>' >&2; exit 1; }
command -v age >/dev/null 2>&1 || { echo 'Verified age required' >&2; exit 1; }
umask 077
mkdir -p "$MOAPAN_ROOT/.local/backups"
stamp=$(date -u '+%Y%m%dT%H%M%SZ')
work=$(mktemp -d "$MOAPAN_ROOT/.local/backups/backup-$stamp.XXXXXX")
pipe="$work/dump.pipe"
out="$work/moapan.sql.age.partial"
mkfifo "$pipe"
trap 'rm -f "$pipe" "$out"' EXIT HUP INT TERM
docker compose exec -T db pg_dump -U moapan -d moapan --no-owner --no-acl > "$pipe" &
dump_pid=$!
if ! age -r "$1" -o "$out" < "$pipe"; then kill "$dump_pid" 2>/dev/null || true; wait "$dump_pid" 2>/dev/null || true; exit 1; fi
wait "$dump_pid"
mv "$out" "$work/moapan.sql.age"
printf 'Encrypted backup created: %s\n' "$work/moapan.sql.age"
