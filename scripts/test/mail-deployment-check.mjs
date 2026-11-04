import assert from 'node:assert/strict';
import {execFileSync,spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const root=fileURLToPath(new URL('../../',import.meta.url));
const environment={...process.env,MOAPAN_PUBLIC_HOST:'192.0.2.10',PUBLIC_BASE_URL:'https://192.0.2.10',COOKIE_SECURE:'true',MAIL_FROM:'no-reply@example.test',MAIL_SMTP_HOST:'smtp.example.test',MAIL_SMTP_PORT:'587',MAIL_SMTP_NETWORK:'synthetic-smtp-network',MAIL_SMTP_CA_FILE:'./build/synthetic-smtp-ca.crt'};
const execute=(args,env=environment)=>JSON.parse(execFileSync('docker',args,{cwd:root,env,encoding:'utf8',stdio:['ignore','pipe','pipe']}));
for(const mode of ['local','public','server']){
  const files=['compose.yaml'];if(mode!=='local')files.push('scripts/deploy/compose-'+mode+'.yaml');files.push('scripts/deploy/compose-translation.yaml','scripts/deploy/compose-classification.yaml');
  const args=paths=>['compose','--env-file','.env.example',...paths.flatMap(p=>['-f',p]),'config','--format','json'];
  const baseline=execute(args(files)),configured=execute(args([...files,'scripts/deploy/compose-mail-external.yaml']));
  assert.deepEqual(Object.keys(configured.services).sort(),Object.keys(baseline.services).sort(),'SMTP integration must not create a daemon');
  assert.deepEqual(configured.volumes,baseline.volumes,'Existing data volumes must be preserved');
  assert.deepEqual(configured.services.proxy.ports,baseline.services.proxy.ports,'Existing ingress must be preserved');
  for(const [name,service] of Object.entries(configured.services)){
    assert.deepEqual(service.ports,baseline.services[name].ports,'No SMTP listener may be published');
    assert.deepEqual(service.user,baseline.services[name].user);assert.deepEqual(service.cap_add,baseline.services[name].cap_add);
    if(name!=='worker')assert.deepEqual(service.networks,baseline.services[name].networks,'Only worker may join SMTP network');
  }
  for(const name of Object.keys(baseline.services.worker.networks))assert(name in configured.services.worker.networks,'Preserve model/backend networks');
  assert.equal(configured.networks['external-smtp'].external,true);assert.equal(configured.networks['external-smtp'].name,'synthetic-smtp-network');
  for(const name of ['web','worker']){
    const service=configured.services[name];assert.equal(service.environment.MOAPAN_MAIL_TLS,'STARTTLS');assert.equal(service.environment.MOAPAN_MAIL_HOST,'smtp.example.test');assert.equal(service.environment.MOAPAN_MAIL_ENABLED,'true');assert.equal(service.read_only,true);assert.deepEqual(service.cap_drop,['ALL']);assert(service.secrets.some(v=>v.source==='email-data-key'));assert(service.secrets.some(v=>v.source==='external-smtp-ca'));assert(!service.environment.MOAPAN_MAIL_DATA_KEY);
  }
  for(const service of Object.values(configured.services))assert(!service.secrets?.some(s=>/smtp-key|smtp-cert/.test(s.source)),'Server TLS private keys must stay outside the application');
  for(const field of ['MAIL_SMTP_HOST','MAIL_SMTP_NETWORK','MAIL_SMTP_CA_FILE','MAIL_FROM']){const missing={...environment};delete missing[field];assert.notEqual(spawnSync('docker',args([...files,'scripts/deploy/compose-mail-external.yaml']),{cwd:root,env:missing,stdio:'pipe'}).status,0,'Missing SMTP configuration must fail closed: '+field);}
}
console.log('External SMTP Compose local/public/server boundaries and 12 missing-configuration rejections passed; no containers changed.');
