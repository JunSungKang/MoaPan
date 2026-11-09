import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {databaseSnapshot} from '../deploy/infrastructure-migration.mjs';
import {modelCheck} from './model-check.mjs';
import {restoreCheck} from './restore-check.mjs';
import {ReviewedPolicyChange} from './recovery.mjs';

const root=fs.realpathSync('.'),base=root+'/.local/platform-ubuntu25-20261109';
const lock='/Users/kkkk/Desktop/jskang/03.workspace/01.project/operations/run/deploy.lock';
const descriptor=Number(process.env.DAMGOX2_DEPLOY_LOCK_FD);
assert(Number.isInteger(descriptor)&&descriptor>=3,'Run through native deploy.sh lock holder');
const held=fs.fstatSync(descriptor),named=fs.statSync(lock);assert(held.isFile()&&held.dev===named.dev&&held.ino===named.ino,'Inherited deployment lock mismatch');
assert(base.startsWith(root+'/'));assert.equal(fs.realpathSync(base),base);
const directory=fs.mkdtempSync(base+'/delivery-');fs.chmodSync(directory,0o700);
const evidence={startedAt:new Date().toISOString(),directory:path.relative(root,directory),lock,lockHeld:true,status:'INCOMPLETE'};
let interrupted=false,rollingBack=false;const cancellation=new AbortController();
for(const signal of ['SIGINT','SIGTERM','SIGHUP'])process.on(signal,()=>{interrupted=true;cancellation.abort();});
function command(binary,args,options={}){if(interrupted&&!rollingBack)throw Error('Interrupted deployment; rollback required');try{return execFileSync(binary,args,{encoding:'utf8',stdio:['pipe','pipe','pipe'],maxBuffer:67108864,...options}).trim();}catch{throw Error(binary+' '+args[0]+' failed; diagnostics withheld to protect credentials/data');}}
const docker=(args,options)=>command('docker',args,options);
const inspect=ids=>JSON.parse(docker(['inspect',...ids]));
const state=()=>{const ids=docker(['ps','-aq']).split('\n').filter(Boolean);return ids.length?inspect(ids):[];};
const safe=c=>({id:c.Id,name:c.Name.slice(1),image:c.Image,startedAt:c.State.StartedAt,running:c.State.Running,ports:c.HostConfig.PortBindings,mounts:c.Mounts.map(m=>({type:m.Type,source:m.Source,destination:m.Destination,name:m.Name,rw:m.RW})),networks:Object.keys(c.NetworkSettings.Networks)});
const write=(name,value)=>fs.writeFileSync(directory+'/'+name,JSON.stringify(value,null,2)+'\n',{mode:0o600});
const envOf=c=>Object.fromEntries(c.Config.Env.map(x=>{const i=x.indexOf('=');return [x.slice(0,i),x.slice(i+1)];}));
const composeArgs=['compose','--env-file',root+'/.env','--env-file',base+'/operator.env','-f',root+'/compose.platform.yaml'];
const configuration=JSON.parse(docker([...composeArgs,'config','--format','json']));
const app=configuration.services['damgox2-web'],worker=configuration.services['damgox2-worker'];
for(const [port,min,max] of [[app.ports[0].published,20100,20104],[configuration.services['damgox2-proxy'].ports[2].published,20000,20004]])assert(Number(port)>=min&&Number(port)<=max,'Reserved port range');
const target={app:JSON.parse(docker(['image','inspect',app.image]))[0],proxy:JSON.parse(docker(['image','inspect',configuration.services['damgox2-proxy'].image]))[0],ai:JSON.parse(docker(['image','inspect',configuration.services['damgox2-translator'].image]))[0]};
for(const info of Object.values(target)) {
  assert.equal(info.Architecture,'arm64');
  command('node',['scripts/security/verify-image-report.mjs','build/reports/security/image-'+info.Id.slice(7)+'.json',info.Id]);
}
evidence.images=Object.fromEntries(Object.entries(target).map(([key,v])=>[key,v.Id]));
evidence.context=docker(['context','show']);assert.equal(evidence.context,'desktop-linux');assert.equal(docker(['info','--format','{{.Architecture}}']),'aarch64');
const all=state(),oldNames=['damgox2-proxy-1','damgox2-worker-1','damgox2-web-1','damgox2-translator-1','damgox2-classifier-1'];
const old=oldNames.map(name=>{const c=all.find(c=>c.Name==='/'+name);assert(c?.State.Running,'Expected prior running service: '+name);return c;});
const db=all.find(c=>c.Name==='/damgox2-db-1');assert(db?.State.Running);assert.equal(worker.environment.DAMGOX2_DATABASE_HOST,'damgox2-db-1','This delivery preserves legacy PG, common-PG switch is separate');
assert.equal(worker.environment.DAMGOX2_DATABASE_USER,'damgox2');
assert.equal(fs.readFileSync(configuration.secrets['damgox2-database-password'].file,'utf8'),envOf(old[1]).DATABASE_PASSWORD,'Preserve existing DB credential');
const other=all.filter(c=>c.State.Running&&!oldNames.includes(c.Name.slice(1))&&c.Id!==db.Id&&!/check|validation/.test(c.Config.Labels?.['com.docker.compose.project']||''));
write('previous-containers.json',{old:old.map(safe),database:safe(db),other:other.map(safe)});
const previousJar=directory+'/previous.jar';docker(['cp',old[2].Id+':/app/damgox2.jar',previousJar]);
const legacy=JSON.parse(fs.readFileSync(root+'/src/main/resources/ai/java27-policy-transition.json'));
for(const c of legacy.classes) {
  const bytes=execFileSync('unzip',['-p',previousJar,'BOOT-INF/classes/'+c.name]);
  assert.equal(createHash('sha256').update(bytes).digest('hex'),c.sha256,'Prior AI class differs from reviewed transition');
}
evidence.oldPolicyClassCount=legacy.classes.length;assert.equal(legacy.classes.length,14);
const pinned={services:Object.fromEntries(['web','worker','proxy','translator','classifier','migrate'].map(role=>['damgox2-'+role,{image:target[['web','worker','migrate'].includes(role)?'app':role==='proxy'?'proxy':'ai'].Id}]))};
write('images.json',pinned);const args=[...composeArgs,'-f',directory+'/images.json'];
const services=['damgox2-translator','damgox2-classifier','damgox2-web','damgox2-proxy','damgox2-worker'];
const sql=q=>docker(['exec','-i',db.Id,'psql','-X','-U','damgox2','-d','damgox2','-At','--set','ON_ERROR_STOP=1'],{input:q});
const wait=async(fn,label)=>{for(let i=0;i<180;i++){try{if(fn())return;}catch{}await new Promise(r=>setTimeout(r,1000));}throw Error(label+' readiness timeout');};
const runtime=(role,extra={})=>{
  const env={...process.env,...worker.environment,DAMGOX2_ROLE:role,...extra};
  return docker(['run','--rm','--read-only','--network',configuration.networks['damgox2-database'].name,'--tmpfs','/tmp:size=128m,mode=1777','--memory','512m','--cap-drop','ALL','--security-opt','no-new-privileges:true','--mount','type=bind,src='+configuration.secrets['damgox2-database-password'].file+',dst=/run/secrets/database-password,readonly',...['DAMGOX2_DATABASE_HOST','DAMGOX2_DATABASE_PORT','DAMGOX2_DATABASE_NAME','DAMGOX2_DATABASE_USER','DAMGOX2_DATABASE_PASSWORD_FILE','DAMGOX2_ROLE','DAMGOX2_POLICY_TRANSITION'].filter(k=>env[k]).flatMap(k=>['-e',k]),target.app.Id],{env});
};
let stopped=false,started=false;const policyChange=new ReviewedPolicyChange();
try {
  // Stop only exact verified project IDs; keep old containers/mounts for rollback. PG never stops.
  stopped=true;for(const c of old)docker(['stop','--timeout','45',c.Id]);
  const before=databaseSnapshot(db.Id,'damgox2','damgox2');write('database-before.json',before);
  const recipient=command(root+'/.local/bin/age-keygen',['-y',root+'/.local/backups/keys/delivery-security-20261108.identity']);
  const backupLog=command('sh',['scripts/backup/backup.sh',recipient]);
  const backup=backupLog.replace('Encrypted backup created: ','');assert(backup.startsWith(root+'/.local/backups/'));assert(fs.statSync(backup).size>0);
  evidence.backup={file:path.relative(root,backup),sha256:createHash('sha256').update(fs.readFileSync(backup)).digest('hex')};
  evidence.backupRestore=await restoreCheck({root,image:db.Image,backup,expected:before,signal:cancellation.signal});
  docker([...args,'--profile','tools','run','--rm','--no-deps','-T','damgox2-migrate']);
  assert.equal(sql('SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1'),'28');
  evidence.models=await modelCheck({image:target.ai.Id,configuration,directory});
  const smtpEnv={...process.env,...worker.environment};
  evidence.smtp=command('docker',['run','--rm','--read-only','--network',configuration.networks['damgox2-mail'].name,'--cap-drop','ALL','--security-opt','no-new-privileges:true','--memory','192m','--mount','type=bind,src='+base+'/probes,dst=/probes,readonly',...['damgox2-smtp-ca','damgox2-smtp-password'].flatMap(key=>['--mount','type=bind,src='+configuration.secrets[key].file+',dst=/run/secrets/'+(key.endsWith('-ca')?'external-smtp-ca':'external-smtp-password')+',readonly']),...['DAMGOX2_MAIL_HOST','DAMGOX2_MAIL_PORT','DAMGOX2_MAIL_USERNAME','DAMGOX2_MAIL_CA_FILE','DAMGOX2_MAIL_PASSWORD_FILE'].flatMap(k=>['-e',k]),'--entrypoint','java',target.app.Id,'-cp','/probes','SmtpConnectionCheck'],{env:smtpEnv});
  evidence.policy=JSON.parse(runtime('policy-info'));assert.equal(evidence.policy.java,25);assert.equal(Object.keys(evidence.policy.classes).length,14);
  evidence.transition=policyChange.apply(()=>JSON.parse(runtime('policy-transition',{DAMGOX2_POLICY_TRANSITION:'apply-reviewed-java25'}).split('\n').at(-1)));
  const after=databaseSnapshot(db.Id,'damgox2','damgox2');write('database-after-policy.json',after);
  const mutable=new Set(['article_translations','article_translation_jobs','article_classification_jobs','translation_runtime','classification_runtime','read_cache_fingerprints','flyway_schema_history']);
  assert.deepEqual(before.filter(t=>!mutable.has(t.table)),after.filter(t=>!mutable.has(t.table)),'Non-policy tables changed during frozen transition');
  evidence.unchangedFrozenTables=before.filter(t=>!mutable.has(t.table)).length;
  started=true;
  for(const service of services) {
    docker([...args,'up','-d','--no-build','--pull','never','--no-deps',service]);
    if(service!=='damgox2-worker')await wait(()=>{const id=docker([...args,'ps','-q',service]);return id&&inspect([id])[0].State.Health?.Status==='healthy';},service);
  }
  const ids=Object.fromEntries(services.map(s=>[s,docker([...args,'ps','-q',s])]));
  await wait(()=>sql("SELECT count(*)>0 FROM worker_status WHERE heartbeat_at>now()-interval '5 seconds'")==='t','worker');
  const status=uri=>command('curl',['--fail-with-body','--silent','--output','/dev/null','--write-out','%{http_code}',worker.environment.PUBLIC_BASE_URL+uri]);
  // curl's nonzero for expected 401/404 is handled separately without response bodies/cookies.
  const http=uri=>command('curl',['--silent','--output','/dev/null','--write-out','%{http_code}','--max-time','20',worker.environment.PUBLIC_BASE_URL+uri]);
  evidence.http={about:Number(status('/about.html')),edition:Number(status('/api/v1/public/news/edition')),account:Number(http('/api/v1/account')),newsletter:Number(http('/newsletter.html')),csrf:Number(status('/api/v1/auth/csrf'))};
  assert.deepEqual(evidence.http,{about:200,edition:200,account:401,newsletter:404,csrf:200});
  const headers=command('curl',['--silent','--head','--max-time','20',worker.environment.PUBLIC_BASE_URL+'/about.html']);assert.match(headers,/strict-transport-security:/i);assert.match(headers,/content-security-policy:/i);
  const w=ids['damgox2-worker'],stopAt=Date.now();docker(['stop','--timeout','15',w]);assert.notEqual(inspect([w])[0].State.ExitCode,137);assert(Date.now()-stopAt<15000);evidence.workerStopMillis=Date.now()-stopAt;
  const restartAt=Date.now();docker(['start',w]);await wait(()=>Date.parse(sql('SELECT max(heartbeat_at)::text FROM worker_status'))>restartAt,'restarted worker');evidence.workerRestart=true;
  const dbAfter=inspect([db.Id])[0];assert.deepEqual(safe(dbAfter),safe(db),'Existing PostgreSQL container/mount/start changed');evidence.database=safe(dbAfter);
  evidence.containers=inspect(Object.values(ids)).map(safe);
  const final=state();evidence.otherProjects=other.map(c=>({name:c.Name.slice(1),sameIdAndStart:final.some(n=>n.Id===c.Id&&n.State.Running&&n.State.StartedAt===c.State.StartedAt)}));
  assert(evidence.otherProjects.every(x=>x.sameIdAndStart),'Another running service changed during lock; inspect independent deployment');
  evidence.finishedAt=new Date().toISOString();evidence.status='PASS';write('result.json',evidence);
  fs.writeFileSync(base+'/latest-delivery.txt',directory+'\n',{mode:0o600});
  console.log('Deployment PASS: existing PostgreSQL/data/other projects preserved; Ubuntu25 app, proxy and both AI services active. Evidence: '+path.relative(root,directory));
} catch(error) {
  rollingBack=true;
  evidence.error=error.message;evidence.status='FAILED';
  try {
    if(started)for(const service of [...services].reverse()){const id=docker([...args,'ps','-aq',service]);if(id)docker(['stop','--timeout','20',id]);}
    policyChange.rollback(
      ()=>{evidence.reverseTransition=runtime('policy-transition',{DAMGOX2_POLICY_TRANSITION:'rollback-reviewed-java27'});},
      ()=>sql("SELECT (SELECT count(*) FROM article_translations WHERE NOT locked AND provider<>'"+legacy.translationProvider+"')+(SELECT count(*) FROM article_classification_jobs WHERE model_policy IS DISTINCT FROM '"+legacy.classificationPolicy+"')")==='0'
    );
    if(stopped)for(const name of ['damgox2-translator-1','damgox2-classifier-1','damgox2-web-1','damgox2-proxy-1','damgox2-worker-1'])docker(['start',old.find(c=>c.Name==='/'+name).Id]);
    evidence.rolledBack=true;
  }catch(rollback){evidence.rolledBack=false;evidence.rollbackError=rollback.message;}
  write('result.json',evidence);console.error('Delivery failed; rollback='+evidence.rolledBack+'; '+error.message+'; evidence='+path.relative(root,directory));process.exitCode=1;
}
