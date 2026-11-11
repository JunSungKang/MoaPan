import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {createHash,X509Certificate} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {artifactPath,writeArtifact} from './artifacts.mjs';
import {frozenConfigurations,serializeConfiguration,renderedEnvironment,FrozenDeployment} from './deployment-state.mjs';

export function memoryOverride(image,aiImage) {
    assert.match(image,/^sha256:[a-f0-9]{64}$/);
    const services=Object.fromEntries(['web','worker'].map(role=>['damgox2-'+role,{image,environment:{JAVA_TOOL_OPTIONS:'',DAMGOX2_DB_POOL_MAX:role==='web'?'8':'6',DAMGOX2_DB_POOL_MIN:role==='web'?'2':'1'}}]));
    if(aiImage){assert.match(aiImage,/^sha256:[a-f0-9]{64}$/);for(const role of ['translator','classifier'])services['damgox2-'+role]={image:aiImage,environment:{DAMGOX2_AI_IDLE_SECONDS:'300'}};}
    return {services};
}
export function certificateIdentity(pem) {
    const certificates=(pem.match(/-----BEGIN CERTIFICATE-----[^]*?-----END CERTIFICATE-----/g)||[]).map(p=>new X509Certificate(p).fingerprint256).sort();
    assert(certificates.length>=50,'Unrecognized truststore');
    return hash(JSON.stringify(certificates));
}
export function requestedMounts(service,configuration) {
    const mounts=(service.volumes||[]).map(v=>({type:v.type,source:v.type==='volume'?configuration.volumes[v.source].name:path.resolve(v.source),target:v.target,readOnly:Boolean(v.read_only)}));
    for(const s of service.secrets||[]){const target=s.target||s.source;mounts.push({type:'bind',source:path.resolve(configuration.secrets[s.source].file),target:target.startsWith('/')?target:'/run/secrets/'+target,readOnly:true});}
    return mounts.sort((a,b)=>a.target.localeCompare(b.target));
}
function command(bin,args,options={}) {
    try{return execFileSync(bin,args,{encoding:'utf8',stdio:['pipe','pipe','pipe'],timeout:120000,maxBuffer:32*1024*1024,...options}).trim();}
    catch{throw Error(bin+' '+args[0]+' failed; sensitive diagnostics withheld');}
}
const docker=(args,options)=>command('docker',args,options);
const inspect=id=>JSON.parse(docker(['inspect',id]))[0];
const envMap=c=>Object.fromEntries(c.Config.Env.map(e=>[e.slice(0,e.indexOf('=')),e.slice(e.indexOf('=')+1)]));
const hash=value=>createHash('sha256').update(value).digest('hex');

