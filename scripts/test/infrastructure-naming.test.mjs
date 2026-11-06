import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {volumePlan,volumeSuffixes,equalSnapshots,evidenceDestination} from '../deploy/infrastructure-migration.mjs';
const names=volumeSuffixes.map(s=>'moapan_'+s);
test('offline migration maps every volume exactly once without deletion',()=>{
 const plan=volumePlan(names,[]);assert.equal(new Set(plan.map(p=>p.target)).size,8);assert(plan.every(p=>p.target==='damgox2_'+p.suffix));
});
test('running consumers, missing source or existing destination fail closed',()=>{
 assert.throws(()=>volumePlan(names,[{State:{Running:true},Mounts:[{Type:'volume',Name:'moapan_database_v1'}]}]),/in use/);
 assert.throws(()=>volumePlan(names.slice(1),[]),/Missing/);
 assert.throws(()=>volumePlan([...names,'damgox2_database_v1'],[]),/already exists/);
});
test('stopped consumers can migrate and every changed table fingerprint blocks cleanup',()=>{
 assert.equal(volumePlan(names,[{State:{Running:false},Mounts:[{Type:'volume',Name:'moapan_database_v1'}]}]).length,8);
 const a=[{table:'users',count:2,sha256:'original'}];assert(equalSnapshots(a,structuredClone(a)));
 assert.throws(()=>equalSnapshots(a,[{table:'users',count:2,sha256:'changed'}]),/mismatch/);
 assert.throws(()=>equalSnapshots(a,[]),/mismatch/);
});
test('canonical deployment names and runtime mail/role bindings stay aligned',()=>{
 const walk=d=>fs.readdirSync(d,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(d+'/'+e.name):[d+'/'+e.name]);
 const files=['compose.yaml','Dockerfile',...walk('scripts/deploy/helm/damgox2'),...walk('scripts/translation'),...walk('scripts/classification'),...walk('scripts/deploy').filter(f=>f.endsWith('.yaml')&&!f.includes('/helm/'))];
 for(const f of files)assert(!/moapan/i.test(fs.readFileSync(f,'utf8')),f);
 const properties=fs.readFileSync('src/main/resources/application.properties','utf8');
 for(const key of ['ENABLED','HOST','PORT','FROM','USERNAME','PASSWORD_FILE','TLS','CA_FILE','DATA_KEY_FILE'])assert(properties.includes('DAMGOX2_MAIL_'+key));
 assert(fs.readFileSync('src/main/java/com/moapan/MoapanApplication.java','utf8').includes('DAMGOX2_ROLE'));
});
test('evidence resolves parents before copy and rejects symlink escapes',()=>{
 const root=fs.realpathSync('.'),work=fs.mkdtempSync(path.join(root,'.local/tmp/infrastructure-path-'));
 try{
  assert.equal(evidenceDestination(root,path.relative(root,work+'/evidence.json')),work+'/evidence.json');
  // Stay within the authorized workspace while simulating escape from .local.
  fs.symlinkSync(root,work+'/escape','dir');
  assert.throws(()=>evidenceDestination(root,path.relative(root,work+'/escape/evidence.json')),/escapes/);
  assert.throws(()=>evidenceDestination(root,'../evidence.json'),/inside/);
  fs.writeFileSync(work+'/existing.json','{}');
  assert.throws(()=>evidenceDestination(root,path.relative(root,work+'/existing.json')),/already exists/);
 }finally{fs.rmSync(work,{recursive:true});}
});
