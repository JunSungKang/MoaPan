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
const latest=Math.max(...fs.readdirSync("src/main/resources/db/migration").map(name=>Number(name.match(/^V(\d+)__/)?.[1]||0)));
const configured=fs.readFileSync("scripts/deploy/helm/moapan/values.yaml","utf8").match(/^migrationVersion:\s*"(\d+)"/m)?.[1];
assert.equal(configured,String(latest),"Helm must wait for the latest Flyway schema before starting the application");
for(const component of ["web","worker"]){
 const deployment=docs.find(d=>d.includes("kind: Deployment")&&d.includes("name: "+component+"\n"));
 assert(deployment?.includes("WHERE version='"+latest+"' AND success"),component+" must wait for the latest schema");
}
console.log("Helm latest Flyway schema wait passed: V"+latest);
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
# External SMTP client render only. No mail-server workload is part of this chart.
cat >> "$work/values.yaml" <<EOF
mail:
  enabled: true
  host: smtp.example.test
  port: 587
  from: no-reply@example.test
  tls: STARTTLS
  username: synthetic-user
  existingSecret: synthetic-email
  customCa: true
EOF
"$helm" lint "$chart" --strict -f "$work/values.yaml"
"$helm" template moapan "$chart" -n moapan -f "$work/values.yaml" > "$work/smtp-rendered.yaml"
node --input-type=module - "$work/rendered.yaml" "$work/smtp-rendered.yaml" <<'JSEND'
import fs from 'node:fs';import assert from 'node:assert/strict';
const [baseline,mail]=process.argv.slice(2).map(p=>fs.readFileSync(p,'utf8'));
const documents=s=>s.split(/\n---/);
for(const kind of ['Deployment','Service','Job'])assert.equal(documents(mail).filter(d=>d.includes('kind: '+kind+'\n')).length,documents(baseline).filter(d=>d.includes('kind: '+kind+'\n')).length,'Mail configuration must not create a daemon or listener');
for(const component of ['web','worker']){const deployment=documents(mail).find(d=>d.includes('kind: Deployment')&&d.includes('name: '+component+'\n'));assert(deployment.includes('value: "smtp.example.test"'));assert(deployment.includes('value: "STARTTLS"'));assert(deployment.includes('MOAPAN_MAIL_DATA_KEY_FILE'));assert(deployment.includes('/run/secrets/email/ca.crt'));assert(deployment.includes('MOAPAN_MAIL_PASSWORD_FILE'));assert(deployment.includes('runAsUser: 10001'));}
assert(!/runAsUser:\s*0(?:\D|$)|containerPort:\s*(?:25|465|587)(?:\D|$)/.test(mail),'No root mail workload or SMTP listener');
assert(!mail.includes('path: smtp-key')&&!mail.includes('path: smtp-cert'),'No server TLS private key mount');
console.log('External SMTP Helm workload, nonroot, secret and TLS boundaries passed');
JSEND
if "$helm" template moapan "$chart" -f "$work/values.yaml" --set-string mail.host= > "$work/rejected.log" 2>&1; then echo 'Missing SMTP hostname unexpectedly accepted'; exit 1; fi
if "$helm" template moapan "$chart" -f "$work/values.yaml" --set-string mail.existingSecret= > "$work/rejected.log" 2>&1; then echo 'Missing email secret unexpectedly accepted'; exit 1; fi
if "$helm" template moapan "$chart" -f "$work/values.yaml" --set mail.tls=NONE > "$work/rejected.log" 2>&1; then echo 'Unverified SMTP mode unexpectedly accepted'; exit 1; fi
if "$helm" template moapan "$chart" -f "$work/values.yaml" --set mail.server.enabled=true > "$work/rejected.log" 2>&1; then echo 'Retired SMTP daemon option unexpectedly accepted'; exit 1; fi
"$helm" template moapan "$chart" -n moapan -f "$work/values.yaml" --set mail.tls=SMTPS --set mail.port=465 > "$work/smtps-rendered.yaml"
mkdir -p "$root/build/reports/security/helm-mail-render"
cp "$work/smtp-rendered.yaml" "$root/build/reports/security/helm-mail-render/manifests.yaml"
cp "$work/smtps-rendered.yaml" "$root/build/reports/security/helm-mail-render/smtps.yaml"
printf 'External SMTP Helm STARTTLS/SMTPS and 4 rejection checks passed; no cluster resources created.\n'
