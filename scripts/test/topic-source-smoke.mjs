import {readFileSync} from 'node:fs';
// Local synthetic account only; credentials and cookies remain in process memory.
import { randomBytes } from 'node:crypto';
import { spawnSync } from 'node:child_process';
const base = process.env.MOAPAN_SMOKE_BASE_URL || 'http://localhost:8080';
if (!base.startsWith('https://') && base !== 'http://localhost:8080') throw Error('Non-loopback smoke requires HTTPS');
let cookie='',csrf;
async function call(path,method='GET',body){
  const headers={Accept:'application/json'};if(cookie)headers.Cookie=cookie;if(method!=='GET'){headers['Content-Type']='application/json';headers['X-CSRF-TOKEN']=csrf;}
  const response=await fetch(base+path,{signal:AbortSignal.timeout(15000),method,headers,body:body===undefined?undefined:JSON.stringify(body)});
  for(const value of response.headers.getSetCookie()){const pair=value.split(';')[0];if(pair.startsWith('SESSION='))cookie=pair;}
  const text=await response.text();let result;try{result=text?JSON.parse(text):null;}catch{throw Error('Non-JSON API response');}
  if(!response.ok)throw Error(`HTTP ${response.status} ${result?.error||'request_failed'}`);return result;
}
async function token(){csrf=(await call('/api/v1/auth/csrf')).token;}
const username='smoke_'+randomBytes(6).toString('hex'), password=randomBytes(32).toString('base64url');let created=false;
try{
  await token();await call('/api/v1/auth/signup','POST',{username,password,termsAccepted:true,termsVersion:'terms-1'});created=true;
  const grant=spawnSync('docker',['compose','-f','compose.yaml','-f','scripts/deploy/compose-public.yaml','run','--rm','-T','-e','MOAPAN_ROLE=admin-cli','web'],{timeout:60000,killSignal:'SIGKILL',input:username+'\n',encoding:'utf8'});
  if(grant.status!==0)throw Error('Local admin-cli failed; inspect role startup without credentials');
  await call('/api/v1/auth/login','POST',{username,password});await token();
  const {definitions,publishedSince}=JSON.parse(readFileSync(new URL('../../config/sources/topic-sources.json',import.meta.url),'utf8'));
  const results=[];
  for(const d of definitions){try{await new Promise(r=>setTimeout(r,4000));
    const list=await call('/api/v1/admin/sites?search='+encodeURIComponent(d.name));let site=list.find(s=>s.normalized_homepage_url===d.home);
    if(!site)site=await call('/api/v1/admin/sites','POST',{name:d.name,homepageUrl:d.home,description:'Official agency feed; original English language.',adminNote:d.note});
    let sources=await call(`/api/v1/admin/sites/${site.id}/sources`);let source=sources.find(s=>s.normalized_endpoint_url===d.endpoint);
    if(!source)source=await call(`/api/v1/admin/sites/${site.id}/sources`,'POST',{label:d.label,type:d.type,endpointUrl:d.endpoint,language:'en',intervalSeconds:21600,enabled:false,allowMetadata:false,allowDisplay:false,allowSummary:false,allowBody:false,allowStore:false,allowTranslation:false,allowImage:false,allowExternalAi:false});
    const input={label:d.label,type:d.type,endpointUrl:d.endpoint,language:'en',intervalSeconds:21600,enabled:false,allowBody:false,allowStore:false,allowTranslation:false,allowImage:false,allowExternalAi:false,rightsStatus:'RESTRICTED',allowMetadata:true,allowDisplay:true,allowSummary:true,termsUrl:d.terms,termsCheckedOn:new Date().toISOString().slice(0,10),reviewNote:d.note,jsonMapping:d.mapping,publishedSince,topicTerms:d.topics,version:source.version};
    source=await call('/api/v1/admin/sources/'+source.id,'PATCH',input);
    const before=await call(`/api/v1/admin/sources/${source.id}/runs`);
    await call(`/api/v1/admin/sources/${source.id}/validate`,'POST');
    await new Promise(r=>setTimeout(r,4000));
    const preview=await call(`/api/v1/admin/sources/${source.id}/preview`,'POST');
    source=await call('/api/v1/admin/sources/'+source.id,'PATCH',{...input,enabled:true,version:source.version});
    await new Promise(r=>setTimeout(r,4000));
    await call(`/api/v1/admin/sites/${site.id}/activate`,'POST');const job=await call(`/api/v1/admin/sources/${source.id}/collect`,'POST');
    let done;for(let n=0;n<30;n++){done=await call('/api/v1/admin/jobs/'+job.id);if(['SUCCEEDED','FAILED','CANCELLED'].includes(done.status))break;await new Promise(resolve=>setTimeout(resolve,2000));}
    if(done.status!=='SUCCEEDED')throw Error('Collection '+done.status+' '+(done.error_code||''));
    const runs=await call(`/api/v1/admin/sources/${source.id}/runs`);results.push({site:d.name,label:d.label,endpoint:d.endpoint,status:done.status,previewCount:preview.items.length,lastRun:runs[0]?.finished_at,priorRuns:before.length});
  }catch(error){results.push({site:d.name,label:d.label,status:'FAILED',error:error.message});}}
  const edition=await call('/api/v1/news/edition');console.log(JSON.stringify({kind:'actual-topic-collection',checkedAt:new Date().toISOString(),sources:results,editionItems:edition.items.length,candidateCount:edition.candidateCount},null,2));if(results.some(r=>r.status==='FAILED'))process.exitCode=1;
}finally{
  if(created){try{if(!csrf)await token();await call('/api/v1/account','DELETE',{currentPassword:password});console.log('Synthetic verification account and sessions removed.');}catch{console.error('Synthetic verification account cleanup requires local administrator attention.');process.exitCode=1;}}
}
