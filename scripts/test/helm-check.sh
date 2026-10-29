#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
root=$(pwd -P)
helm=${HELM_BIN:-helm}
mkdir -p "$root/.local/tmp"
work=$(mktemp -d "$root/.local/tmp/helm-check.XXXXXX")
trap 'rm -r "$work"' EXIT HUP INT TERM
# Synthetic rendering references only; never installed or published.
digest=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
cat > "$work/values.yaml" <<EOF
publicHost: moapan.example.test
existingSecret: synthetic-test-secret
images:
  app: registry.example.test/app@sha256:$digest
  database: registry.example.test/db@sha256:$digest
  proxy: registry.example.test/proxy@sha256:$digest
  translator: registry.example.test/translator@sha256:$digest
  classifier: registry.example.test/classifier@sha256:$digest
ingress:
  className: nginx
  tlsSecret: synthetic-tls
EOF
chart=scripts/deploy/helm/moapan
"$helm" lint "$chart" --strict -f "$work/values.yaml"
"$helm" template moapan "$chart" -n moapan -f "$work/values.yaml" > "$work/rendered.yaml"
[ -s "$work/rendered.yaml" ]
if "$helm" template moapan "$chart" > "$work/rejected.log" 2>&1; then echo 'Missing values unexpectedly accepted'; exit 1; fi
if "$helm" template moapan "$chart" -f "$work/values.yaml" --set images.app=app:latest > "$work/rejected.log" 2>&1; then echo 'Unpinned image unexpectedly accepted'; exit 1; fi
if "$helm" template moapan "$chart" -f "$work/values.yaml" --set-string migrationVersion=invalid > "$work/rejected.log" 2>&1; then echo 'Invalid migration version unexpectedly accepted'; exit 1; fi
printf 'Helm lint/render and 3 invalid-input checks passed; no cluster resources created.\n'
