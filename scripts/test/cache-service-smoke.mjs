// Approved live-service checks. Only a synthetic account is created; secrets stay in memory.
import fs from 'node:fs';
import path from 'node:path';
import {randomBytes} from 'node:crypto';
import {spawnSync} from 'node:child_process';
const root=fs.realpathSync(process.cwd());
const base=process.env.DAMGOX2_SMOKE_BASE_URL;
if(!base||new URL(base).protocol!=='https:')throw Error('Set the approved HTTPS smoke base URL');
const directory=path.resolve(root,'.local/server-cache-live-20261101');
if(!directory.startsWith(root+path.sep))throw Error('Report outside workspace');
fs.mkdirSync(directory,{recursive:true});
const state=path.join(directory,'synthetic-cleanup.json');
const username='cache_smoke_'+randomBytes(8).toString('hex'),password=randomBytes(32).toString('base64url');
const report={checkedAt:new Date().toISOString(),checks:[],timingsMs:{},syntheticAccountRemoved:false};
let cookie='',csrf='',attempted=false;
const assert=(condition,message)=>{if(!condition)throw Error(message);};
async function call(url,method='GET',body,expected=200){
    const headers={Accept:'application/json'};if(cookie)headers.Cookie=cookie;
    if(method!=='GET'){headers['Content-Type']='application/json';headers['X-CSRF-TOKEN']=csrf;}
    const response=await fetch(base+url,{method,headers,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(20000)});
    for(const value of response.headers.getSetCookie()){const pair=value.split(';')[0];if(pair.startsWith('SESSION='))cookie=pair;}
    const text=await response.text();let data;try{data=text?JSON.parse(text):null;}catch{throw Error('Expected API JSON');}
    assert(response.status===expected,`Unexpected HTTP status for ${url.split('?')[0]}: ${response.status}`);
    if(url.startsWith('/api/'))assert(response.headers.get('cache-control')?.includes('no-store'),'Dynamic API must remain no-store');
    return data;
}
async function token(){csrf=(await call('/api/v1/auth/csrf')).token;}
async function login(){await token();await call('/api/v1/auth/login','POST',{username,password});await token();}
async function twice(url,name){const times=[];let result;for(let i=0;i<2;i++){const start=performance.now();result=await call(url);times.push(Math.round((performance.now()-start)*100)/100);}report.timingsMs[name]=times;report.checks.push(name);return result;}
try{
    await call('/api/v1/news/edition','GET',undefined,401);
    await call('/api/v1/newsletters','GET',undefined,401);
    await twice('/api/v1/public/tags','public-tags');await twice('/api/v1/public/interests','public-representative-defaults');
    const publicNews=await twice('/api/v1/public/news/edition','public-edition');assert(publicNews.items.length<=42&&publicNews.candidateCount<=300,'Edition bounds changed');
    await token();attempted=true;fs.writeFileSync(state,JSON.stringify({syntheticUsername:username,state:'SIGNUP_ATTEMPTED'})+'\n',{mode:0o600});
    await call('/api/v1/auth/signup','POST',{username,password,termsAccepted:true,termsVersion:'terms-1'});await login();
    await call('/api/v1/admin/sites','GET',undefined,403);report.checks.push('ordinary-member-admin-forbidden');
    await call('/api/v1/auth/logout','POST');cookie='';csrf='';
    const grant=spawnSync('docker',['compose','run','--rm','--no-deps','-T','-e','DAMGOX2_ROLE=admin-cli','web'],{input:username+'\n',encoding:'utf8',timeout:60000,killSignal:'SIGKILL'});
    assert(grant.status===0,'Synthetic local admin-cli failed');await login();
    await twice('/api/v1/admin/classification','classification-settings');await twice('/api/v1/sources','public-source-catalog');await twice('/api/v1/advertisements','public-advertisement');
    const accountNews=await twice('/api/v1/news/edition?size=42','authenticated-edition');assert(accountNews.items.length<=42,'Authenticated page bound changed');
    const publications=await twice('/api/v1/newsletters','publication-catalog');assert(publications.length>0,'Existing publication is not currently visible; do not change source rights to force a pass');
    await twice('/api/v1/newsletters/'+publications[0].id,'published-manuscript');
    const asset=await fetch(base+'/assets/js/shared/dom.js',{headers:{'Accept-Encoding':'gzip'},signal:AbortSignal.timeout(10000)});assert(asset.ok,'Shared browser module missing');
    const etag=asset.headers.get('etag');assert(etag&&asset.headers.get('cache-control')?.includes('no-cache'),'Static asset revalidation missing');await asset.arrayBuffer();
    const unchanged=await fetch(base+'/assets/js/shared/dom.js',{headers:{'If-None-Match':etag,'Accept-Encoding':'gzip'},signal:AbortSignal.timeout(10000)});assert(unchanged.status===304,'Static content ETag did not revalidate');report.checks.push('static-shared-module-etag-304');
    const histogram=spawnSync('docker',['exec','damgox2-web-1','jcmd','1','GC.class_histogram'],{encoding:'utf8',timeout:30000,killSignal:'SIGKILL',maxBuffer:8*1024*1024});
    assert(histogram.status===0,'Nonidentifying cache heap inspection failed');
    const allowed=new Set(['com.moapan.shared.infrastructure.cache.VersionedReadCache$Entry','com.moapan.edition.application.EditionReadModels$Slice','com.moapan.newsletter.PublicationReadModels$Detail','com.moapan.classification.domain.TopicTaxonomy$Snapshot','com.moapan.edition.application.RepresentativeInterests$Settings','com.moapan.advertising.application.AdvertisingSettings$Display']);
    report.liveCacheClasses=histogram.stdout.split('\n').flatMap(line=>{const m=line.trim().match(/^\d+:\s+(\d+)\s+(\d+)\s+(\S+)/);return m&&allowed.has(m[3])?[{class:m[3],instances:Number(m[1]),shallowBytes:Number(m[2])}]:[];});
    assert(report.liveCacheClasses.some(c=>c.class.endsWith('VersionedReadCache$Entry')&&c.instances>0),'No live cache entries observed');assert(report.liveCacheClasses.some(c=>c.class.endsWith('EditionReadModels$Slice')&&c.instances>0),'No live cached card slice observed');assert(report.liveCacheClasses.some(c=>c.class.endsWith('PublicationReadModels$Detail')&&c.instances>0),'Published manuscript was not retained');
    report.heapScope='Live class counts and shallow sizes only; not retained graph size or cache hit-rate measurement';
    report.checks.push('live-cached-models-observed-in-web-jvm');
}finally{
    if(attempted){
        try{await login();await call('/api/v1/account','DELETE',{currentPassword:password});cookie='';csrf='';report.syntheticAccountRemoved=true;fs.rmSync(state,{force:true});}
        catch{report.syntheticAccountRemoved=false;console.error('Synthetic cleanup failed; local recovery handle retained without password or tokens.');process.exitCode=1;}
    }
    fs.writeFileSync(path.join(directory,'https-smoke.json'),JSON.stringify(report,null,2)+'\n');
}
console.log(JSON.stringify(report,null,2));
