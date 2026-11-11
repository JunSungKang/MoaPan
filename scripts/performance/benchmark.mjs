// Synthetic, disposable PostgreSQL only. No production URLs, users, or data.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {randomBytes,createHash} from 'node:crypto';
import {artifactPath,writeArtifact} from './artifacts.mjs';

const root=fs.realpathSync('.');
const out=artifactPath(root,process.env.DAMGOX2_BENCH_OUTPUT||'.local/memory-performance-20261111/benchmark.json');
const image=JSON.parse(docker(['image','inspect',process.env.DAMGOX2_BENCH_IMAGE||'platform/damgox2-app:ubuntu26.04-20261109-r2']))[0].Id;
const pgImage=JSON.parse(docker(['image','inspect',process.env.DAMGOX2_BENCH_PG_IMAGE||'platform/postgresql18:ubuntu26.04-20261109-r2']))[0].Id;
const dir=fs.mkdtempSync(path.dirname(out)+'/fixture-');
const network='damgox2-memory-fixture-'+process.pid;
const containers=new Set();
const delay=ms=>new Promise(r=>setTimeout(r,ms));
function docker(args,options={}) {
    try { return execFileSync('docker',args,{encoding:'utf8',stdio:['pipe','pipe','pipe'],maxBuffer:8*1024*1024,...options}).trim(); }
    catch { throw Error('Isolated benchmark Docker '+args[0]+' failed; sensitive diagnostics withheld'); }
}
async function wait(check) {
    const deadline=Date.now()+60000;
    while(Date.now()<deadline){try{if(await check())return;}catch{}await delay(400);}
    throw Error('Synthetic readiness timed out');
}
const password=randomBytes(24).toString('hex');
fs.writeFileSync(dir+'/password',password,{mode:0o444});
const env={...process.env,POSTGRES_PASSWORD:password,POSTGRES_APP_PASSWORD:password,PGDATA:'/var/lib/postgresql/fixture',POSTGRES_DB:'damgox2',POSTGRES_USER:'fixture_owner',DAMGOX2_SECRET:randomBytes(32).toString('hex')};
let db;
const result={at:new Date().toISOString(),image:docker(['image','inspect','--format','{{.Id}}',image]),cpu:1.5,memoryLimitMiB:768,concurrency:8,articles:300,runs:[],productionDataUsed:false};
const sql=query=>docker(['exec','-i','-e','PGPASSWORD',db,'psql','-X','-h','127.0.0.1','-U','fixture_owner','-d','damgox2','-At','--set','ON_ERROR_STOP=1'],{env:{...env,PGPASSWORD:password},input:query});
function run(role,tuned,daemon=true) {
    const e={...env,DAMGOX2_DATABASE_HOST:'fixture-db',DAMGOX2_DATABASE_NAME:'damgox2',DAMGOX2_DATABASE_USER:'fixture_owner',DAMGOX2_DATABASE_PASSWORD_FILE:'/run/secrets/database-password',DAMGOX2_ROLE:role,PUBLIC_BASE_URL:'http://127.0.0.1',COOKIE_SECURE:'false',DAMGOX2_MAIL_ENABLED:'false'};
    if(tuned)Object.assign(e,{JAVA_TOOL_OPTIONS:'',MALLOC_ARENA_MAX:'2',SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE:role==='worker'?'6':'8',SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE:role==='worker'?'1':'2',SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT:'60000',SERVER_TOMCAT_THREADS_MAX:'32',SERVER_TOMCAT_THREADS_MIN_SPARE:'2'});
    else Object.assign(e,{JAVA_TOOL_OPTIONS:'-XX:MaxRAMPercentage=70 -Djava.io.tmpdir=/tmp -Duser.timezone=UTC',MALLOC_ARENA_MAX:'0',SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE:'12',SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE:'12',SERVER_TOMCAT_THREADS_MAX:'200',SERVER_TOMCAT_THREADS_MIN_SPARE:'10'});
    const names=Object.keys(e).filter(k=>/^(DAMGOX2_|SPRING_|SERVER_|JAVA_TOOL_OPTIONS$|MALLOC_ARENA_MAX$|PUBLIC_BASE_URL$|COOKIE_SECURE$)/.test(k));
    const id=docker(['run',...(daemon?['-d']:['--rm']),'--network',network,'--read-only','--tmpfs','/tmp:size=128m,mode=1777','--memory','768m','--cpus','1.5','--pids-limit','256','--cap-drop','ALL','--security-opt','no-new-privileges:true',...(role==='web'?['-p','127.0.0.1::8080']:[]),'--mount','type=bind,src='+dir+'/password,dst=/run/secrets/database-password,readonly',...(tuned?['--mount','type=bind,src='+root+'/scripts/performance/java-entrypoint.sh,dst=/performance-entrypoint.sh,readonly','--entrypoint','sh']:[]),...names.flatMap(k=>['-e',k]),image,...(tuned?['/performance-entrypoint.sh']:[])],{env:e});
    if(daemon)containers.add(id);
    return id;
}
function memory(id) {
    const raw=docker(['exec',id,'sh','-ec','cat /sys/fs/cgroup/memory.current; cat /sys/fs/cgroup/memory.stat; cat /proc/1/smaps_rollup']);
    return {cgroupMiB:Number(raw.split('\n')[0])/1048576,workingMiB:(Number(raw.split('\n')[0])-Number(raw.match(/^inactive_file (\d+)/m)?.[1]||0))/1048576,rssMiB:Number(raw.match(/^Rss:\s+(\d+)/m)[1])/1024,pssMiB:Number(raw.match(/^Pss:\s+(\d+)/m)[1])/1024};
}
async function exercise(url,count,concurrency=8) {
    const times=[];let next=0;const start=performance.now();
    await Promise.all(Array.from({length:concurrency},async()=>{while(next<count){next++;const before=performance.now();const response=await fetch(url+'/api/v1/public/news/edition',{headers:{Host:'127.0.0.1'},signal:AbortSignal.timeout(15000)});assert.equal(response.status,200);const body=await response.json();assert.equal(body.items.length,42);assert.equal(body.candidateCount,300);const payload=createHash('sha256').update(JSON.stringify(body.items)).digest('hex');result.payloadSha256??=payload;assert.equal(payload,result.payloadSha256,'Response changed between variants');times.push(performance.now()-before);}}));
    times.sort((a,b)=>a-b);return {requests:times.length,errors:0,p50Ms:times[Math.floor(times.length*.5)],p95Ms:times[Math.floor(times.length*.95)],requestsPerSecond:count/((performance.now()-start)/1000)};
}
try {
    // Dedicated network, with only the synthetic HTTP server published to loopback.
    // Sources remain DRAFT/disabled, so the worker never fetches external content.
    docker(['network','create',network]);
    // The common production entrypoint deliberately requires operator secrets and
    // reserves 512MiB shared buffers. This fixture owns its smaller initdb cluster.
    db=docker(['run','-d','--network',network,'--network-alias','fixture-db','--read-only','--user','postgres','--tmpfs','/var/lib/postgresql:rw,mode=1777,size=512m','--tmpfs','/var/run/postgresql:rw,mode=1777','--tmpfs','/tmp:size=32m,mode=1777','--memory','384m','--cpus','1','--cap-drop','ALL','--security-opt','no-new-privileges:true','--mount','type=bind,src='+dir+'/password,dst=/run/secrets/password,readonly','--entrypoint','sh',pgImage,'-ec',`initdb -D /var/lib/postgresql/fixture -U fixture_owner --encoding=UTF8 --locale=C.UTF-8 --auth-local=trust --auth-host=scram-sha-256 --pwfile=/run/secrets/password >/dev/null; printf '\\nhost all all all scram-sha-256\\n' >> /var/lib/postgresql/fixture/pg_hba.conf; exec postgres -D /var/lib/postgresql/fixture -c listen_addresses='*' -c shared_buffers=32MB -c max_connections=40 -c log_statement=none -c log_min_error_statement=panic`]);containers.add(db);
    await wait(()=>docker(['exec',db,'pg_isready','-U','fixture_owner','-d','damgox2','-q'])==='');
    docker(['exec',db,'createdb','-U','fixture_owner','damgox2']);
    run('migrate',false,false);
    sql(`INSERT INTO sites(id,name,homepage_url,normalized_homepage_url) VALUES('10000000-0000-0000-0000-000000000001','Synthetic benchmark','https://example.test','https://example.test');
      INSERT INTO sources(id,site_id,label,type,endpoint_url,normalized_endpoint_url,rights_status,allow_metadata,allow_display,allow_summary) VALUES('10000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001','Synthetic source','RSS','https://example.test/feed','https://example.test/feed','APPROVED',true,true,true);
      INSERT INTO articles(id,source_id,source_guid,canonical_url,title,summary_bullets,summary_mode,language,input_hash) SELECT gen_random_uuid(),'10000000-0000-0000-0000-000000000002',n::text,'https://example.test/'||n,'Synthetic memory performance research '||n,jsonb_build_array(repeat('Synthetic research summary. ',20),repeat('Synthetic test conditions. ',20)),'EXTRACTIVE_ORIGINAL_LANGUAGE','en',repeat('a',64) FROM generate_series(1,300) n;`);
    for(const tuned of [false,true,true,false]) {
        sql('TRUNCATE auth_rate_limits; DELETE FROM worker_status;');
        const started=Date.now(),web=run('web',tuned);
        const binding=JSON.parse(docker(['inspect',web]))[0].NetworkSettings.Ports['8080/tcp'][0];
        const url='http://127.0.0.1:'+binding.HostPort;
        await wait(async()=>{const r=await fetch(url+'/internal/ready',{headers:{Host:'127.0.0.1'},signal:AbortSignal.timeout(1000)});return r.status===200;});
        const startupMs=Date.now()-started;
        await exercise(url,120);
        const idle=memory(web),load=await exercise(url,240),burst=await exercise(url,80,32),peak=memory(web);
        const connections=Number(sql("SELECT count(*) FROM pg_stat_activity WHERE datname='damgox2' AND usename='fixture_owner' AND application_name='PostgreSQL JDBC Driver'"));
        // First pair also verifies that excess connections are actually returned.
        let afterIdle=null,idleConnections=null;
        if(result.runs.length<2){await delay(95000);afterIdle=memory(web);idleConnections=Number(sql("SELECT count(*) FROM pg_stat_activity WHERE datname='damgox2' AND usename='fixture_owner' AND application_name='PostgreSQL JDBC Driver'"));}
        docker(['stop','--timeout','15',web]);docker(['rm',web]);containers.delete(web);
        const worker=run('worker',tuned);
        await wait(()=>Number(sql('SELECT count(*) FROM worker_status WHERE heartbeat_at>now()-interval \'5 seconds\''))>0);
        await delay(2000);const workerMemory=memory(worker);
        const stop=Date.now();docker(['stop','--timeout','15',worker]);const state=JSON.parse(docker(['inspect',worker]))[0].State;assert.notEqual(state.ExitCode,137);const workerStopMs=Date.now()-stop;
        docker(['rm',worker]);containers.delete(worker);
        result.runs.push({variant:tuned?'tuned':'baseline',startupMs,idle,peak,connections,afterIdle,idleConnections,workerMemory,workerStopMs,burst,...load});
        writeArtifact(root,out,result);
        console.log(JSON.stringify(result.runs.at(-1)));
    }
    result.completed=true;writeArtifact(root,out,result);
}finally {
    for(const id of [...containers].reverse())try{docker(['rm','-f',id]);}catch{}
    try{docker(['network','rm',network]);}catch{}
    fs.rmSync(dir,{recursive:true});
}
