import fs from 'node:fs';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';

export const advisories = {
 'GHSA-mgcc-cww5-fp2q':'CVE-2026-116587',
 'GHSA-4w3g-jfj2-cw2q':'CVE-2026-116585',
 'GHSA-vvm8-gf78-28g4':'CVE-2026-116586'
};
export const sourcePaths = [
 'Dockerfile','docker-bake.hcl','scripts/platform/base.Dockerfile','scripts/platform/Caddyfile','scripts/platform/runtime-versions.json','scripts/platform/fetch-java.mjs','scripts/platform/prepare-java.mjs','scripts/platform/java-dist/README.txt','scripts/deploy/proxy/main.go','scripts/deploy/proxy/go.mod','scripts/deploy/proxy/go.sum',
 ...['README.md','cipher_security_test.go','jose-v3-security.patch','jose_security_test.go','json_security_test.go','modified.sha256','original.sha256','public_api_test.go'].map(p=>'scripts/deploy/proxy/patches/'+p),
 'scripts/security/jose-backport-check.sh','scripts/security/jose-validation.mjs','scripts/security/CollectModuleLicenses.go','scripts/security/CopyModule.go',
 'scripts/security/InspectGoBinary.go','scripts/test/ProxySecurityCheck.go'
];
const directory='build/reports/security/';
const negativeTests=['TestDamGoX2OversizedRSARejectedBeforeValidation','TestEmbeddedPrivateJWKRejected','TestGeneralSerializationUnprotectedHeader','TestJWERejectsShortGCMCEKForDeclaredContentEncryption','TestJWERejectsShortCBCHMACCEKForDeclaredContentEncryption'];
const packageNames=['github.com/go-jose/go-jose/v3','github.com/go-jose/go-jose/v3/cipher','github.com/go-jose/go-jose/v3/cryptosigner','github.com/go-jose/go-jose/v3/json','github.com/go-jose/go-jose/v3/jwt'];
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
export const proofFiles=['jose-patched.jsonl','jose-unpatched.jsonl','jose-runtime-checks.txt','jose-builder.id','proxy-boundaries.txt','caddy-functions.json','trivy-functions.json',...Object.keys(advisories).map(id=>id+'.json')];

export function verifyJoseValidation(proof,{read=fs.readFileSync,now=Date.now()}={}) {
 assert.equal(proof.schemaVersion,1);
 assert.equal(proof.status,'fixed');
 const age=now-Date.parse(proof.validatedAt);
 assert(age>=0&&age<86400000,'JOSE actual verification must be within 24 hours');
 for(const [key,paths] of [['sourceSha256',sourcePaths],['proofSha256',proofFiles.map(p=>directory+p)]]){
  assert.deepEqual(Object.keys(proof[key]||{}).sort(),[...paths].sort(),'Incomplete proof inventory');
  for(const p of paths)assert.equal(digest(read(p)),proof[key][p],`Changed JOSE verification input: ${p}`);
 }
 const events=name=>read(directory+name,'utf8').trim().split('\n').map(line=>JSON.parse(line));
 const positive=events('jose-patched.jsonl'),negative=events('jose-unpatched.jsonl');
 for(const event of [...positive,...negative]){
  const age=now-Date.parse(event.Time);
  assert(age>=0&&age<86400000&&Date.parse(event.Time)<=Date.parse(proof.validatedAt),'Stale or future test event');
 }
 assert(!positive.some(e=>e.Action==='fail'),'Patched regression failed');
 assert.deepEqual(positive.filter(e=>e.Action==='pass'&&!e.Test).map(e=>e.Package).sort(),[...packageNames].sort());
 for(const name of negativeTests)assert(positive.some(e=>e.Action==='pass'&&e.Test===name),`Missing patched test: ${name}`);
 assert.deepEqual(negative.filter(e=>e.Action==='fail'&&e.Test&&!e.Test.includes('/')).map(e=>e.Test).sort(),[...negativeTests].sort(),'Negative control must reproduce all five defects');
 const runtime=read(directory+'jose-runtime-checks.txt','utf8');
 assert.equal(runtime.split('\n').filter(line=>line.endsWith(': OK')).length,6);
 const binary=runtime.match(/^([a-f0-9]{64})\s+\/caddy$/m);
 assert(binary&&binary[1]===proof.binarySha256,'Binary fingerprint missing');
 assert(/^sha256:[a-f0-9]{64}$/.test(proof.builderImage));
 assert.equal(read(directory+'jose-builder.id','utf8').trim(),proof.builderImage);
 assert.match(read(directory+'proxy-boundaries.txt','utf8'),/^Proxy boundary checks PASS: 27;/);
 for(const name of ['caddy','trivy']){
  const functions=JSON.parse(read(directory+name+'-functions.json','utf8'));
  assert(functions.Functions>10000,'Incomplete executable function inventory');
  assert.deepEqual(functions.Matches['golang.org/x/crypto/openpgp'],[],'Unsafe OpenPGP code linked');
  assert.equal(functions.SHA256,name==='caddy'?proof.binarySha256:digest(read('.local/bin/trivy')),'Function inventory covers another executable');
 }
 for(const [ghsa,cve] of Object.entries(advisories)){
  const advisory=JSON.parse(read(directory+ghsa+'.json','utf8'));
  assert.equal(advisory.ghsa_id,ghsa);assert.equal(advisory.cve_id,cve);
  const scope=advisory.vulnerabilities?.find(v=>v.package?.name==='github.com/go-jose/go-jose/v3'&&v.package.ecosystem==='go');
  assert(scope,'Supplier no longer describes reviewed v3 scope; review required');
  assert(!scope.patched_versions,'Official v3 fix is available; replace the backport and review');
 }
 return proof;
}

if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
 const file=directory+'jose-validation.json';
 const hashes=paths=>Object.fromEntries(paths.map(p=>[p,digest(fs.readFileSync(p))]));
 if(process.argv[2]==='snapshot'){
  fs.writeFileSync(directory+'jose-inputs.json',JSON.stringify(hashes(sourcePaths))+'\n');
  process.exit(0);
 }
 if(process.argv[2]==='record'){
  const sourceSha256=JSON.parse(fs.readFileSync(directory+'jose-inputs.json','utf8'));
  assert.deepEqual(sourceSha256,hashes(sourcePaths),'Source changed during actual validation; rerun');
  const runtime=fs.readFileSync(directory+'jose-runtime-checks.txt','utf8');
  const proof={schemaVersion:1,status:'fixed',validatedAt:new Date().toISOString(),builderImage:fs.readFileSync(directory+'jose-builder.id','utf8').trim(),binarySha256:runtime.match(/^([a-f0-9]{64})\s+\/caddy$/m)?.[1],sourceSha256,proofSha256:hashes(proofFiles.map(p=>directory+p))};
  verifyJoseValidation(proof);fs.writeFileSync(file,JSON.stringify(proof,null,2)+'\n');
 }else verifyJoseValidation(JSON.parse(fs.readFileSync(file,'utf8')));
 console.log('JOSE source checks, five upstream packages, five unpatched failures and current supplier advisories verified.');
}
