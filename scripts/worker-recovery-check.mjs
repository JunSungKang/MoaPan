// Destructive to the LOCAL worker process only; use only the synthetic local Compose deployment.
import {execFileSync} from 'node:child_process';
import assert from 'node:assert/strict';
const docker=args=>execFileSync('docker',args,{timeout:30000,killSignal:'SIGKILL',encoding:'utf8',stdio:['pipe','pipe','pipe']}).trim();
const sql=text=>docker(['compose','exec','-T','db','psql','-U','moapan_migrate','-d','moapan','-At','--set','ON_ERROR_STOP=1','-c',text]);
assert.equal(process.env.PUBLIC_BASE_URL||'http://localhost:8080','http://localhost:8080');
const delay=ms=>new Promise(r=>setTimeout(r,ms));
assert.equal(docker(['compose','exec','-T','web','printenv','PUBLIC_BASE_URL']),'http://localhost:8080','Recovery test is restricted to the loopback synthetic deployment');
// Close only orphaned runs left by early local development failures, preserving their failure history.
sql("UPDATE fetch_runs f SET result='FAILED',error_code='local_pre_fix_orphan',finished_at=now() WHERE f.result='RUNNING' AND f.started_at<now()-interval '5 minutes' AND NOT EXISTS(SELECT 1 FROM content_jobs j WHERE j.source_id=f.source_id AND j.status='RUNNING')");
const before=Number(sql('SELECT count(*) FROM articles'));
const id=sql("INSERT INTO content_jobs(id,source_id,status,site_version,source_version,available_at) SELECT gen_random_uuid(),s.id,'QUEUED',p.version,s.version,GREATEST(now(),COALESCE(s.retry_after,now())) FROM sources s JOIN sites p ON p.id=s.site_id WHERE s.endpoint_url='https://www.nasa.gov/news-release/feed/' AND s.enabled AND p.status='ACTIVE' AND s.validation_status='PASSED' AND s.allow_metadata AND s.allow_display AND s.rights_status IN ('APPROVED','RESTRICTED') AND NOT EXISTS(SELECT 1 FROM content_jobs j WHERE j.source_id=s.id AND j.status IN ('QUEUED','RUNNING')) RETURNING id").split('\n')[0];
assert.match(id,/^[0-9a-f-]{36}$/);
const state=()=>sql(`SELECT status FROM content_jobs WHERE id='${id}'::uuid`);
let running=false;
for(let i=0;i<160;i++){const s=state();if(s==='RUNNING'){running=true;break;}assert.equal(s,'QUEUED','Job completed before controlled interruption; retry test separately');await delay(100);}
assert(running,'Worker did not claim job');
docker(['compose','kill','-s','SIGKILL','worker']);
assert.equal(state(),'RUNNING','Interrupted job must retain its lease');
docker(['compose','up','-d','worker']);
console.log('Worker interrupted during RUNNING and restarted; waiting for the actual two-minute lease to expire.');
let complete=false;
for(let i=0;i<220;i++){const s=state();if(s==='SUCCEEDED'){complete=true;break;}assert.notEqual(s,'FAILED','Worker recovery failed');await delay(1000);}
assert(complete,'Recovery deadline exceeded');
assert.equal(Number(sql('SELECT count(*) FROM articles')),before,'Recovery must not duplicate news');
assert.equal(Number(sql("SELECT count(*) FROM fetch_runs WHERE result='RUNNING'")),0,'Interrupted fetch run must be closed');
console.log(JSON.stringify({kind:'actual-worker-crash-recovery',checkedAt:new Date().toISOString(),jobStatus:state(),attempts:Number(sql(`SELECT attempts FROM content_jobs WHERE id='${id}'::uuid`)),articlesBefore:before,articlesAfter:Number(sql('SELECT count(*) FROM articles')),staleRunningFetches:0},null,2));
