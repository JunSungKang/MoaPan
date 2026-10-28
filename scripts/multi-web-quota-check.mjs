// Real local HTTP and two independent web processes; credentials exist only in memory/stdin.
import {randomBytes} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import assert from 'node:assert/strict';
const origin='http://localhost:8080';
const ids=execFileSync('docker',['compose','ps','-q','web'],{timeout:30000,killSignal:'SIGKILL',encoding:'utf8'}).trim().split('\n');assert.equal(ids.length,2,'Scale local web to exactly two instances first');
for(const id of ids)assert.equal(execFileSync('docker',['exec',id,'printenv','PUBLIC_BASE_URL'],{timeout:30000,killSignal:'SIGKILL',encoding:'utf8'}).trim(),origin);
const username='quota_'+randomBytes(7).toString('hex'),password=randomBytes(24).toString('base64url');
let cookie='',csrf;
async function call(path,method='GET',body){
 const headers={};if(cookie)headers.Cookie=cookie;
 if(method!=='GET'){if(!csrf)await token();headers['X-CSRF-TOKEN']=csrf;headers['Content-Type']='application/json';}
 const r=await fetch(origin+path,{signal:AbortSignal.timeout(15000),method,headers,body:body===undefined?undefined:JSON.stringify(body)});
 const set=r.headers.get('set-cookie');if(set)cookie=set.split(';')[0];const text=await r.text();return {status:r.status,data:text?JSON.parse(text):null};
}
async function token(){const r=await call('/api/v1/auth/csrf');assert.equal(r.status,200);csrf=r.data.token;}
const direct=id=>{
 const config=`url="http://localhost:8080/api/v1/news/edition"\nheader="Host: localhost"\nheader="Cookie: ${cookie}"\n`;
 const out=execFileSync('docker',['exec','-i',id,'curl','-sS','--config','-','-w','\n%{http_code}'],{timeout:30000,killSignal:'SIGKILL',input:config,encoding:'utf8',stdio:['pipe','pipe','pipe']});return Number(out.slice(out.lastIndexOf('\n')+1));
};
let registered=false;
try{
 await token();assert.equal((await call('/api/v1/auth/signup','POST',{username,password,termsAccepted:true,termsVersion:'2026-10-28'})).status,200);registered=true;
 assert.equal((await call('/api/v1/auth/login','POST',{username,password})).status,200);csrf=undefined;await token();
 const within=Date.now()%60000;if(within>30000)await new Promise(r=>setTimeout(r,61000-within));
 let successful=0;for(let i=0;i<60;i++){assert.equal(direct(ids[i%2]),200);successful++;}
 assert.equal(direct(ids[0]),429);assert.equal(direct(ids[1]),429);
 assert.equal((await call('/api/v1/auth/logout','POST')).status,200);csrf=undefined;await token();assert.equal((await call('/api/v1/auth/login','POST',{username,password})).status,200);csrf=undefined;await token();assert.equal(direct(ids[1]),429);
 console.log(JSON.stringify({kind:'actual-multi-web-quota',checkedAt:new Date().toISOString(),webInstances:2,successfulRequests:successful,overLimitBoth:429,reloginStatus:429},null,2));
}finally{if(registered){const result=await call('/api/v1/account','DELETE',{currentPassword:password});assert.equal(result.status,200);console.log('Synthetic quota account and sessions removed.');}}
