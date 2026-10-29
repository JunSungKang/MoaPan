#!/bin/sh
# Execute from any directory. Existing secrets/data are never replaced.
set -eu
cd "$(dirname "$0")/../.."
[ -f .env ] || { echo 'Prepare the private .env using .env.example first' >&2; exit 1; }
mode="${1:-local}"
case "$mode" in
  local) set -- -f compose.yaml ;;
  public) set -- -f compose.yaml -f scripts/deploy/compose-public.yaml ;;
  server) set -- -f compose.yaml -f scripts/deploy/compose-server.yaml ;;
  *) echo 'Usage: sh scripts/deploy/start.sh local|public|server' >&2; exit 1 ;;
esac
docker compose "$@" -f scripts/deploy/compose-translation.yaml -f scripts/deploy/compose-classification.yaml config --quiet
sh scripts/translation/translation-prepare.sh
sh scripts/classification/classification-prepare.sh
docker compose "$@" -f scripts/deploy/compose-translation.yaml -f scripts/deploy/compose-classification.yaml up -d --build
docker compose "$@" -f scripts/deploy/compose-translation.yaml -f scripts/deploy/compose-classification.yaml ps -a