async function deploy(baseFile,image,lockFile,aiImage) {
    const root=fs.realpathSync('.'),dir=root+'/.local/memory-performance-20261111';
    assert(fs.realpathSync(dir).startsWith(root+'/.local/'));
    const fd=Number(process.env.DAMGOX2_DEPLOY_LOCK_FD);
    assert(Number.isInteger(fd)&&fd>=3,'Use native deploy-lock helper');
    const held=fs.fstatSync(fd),expected=fs.statSync(lockFile);
    assert(held.isFile()&&held.dev===expected.dev&&held.ino===expected.ino,'Wrong deployment lock');
    assert(path.isAbsolute(baseFile));assert(fs.statSync(baseFile).isFile());
    command('node',['scripts/security/verify-image-report.mjs','build/reports/security/image-'+image.slice(7)+'.json',image]);
    assert.match(aiImage,/^sha256:[a-f0-9]{64}$/);
    command('node',['scripts/security/verify-image-report.mjs','build/reports/security/image-'+aiImage.slice(7)+'.json',aiImage]);
    const previous=Object.fromEntries(['web','worker','translator','classifier'].map(r=>[r,inspect('damgox2-'+r)]));
    const all=JSON.parse(docker(['inspect',...docker(['ps','-q']).split('\n')]));
    const others=all.filter(c=>!Object.values(previous).some(p=>p.Id===c.Id)&&c.Config.Labels?.['com.docker.compose.project']==='platform'&&c.Config.Labels['com.docker.compose.oneoff']!=='True'&&!c.HostConfig.AutoRemove);
    for(const c of Object.values(previous)){assert(c.State.Running);assert.equal(c.Config.Labels['com.docker.compose.project'],'platform');docker(['image','inspect',c.Image]);}
    const compose=['compose','-p','platform','-f',baseFile];
    const baseConfig=()=>docker([...compose,'config','--format','json']);
    const originalConfig=baseConfig(),baseHash=hash(originalConfig);
    const configuration=JSON.parse(originalConfig),next=memoryOverride(image,aiImage);
    for(const [role,c]of Object.entries(previous)){
        docker(['image','tag',c.Image,'damgox2-memory-rollback:'+role+'-20261111']);
    }
    const frozen=frozenConfigurations(configuration,previous,next);
    const snapshotCompose=['compose','--env-file','/dev/null','-p','platform','-f','-'];
    const desired=JSON.parse(docker([...snapshotCompose,'config','--format','json'],{input:serializeConfiguration(frozen.after)}));
    for(const [role,c]of Object.entries(previous)){
        const s=desired.services['damgox2-'+role],e=envMap(c);
        assert.equal(s.container_name,c.Name.slice(1));
        assert(!s.entrypoint&&!s.command,'Base must use the delivered image entrypoint');
        const defaults=envMap(JSON.parse(docker(['image','inspect',s.image]))[0]);
        const effective={...defaults,...renderedEnvironment(s)};
        for(const key of new Set([...Object.keys(e),...Object.keys(effective)]))if(!['JAVA_TOOL_OPTIONS','DAMGOX2_DB_POOL_MAX','DAMGOX2_DB_POOL_MIN','DAMGOX2_AI_IDLE_SECONDS','MALLOC_ARENA_MAX'].includes(key)&&effective[key]!==e[key])throw Error('Unrelated effective environment differs: '+key);
        assert.deepEqual(Object.keys(s.networks).map(n=>desired.networks[n].name).sort(),Object.keys(c.NetworkSettings.Networks).sort(),'Network change');
        assert.deepEqual(requestedMounts(s,desired),c.Mounts.filter(m=>['bind','volume'].includes(m.Type)).map(m=>({type:m.Type,source:m.Type==='volume'?m.Name:path.resolve(m.Source),target:m.Destination,readOnly:!m.RW})).sort((a,b)=>a.target.localeCompare(b.target)),'Mount/secret/volume change');
        const ports=(s.ports||[]).map(p=>(p.host_ip||'0.0.0.0')+':'+p.published+'->'+p.target+'/'+(p.protocol||'tcp')).sort();
        const actualPorts=Object.entries(c.HostConfig.PortBindings||{}).flatMap(([target,bindings])=>bindings.map(p=>(p.HostIp||'0.0.0.0')+':'+p.HostPort+'->'+target)).sort();
        assert.deepEqual(ports,actualPorts,'Port change');assert.equal(Boolean(s.read_only),c.HostConfig.ReadonlyRootfs,'Read-only policy changed');
        assert.equal(Number(s.mem_limit),c.HostConfig.Memory,'Container memory cap changed');assert.equal(Number(s.cpus)*1e9,c.HostConfig.NanoCpus,'CPU cap changed');assert.equal(Number(s.pids_limit),c.HostConfig.PidsLimit,'PID cap changed');assert.equal(s.restart,c.HostConfig.RestartPolicy.Name,'Restart policy changed');
    }
    const policy=id=>JSON.parse(docker(['run','--rm','--read-only','--network','none','--memory','256m','--tmpfs','/tmp:size=32m,mode=1777','-e','DAMGOX2_ROLE=policy-info',id]));
    const targetPolicy=policy(image);
    for(const c of [previous.web,previous.worker])assert.deepEqual(policy(c.Image),targetPolicy,'AI policy changed');
    assert.equal(Object.keys(targetPolicy.classes).length,14);
    // PKCS12 stores contain per-save salts; compare actual trust anchors, not container bytes.
    const ca=id=>certificateIdentity(docker(['run','--rm','--read-only','--network','none','--entrypoint','keytool',id,'-list','-cacerts','-storepass','changeit','-rfc']));
    for(const c of [previous.web,previous.worker])assert.equal(ca(c.Image),ca(image),'Truststore changed');
    const native=id=>docker(['run','--rm','--read-only','--network','none','--entrypoint','sha256sum',id,'/usr/local/bin/llama-server']).split(' ')[0];
    for(const c of [previous.translator,previous.classifier]){assert.equal(native(c.Image),native(aiImage),'Native executable changed');assert.equal(JSON.parse(docker(['image','inspect',aiImage]))[0].Architecture,JSON.parse(docker(['image','inspect',c.Image]))[0].Architecture,'AI architecture changed');}
    const e=envMap(previous.worker),secret=previous.worker.Mounts.find(m=>m.Destination===e.DAMGOX2_DATABASE_PASSWORD_FILE);
    assert(secret?.Source);const password=fs.readFileSync(secret.Source,'utf8').replace(/\r?\n$/,'');
    const database=inspect(e.DAMGOX2_DATABASE_HOST);
    const sql=q=>docker(['exec','-i','-e','PGPASSWORD',database.Id,'psql','-X','-h','127.0.0.1','-U',e.DAMGOX2_DATABASE_USER,'-d',e.DAMGOX2_DATABASE_NAME,'-At','--set','ON_ERROR_STOP=1'],{env:{...process.env,PGPASSWORD:password},input:q});
    const stable=['users','sites','sources','articles','article_tags','article_translations','tag_aliases','tags','tag_redirects','article_revisions','newsletter_publications'];
    const snapshot=()=>Object.fromEntries(stable.filter(t=>sql(`SELECT to_regclass('public.${t}') IS NOT NULL`)==='t').map(t=>[t,hash(sql(`COPY (SELECT to_jsonb(r)::text FROM ${t} r ORDER BY to_jsonb(r)::text COLLATE "C") TO STDOUT`))]));
    // Persist only encrypted recovery data; automatic recovery uses this same frozen state.
    const identity=fs.realpathSync(process.env.DAMGOX2_RECOVERY_IDENTITY_FILE||root+'/.local/backups/keys/delivery-security-20261108.identity');
    assert(identity.startsWith(root+'/.local/'),'Recovery identity must stay in workspace');
    const recipient=command(root+'/.local/bin/age-keygen',['-y',identity]);
    const clear=Buffer.from(serializeConfiguration(frozen.before));
    const bytes=(args,input)=>{try{return execFileSync(root+'/.local/bin/age',args,{input,stdio:['pipe','pipe','pipe'],timeout:15000,maxBuffer:32*1024*1024});}catch{throw Error('Recovery encryption verification failed; credentials withheld');}};
    const sealed=bytes(['-r',recipient],clear);
    if(!bytes(['-d','-i',identity],sealed).equals(clear))throw Error('Encrypted recovery round trip mismatch');
    const archive=artifactPath(root,dir+'/recovery-'+Date.now()+'.json.age');
    const archiveFd=fs.openSync(archive,fs.constants.O_WRONLY|fs.constants.O_CREAT|fs.constants.O_EXCL|fs.constants.O_NOFOLLOW,0o600);
    try{fs.writeFileSync(archiveFd,sealed);fs.fsyncSync(archiveFd);}finally{fs.closeSync(archiveFd);}
    const evidence={startedAt:new Date().toISOString(),status:'INCOMPLETE',image,aiImage,baseConfigSha256:baseHash,encryptedRecovery:archive.slice(root.length+1),recoverySha256:hash(sealed),recoveryRoundTrip:true,policyAndTruststoreUnchanged:true,nativeExecutableUnchanged:true,oldImages:Object.fromEntries(Object.entries(previous).map(([r,c])=>[r,c.Image]))};
    const save=()=>writeArtifact(root,dir+'/deployment.json',evidence);
    const pause=ms=>new Promise(r=>setTimeout(r,ms));
    let cancelled=false,recovering=false;
    const cancel=()=>{cancelled=true;};
    const checkCancellation=()=>{if(cancelled&&!recovering)throw Error('Deployment cancelled');};
    for(const signal of ['SIGTERM','SIGINT','SIGHUP'])process.on(signal,cancel);
    async function wait(check){for(let i=0;i<120;i++){checkCancellation();try{if(check())return;}catch{}await pause(500);}throw Error('Deployment readiness timed out');}
    const transition=new FrozenDeployment({...frozen,expectedHash:baseHash,readHash:()=>hash(baseConfig()),execute:(state,role)=>docker([...snapshotCompose,'up','-d','--no-build','--no-deps','--pull','never','damgox2-'+role],{input:serializeConfiguration(state)})});
    const up=(rollback,role)=>{checkCancellation();return rollback?transition.restore(role):transition.apply(role);};
    let stopped=false;
    try {
        save();checkCancellation();stopped=true;docker(['stop','--timeout','45',previous.worker.Id,previous.web.Id]);
        const before=snapshot();
        up(false,'web');await wait(()=>inspect('damgox2-web').State.Health?.Status==='healthy');
        assert.deepEqual(snapshot(),before,'Stored data changed before worker restart');
        docker(['stop','--timeout','20',previous.translator.Id,previous.classifier.Id]);
        for(const role of ['translator','classifier']){up(false,role);await wait(()=>inspect('damgox2-'+role).State.Health?.Status==='healthy');}
        up(false,'worker');const started=Date.now();
        await wait(()=>Date.parse(sql('SELECT max(heartbeat_at)::text FROM worker_status'))>started);
        const worker=inspect('damgox2-worker');const stop=Date.now();docker(['stop','--timeout','15',worker.Id]);assert.notEqual(inspect(worker.Id).State.ExitCode,137);evidence.workerStopMs=Date.now()-stop;assert(evidence.workerStopMs<15000);const restart=Date.now();docker(['start',worker.Id]);await wait(()=>Date.parse(sql('SELECT max(heartbeat_at)::text FROM worker_status'))>restart);
        for(const role of ['web','worker','translator','classifier'])assert.equal(inspect('damgox2-'+role).Image,JSON.parse(docker(['image','inspect',['web','worker'].includes(role)?image:aiImage]))[0].Id);
        const baseUrl=envMap(previous.web).PUBLIC_BASE_URL;
        for(const [uri,code]of [['/about.html',200],['/api/v1/public/news/edition',200],['/api/v1/account',401],['/newsletter.html',404]])assert.equal(Number(command('curl',['--silent','--show-error','--max-time','15','--output','/dev/null','--write-out','%{http_code}',baseUrl+uri])),code);
        for(const c of others){const now=inspect(c.Id);assert(now.State.Running&&now.State.StartedAt===c.State.StartedAt,'Another service restarted');}
        checkCancellation();evidence.status='PASS';evidence.stableTablesUnchanged=Object.keys(before).length;evidence.otherContainersUnchanged=others.length;evidence.workerRestartHeartbeat=true;evidence.https={about:200,edition:200,account:401,newsletter:404};
    } catch(error) {
        evidence.status='FAILED';evidence.error=error.message;
        recovering=true;
        if(stopped){try{docker(['stop','--timeout','20','damgox2-worker']);up(true,'web');await wait(()=>inspect('damgox2-web').State.Health?.Status==='healthy');for(const role of ['translator','classifier']){up(true,role);await wait(()=>inspect('damgox2-'+role).State.Health?.Status==='healthy');}const restart=Date.now();up(true,'worker');await wait(()=>Date.parse(sql('SELECT max(heartbeat_at)::text FROM worker_status'))>restart);evidence.rollback=true;}catch{evidence.rollback=false;}}
        save();throw error;
    } finally {for(const signal of ['SIGTERM','SIGINT','SIGHUP'])process.removeListener(signal,cancel);}
    evidence.finishedAt=new Date().toISOString();save();console.log('Own web/worker/AI updated; data, other containers, policies and truststore preserved.');
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))await deploy(...process.argv.slice(2));
