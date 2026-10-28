#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
MOAPAN_ROOT=$(pwd -P)
export TMPDIR="$MOAPAN_ROOT/.local/tmp"
export TRIVY_DISABLE_TELEMETRY=true
mkdir -p "$TMPDIR"
export PATH="$MOAPAN_ROOT/.local/bin:$PATH"
export DOCKER_HOST="${DOCKER_HOST:-$(docker context inspect --format '{{.Endpoints.docker.Host}}')}"
command -v trivy >/dev/null 2>&1 || { echo 'Verified Trivy required' >&2; exit 1; }
mkdir -p build/reports/security .local/trivy
for image in "$@"; do
  digest=$(docker image inspect --format '{{.Id}}' "$image")
  trivy --cache-dir "$MOAPAN_ROOT/.local/trivy" image --scanners vuln,secret --list-all-pkgs --severity UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL --exit-code 0 --format json --output "build/reports/security/image-${digest#sha256:}.json" "$image"
  node scripts/verify-image-report.mjs "build/reports/security/image-${digest#sha256:}.json" "$digest"
done
