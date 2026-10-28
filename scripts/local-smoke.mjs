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
  await token();await call('/api/v1/auth/signup','POST',{username,password,termsAccepted:true,termsVersion:'2026-10-28'});created=true;
  const grant=spawnSync('docker',['compose','run','--rm','-T','-e','MOAPAN_ROLE=admin-cli','web'],{timeout:60000,killSignal:'SIGKILL',input:username+'\n',encoding:'utf8'});
  if(grant.status!==0)throw Error('Local admin-cli failed; inspect role startup without credentials');
  await call('/api/v1/auth/login','POST',{username,password});await token();
  const definitions=[
    {name:'NASA News Releases',home:'https://www.nasa.gov/',endpoint:'https://www.nasa.gov/news-release/feed/',terms:'https://www.nasa.gov/nasa-brand-center/images-and-media/',note:'Official NASA release feed text only; informational factual use with attribution, no endorsement. Images, logos, full body and third-party copyrighted material excluded.'},
    {name:'NOAA News',home:'https://www.noaa.gov/',endpoint:'https://www.noaa.gov/rss.xml',terms:'https://oceanservice.noaa.gov/about/faq.html',note:'Official NOAA feed text only, attribution and no endorsement. Third-party marked material, images, logos and full body excluded. Agency public-domain guidance reviewed; business legal review separate.'}
  ];
  const results=[];
  for(const d of definitions){
    const list=await call('/api/v1/admin/sites?search='+encodeURIComponent(d.name));let site=list.find(s=>s.normalized_homepage_url===d.home);
    if(!site)site=await call('/api/v1/admin/sites','POST',{name:d.name,homepageUrl:d.home,description:'Official agency feed; original English language.',adminNote:d.note});
    let sources=await call(`/api/v1/admin/sites/${site.id}/sources`);let source=sources.find(s=>s.normalized_endpoint_url===d.endpoint);
    if(!source)source=await call(`/api/v1/admin/sites/${site.id}/sources`,'POST',{label:d.name,type:'RSS',endpointUrl:d.endpoint,language:'en',intervalSeconds:900,enabled:false,allowMetadata:false,allowDisplay:false,allowSummary:false,allowBody:false,allowStore:false,allowTranslation:false,allowImage:false,allowExternalAi:false});
    const input={label:d.name,type:'RSS',endpointUrl:d.endpoint,language:'en',intervalSeconds:900,enabled:false,allowBody:false,allowStore:false,allowTranslation:false,allowImage:false,allowExternalAi:false,rightsStatus:'RESTRICTED',allowMetadata:true,allowDisplay:true,allowSummary:true,termsUrl:d.terms,termsCheckedOn:'2026-10-28',reviewNote:d.note,jsonMapping:{},version:source.version};
    source=await call('/api/v1/admin/sources/'+source.id,'PATCH',input);
    const before=await call(`/api/v1/admin/sources/${source.id}/runs`);
    await call(`/api/v1/admin/sources/${source.id}/validate`,'POST');
    const preview=await call(`/api/v1/admin/sources/${source.id}/preview`,'POST');if(!preview.items?.length)throw Error('Actual source returned no preview');
    source=await call('/api/v1/admin/sources/'+source.id,'PATCH',{...input,enabled:true,version:source.version});
    await call(`/api/v1/admin/sites/${site.id}/activate`,'POST');const job=await call(`/api/v1/admin/sources/${source.id}/collect`,'POST');
    let done;for(let n=0;n<30;n++){done=await call('/api/v1/admin/jobs/'+job.id);if(['SUCCEEDED','FAILED','CANCELLED'].includes(done.status))break;await new Promise(resolve=>setTimeout(resolve,2000));}
    if(done.status!=='SUCCEEDED')throw Error('Collection '+done.status+' '+(done.error_code||''));
    const runs=await call(`/api/v1/admin/sources/${source.id}/runs`);results.push({site:d.name,endpoint:d.endpoint,status:done.status,previewCount:preview.items.length,lastRun:runs[0]?.finished_at,priorRuns:before.length});
  }
  const edition=await call('/api/v1/news/edition');console.log(JSON.stringify({kind:'actual-source-smoke',checkedAt:new Date().toISOString(),sources:results,editionItems:edition.items.length,candidateCount:edition.candidateCount},null,2));
}finally{
  if(created){try{if(!csrf)await token();await call('/api/v1/account','DELETE',{currentPassword:password});console.log('Synthetic verification account and sessions removed.');}catch{console.error('Synthetic verification account cleanup requires local administrator attention.');process.exitCode=1;}}
}
