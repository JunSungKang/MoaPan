import fs from 'node:fs';
import assert from 'node:assert/strict';
import {test,mock} from 'node:test';
import {documentedSourceBackport} from './security-applicability.mjs';
const finding={VulnerabilityID:'CVE-2026-116587',PkgName:'github.com/go-jose/go-jose/v3',InstalledVersion:'v3.0.5'};
test('source backport proof is exact and fails closed for malformed or changed evidence',()=>{
 const read=fs.readFileSync.bind(fs),file='docs/05-test/evidence/jose-v3-backport.json',review=JSON.parse(read(file,'utf8'));
 assert.equal(documentedSourceBackport(finding),true);
 for(const changed of[{VulnerabilityID:'CVE-2099-1234'},{PkgName:'unrelated'},{InstalledVersion:'v3.0.4'}])assert.equal(documentedSourceBackport({...finding,...changed}),false);
 for(const changed of[{sourceSha256:{}},{expiresAt:'invalid'},{expiresAt:'2000-01-01T00:00:00Z'},{runtimeSourceChecksumsVerified:0}]){
  mock.method(fs,'readFileSync',(p,...args)=>p===file?JSON.stringify({...review,...changed}):read(p,...args));
  try{assert.equal(documentedSourceBackport(finding),false);}finally{mock.restoreAll();}
 }
 mock.method(fs,'readFileSync',(p,...args)=>p==='Dockerfile'?Buffer.from('synthetic changed recipe'):read(p,...args));
 try{assert.equal(documentedSourceBackport(finding),false);}finally{mock.restoreAll();}
});
