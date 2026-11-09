#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
DAMGOX2_ROOT=$(pwd -P)
export TMPDIR="$DAMGOX2_ROOT/.local/tmp"
export TRIVY_DISABLE_TELEMETRY=true
mkdir -p "$TMPDIR"
mkdir -p "$DAMGOX2_ROOT/build/reports/security" "$DAMGOX2_ROOT/.local/trivy"
export PATH="$DAMGOX2_ROOT/.local/bin:$PATH"
command -v trivy >/dev/null 2>&1 || { echo 'BLOCKED: verified Trivy executable is required; scan not performed' >&2; exit 1; }
trivy --version > build/reports/security/tool-version.txt
sh scripts/security/jose-backport-check.sh
tool_work=$(mktemp -d "$TMPDIR/scanner-self.XXXXXX")
trap 'rm -rf "$tool_work"' EXIT HUP INT TERM
cp "$DAMGOX2_ROOT/.local/bin/trivy" "$tool_work/trivy"
trivy --cache-dir "$DAMGOX2_ROOT/.local/trivy" rootfs --list-all-pkgs --scanners vuln,secret --format json --output build/reports/security/tool.json "$tool_work"
if ! command -v "${HELM_BIN:-helm}" >/dev/null 2>&1 && [ -x "$DAMGOX2_ROOT/.local/helm/darwin-arm64/helm" ]; then export HELM_BIN="$DAMGOX2_ROOT/.local/helm/darwin-arm64/helm"; fi
HELM_RENDER_OUTPUT=build/reports/security/helm-render/manifests.yaml sh scripts/test/helm-check.sh
trivy --cache-dir "$DAMGOX2_ROOT/.local/trivy" config --format json --output build/reports/security/helm.json build/reports/security/helm-render
trivy --cache-dir "$DAMGOX2_ROOT/.local/trivy" config --format json --output build/reports/security/helm-mail.json build/reports/security/helm-mail-render
trivy --cache-dir "$DAMGOX2_ROOT/.local/trivy" fs --skip-dirs .local --skip-dirs .git --skip-dirs build --skip-dirs scripts/deploy/helm/damgox2 --skip-files .env --include-dev-deps --scanners vuln,secret,misconfig --exit-code 0 --severity UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL --format json --output build/reports/security/source.json .
trivy --cache-dir "$DAMGOX2_ROOT/.local/trivy" sbom --exit-code 0 --severity UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL --format json --output build/reports/security/sbom.json build/reports/sbom.cdx.json
trivy --cache-dir "$DAMGOX2_ROOT/.local/trivy" rootfs --list-all-pkgs --scanners vuln --exit-code 0 --severity UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL --format json --output build/reports/security/jar.json build/libs
date -u '+%Y-%m-%dT%H:%M:%SZ' > build/reports/security/completed-at.txt

node scripts/security/verify-security-reports.mjs
