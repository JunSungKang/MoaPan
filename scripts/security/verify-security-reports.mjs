import {verifyScanFreshness} from './scan-freshness.mjs';
import {documentedSourceBackport} from './security-applicability.mjs';
import {verifyJoseValidation} from './jose-validation.mjs';
import fs from 'node:fs';
import assert from 'node:assert/strict';
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
verifyJoseValidation(read('build/reports/security/jose-validation.json'));
const jar=read('build/reports/security/jar.json');
assert(jar.Results?.some(r=>r.Type==='jar'&&r.Packages?.length>0),'JAR analyzer did not recognize any packages; scan is incomplete');
const bom=read('build/reports/sbom.cdx.json');
assert(bom.components?.length>0,'Empty SBOM');
for(const f of ['source','jar','sbom','helm','helm-mail','tool']){
 const report=read(`build/reports/security/${f}.json`);
 verifyScanFreshness(report,read('.local/trivy/db/metadata.json'));
 assert(Array.isArray(report.Results),`Missing scanner results: ${f}`);
 if(f.startsWith('helm'))assert(report.Results.some(r=>r.Type==='kubernetes'&&r.Misconfigurations?.length),'Rendered Kubernetes resources were not analyzed');
 if(f==='tool')assert(report.Results.some(r=>r.Type==='gobinary'&&r.Packages?.length>0),'Scanner executable was not analyzed');
 assert.equal(report.Results.flatMap(r=>r.Vulnerabilities||[]).filter(v=>['HIGH','CRITICAL'].includes(v.Severity)&&!(f==='source'&&documentedSourceBackport(v))).length,0,`High risk findings: ${f}`);
 assert.equal(report.Results.flatMap(r=>r.Misconfigurations||[]).filter(v=>['HIGH','CRITICAL'].includes(v.Severity)&&v.Status==='FAIL').length,0,`High risk configuration: ${f}`);
 assert.equal(report.Results.flatMap(r=>r.Secrets||[]).length,0,`Secrets detected: ${f}`);
}
const metadata=read('.local/trivy/db/metadata.json');
const age=Date.now()-Date.parse(metadata.DownloadedAt);
assert(age>=0&&age<86400000,'Vulnerability DB synchronization is older than 24 hours');
console.log(`Recognized ${jar.Results.flatMap(r=>r.Packages||[]).length} JAR packages; SBOM ${bom.components.length} components; DB sync ${metadata.DownloadedAt}`);
