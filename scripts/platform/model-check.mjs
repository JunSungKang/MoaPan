import fs from 'node:fs';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';

// One model at a time. No production DB, article text, writable model mount or exposed port.
export async function modelCheck({image,configuration,directory}) {
  const run=(args,input)=>{try{return execFileSync('docker',args,{encoding:'utf8',input,stdio:['pipe','pipe','pipe'],maxBuffer:1048576}).trim();}catch{throw Error('Isolated model '+args[0]+' failed; sensitive diagnostics withheld');}};
  const wait=async fn=>{for(let i=0;i<180;i++){try{if(fn())return;}catch{}await new Promise(r=>setTimeout(r,1000));}throw Error('Model startup timeout');};
  const network='damgox2-model-check-'+process.pid;const results=[];
  run(['network','create','--internal',network]);
  try {
    for(const kind of ['translation','classification']) {
      let container;
      const keySource=configuration.secrets['damgox2-'+kind+'-key'].file;
      const volume=configuration.volumes['damgox2-'+kind+'-models'].name;
      const key=fs.readFileSync(keySource,'utf8').trim();assert(key.length>=32);
      try {
        container=run(['run','-d','--network',network,'--read-only','--tmpfs','/tmp:size=128m,mode=1777','--cap-drop','ALL','--security-opt','no-new-privileges:true','--memory','4g','--cpus','2','--pids-limit','128','-e','DAMGOX2_AI_KIND='+kind,'-v',volume+':/models:ro','--mount','type=bind,src='+keySource+',dst=/run/secrets/'+kind+'_key,readonly',image]);
        await wait(()=>run(['exec',container,'curl','-fsS','--max-time','2','http://127.0.0.1:9931/health']).includes('ok'));
        const request=body=>{
          const config='silent\nshow-error\nfail\nmax-time = 300\nurl = "http://127.0.0.1:9931/v1/chat/completions"\nheader = '+JSON.stringify('Authorization: Bearer '+key)+'\nheader = "Content-Type: application/json"\nrequest = "POST"\ndata = '+JSON.stringify(JSON.stringify(body))+'\n';
          return JSON.parse(run(['exec','-i',container,'curl','--config','-'],config));
        };
        const started=Date.now();let text;
        if(kind==='translation') {
          const reply=request({model:'moapan-translation',messages:[{role:'system',content:'请将以下文本翻译为韩语，直接输出翻译结果，不要进行任何解释。保留数字。'},{role:'user',content:'Database search latency decreased by 20%.'}],temperature:0,max_tokens:120,stream:false});
          text=reply.choices?.[0]?.message?.content;assert.equal(typeof text,'string');assert.match(text,/[가-힣]/);assert.match(text,/20/);
        } else {
          const schema={type:'object',properties:{topics:{type:'array',items:{type:'string',enum:['database','security']},maxItems:2}},required:['topics'],additionalProperties:false};
          const reply=request({model:'moapan-classification',messages:[{role:'system',content:'Classify the article using database or security. Return JSON {"topics":[...]}. Database covers database search and indexing. Security covers vulnerabilities.'},{role:'user',content:'A PostgreSQL database indexing experiment reduced database search latency.'}],temperature:0,seed:42,max_tokens:160,stream:false,chat_template_kwargs:{enable_thinking:false},response_format:{type:'json_schema',json_schema:{name:'topics',strict:true,schema}}});
          text=reply.choices?.[0]?.message?.content;const topics=JSON.parse(text);assert.deepEqual(Object.keys(topics),['topics']);assert(topics.topics.includes('database'));assert(topics.topics.every(t=>['database','security'].includes(t)));
        }
        const abi=run(['exec',container,'sh','-ec','uname -m; ldd --version | head -n 1; ldd /usr/local/bin/llama-server']);
        assert.match(abi,/aarch64|x86_64/);assert.match(abi,/GLIBC|GNU libc/);assert(!abi.includes('not found'));
        const unauthorized=run(['exec',container,'curl','-s','-o','/dev/null','-w','%{http_code}','http://127.0.0.1:9931/v1/models']);assert.equal(unauthorized,'401');
        results.push({kind,status:'PASS',durationMs:Date.now()-started,output:text,apiAuthentication:401,port:9931,abi,modelVolumeReadOnly:true});
      } finally {if(container)run(['rm','-f',container]);}
    }
  } finally {run(['network','rm',network]);}
  fs.writeFileSync(directory+'/model-check.json',JSON.stringify({verifiedAt:new Date().toISOString(),image,results},null,2)+'\n',{mode:0o600});
  return results;
}
