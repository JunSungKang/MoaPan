#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
MOAPAN_ROOT=$(pwd -P)
export PATH="$MOAPAN_ROOT/.local/bin:$PATH"
[ "$#" -eq 2 ] || { echo 'Usage: sh scripts/backup/restore-check.sh <encrypted-backup> <private-identity-file>' >&2; exit 1; }
command -v age >/dev/null 2>&1 || { echo 'Verified age required' >&2; exit 1; }
[ ! -L "$1" ] && [ ! -L "$2" ] || { echo 'Symlinks not accepted' >&2; exit 1; }
backup=$(cd "$(dirname "$1")" && pwd -P)/$(basename "$1")
identity=$(cd "$(dirname "$2")" && pwd -P)/$(basename "$2")
case "$backup" in "$MOAPAN_ROOT"/.local/backups/*) ;; *) echo 'Backup must be inside workspace .local/backups' >&2; exit 1;; esac
case "$identity" in "$MOAPAN_ROOT"/.local/backups/*) ;; *) echo 'Test identity must be inside workspace .local/backups' >&2; exit 1;; esac
umask 077
stamp=$(date -u '+%Y%m%d%H%M%S')
restore_db="moapan_restore_$stamp"
work=$(mktemp -d "$MOAPAN_ROOT/.local/backups/restore-$stamp.XXXXXX")
pipe="$work/restore.pipe"
errors="$work/restore.err"
created=false
cleanup(){ rm -f "$pipe" "$errors"; rmdir "$work"; if [ "$created" = true ]; then docker compose exec -T db dropdb -U moapan_migrate "$restore_db" >/dev/null; fi; }
trap cleanup EXIT HUP INT TERM
docker compose exec -T db createdb -U moapan_migrate "$restore_db"
created=true
mkfifo "$pipe"
age --decrypt -i "$identity" "$backup" > "$pipe" 2> "$errors" &
decrypt_pid=$!
if ! docker compose exec -T db psql -U moapan_migrate -d "$restore_db" --set ON_ERROR_STOP=1 < "$pipe" >/dev/null 2> "$errors"; then kill "$decrypt_pid" 2>/dev/null || true; wait "$decrypt_pid" 2>/dev/null || true; echo 'Restore failed; isolated DB removed, sensitive diagnostics withheld' >&2; exit 1; fi
wait "$decrypt_pid"
# Restoration must not silently reactivate deleted accounts or old sessions.
printf '%s\n' 'UPDATE users SET active=false;' 'TRUNCATE spring_session CASCADE;' | docker compose exec -T db psql -U moapan_migrate -d "$restore_db" --set ON_ERROR_STOP=1 >/dev/null 2> "$errors"
docker compose exec -T db psql -U moapan_migrate -d "$restore_db" -At --set ON_ERROR_STOP=1 -c "SELECT 'articles='||count(*) FROM articles; SELECT 'active_accounts='||count(*) FROM users WHERE active; SELECT 'sessions='||count(*) FROM spring_session;"
printf 'Encrypted backup decrypt and isolated restore verified; isolated database will be removed.\n'
