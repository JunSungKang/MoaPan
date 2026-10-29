#!/bin/sh
# Run from the repository; writes only workspace-owned secrets, never global paths.
set -eu
cd "$(dirname "$0")/../.."
root=$(pwd -P)
mkdir -p "$root/.local/translation"
chmod 0700 "$root/.local/translation"
if [ ! -f "$root/.local/translation/api.key" ]; then
  (umask 077; openssl rand -hex 32 > "$root/.local/translation/api.key")
fi
# Docker secrets must be readable by UID 10001; parent directory remains owner-only.
chmod 0644 "$root/.local/translation/api.key"
echo 'Workspace translation secret ready. Value is not printed.'
docker compose -f compose.yaml -f scripts/deploy/compose-translation.yaml build translator
# Start only the one-shot initializer. On failure, service is not enabled.
docker compose -f compose.yaml -f scripts/deploy/compose-translation.yaml run --rm model-init
