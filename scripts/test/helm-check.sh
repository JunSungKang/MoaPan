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
node --input-type=module - "$work/rendered.yaml" <<'JSEND'
import fs from "node:fs";import assert from "node:assert/strict";
const docs=fs.readFileSync(process.argv[2],"utf8").split(/\n---/);
for(const model of ["translator","classifier"]){
 const deployment=docs.find(d=>d.includes("kind: Deployment")&&d.includes("name: "+model+"\n"));
 const download=docs.find(d=>d.includes("kind: Job")&&d.includes(model+"-download-"));
 const egress=docs.find(d=>d.includes("kind: NetworkPolicy")&&d.includes(model+"-egress-deny"));
 assert(deployment&&download&&egress);
 assert(deployment.includes("model-volume: "+model));assert(download.includes("model-volume: "+model));
 assert(deployment.includes("podAffinity:")&&download.includes("podAffinity:"));
 assert(deployment.includes("selector: {matchLabels: {app: moapan, component: "+model+"}}"));
 assert(deployment.includes("wait-for-verified-model")&&!deployment.includes("download-verified-huggingface-model"));
 assert(egress.includes("policyTypes: [Egress]")&&egress.includes("egress: []"));
 assert(!download.includes("secretName:")&&!download.includes("containerPort:"));
}
console.log("Helm model runtime/download/egress/affinity boundaries passed");
JSEND
if "$helm" template moapan "$chart" > "$work/rejected.log" 2>&1; then echo 'Missing values unexpectedly accepted'; exit 1; fi
if "$helm" template moapan "$chart" -f "$work/values.yaml" --set images.app=app:latest > "$work/rejected.log" 2>&1; then echo 'Unpinned image unexpectedly accepted'; exit 1; fi
if "$helm" template moapan "$chart" -f "$work/values.yaml" --set-string migrationVersion=invalid > "$work/rejected.log" 2>&1; then echo 'Invalid migration version unexpectedly accepted'; exit 1; fi
printf 'Helm lint/render and 3 invalid-input checks passed; no cluster resources created.\n'
if [ -n "${HELM_RENDER_OUTPUT:-}" ]; then
  node --input-type=module - "$root" "$HELM_RENDER_OUTPUT" "$work/rendered.yaml" <<'JS'
import fs from 'node:fs';import path from 'node:path';
const [root,destination,source]=process.argv.slice(2),absolute=path.resolve(root,destination);
if(!absolute.startsWith(root+'/build/'))throw new Error('Helm scan output must be under workspace build/');
fs.mkdirSync(path.dirname(absolute),{recursive:true});fs.copyFileSync(source,absolute);
JS
fi
