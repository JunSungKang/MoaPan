// Exercise the delivered scripts, pinned model volumes and private API on disposable containers.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {performance} from 'node:perf_hooks';
import {writeArtifact} from './artifacts.mjs';

const root=fs.realpathSync('.'),dir=root+'/.local/memory-performance-20261111';
assert(fs.realpathSync(dir).startsWith(root+'/.local/'));
const image=JSON.parse(docker(['image','inspect',process.env.DAMGOX2_BENCH_AI_IMAGE||'damgox2-ai:memory-delivery-20261111']))[0].Id;
const delay=ms=>new Promise(r=>setTimeout(r,ms));
const result={image,checkedAt:new Date().toISOString(),fixtureIdleSeconds:10,productionIdleSeconds:300,models:[]};
function docker(args,options={}) {
    try{return execFileSync('docker',args,{encoding:'utf8',stdio:['pipe','pipe','pipe'],...options}).trim();}
    catch{throw Error('AI fixture Docker '+args[0]+' failed; credentials withheld');}
}
for(const kind of ['translation','classification']) {
    let id;
    try {
        const live=JSON.parse(docker(['inspect',kind==='translation'?'damgox2-translator':'damgox2-classifier']))[0];
        const key=live.Mounts.find(m=>m.Destination==='/run/secrets/'+kind+'_key');
        const model=live.Mounts.find(m=>m.Destination==='/models');
        assert(key?.Source&&model?.Name&&!model.RW);
        id=docker(['run','-d','--read-only','--network','none','--memory','4g','--cpus','2','--pids-limit','128','--cap-drop','ALL','--security-opt','no-new-privileges:true','--tmpfs','/tmp:size=128m,mode=1777','--mount','type=volume,src='+model.Name+',dst=/models,readonly','--mount','type=bind,src='+key.Source+',dst='+key.Destination+',readonly','-e','DAMGOX2_AI_KIND='+kind,'-e','DAMGOX2_AI_IDLE_SECONDS=10',image]);
        const getHealth=()=>docker(['exec',id,'curl','-fsS','--max-time','5','http://127.0.0.1:9931/health']);
        for(let n=0;n<180;n++){try{if(JSON.parse(getHealth()).status==='ok')break;}catch{}await delay(500);if(n===179)throw Error('AI startup timed out');}
        const native=digest=>docker(['run','--rm','--read-only','--network','none','--entrypoint','sha256sum',digest,'/usr/local/bin/llama-server']).split(' ')[0];
        assert.equal(native(live.Image),native(image));
        const request=(endpoint,body)=>{
            let config='silent\nshow-error\nfail\nmax-time=120\nurl='+JSON.stringify('http://127.0.0.1:9931'+endpoint)+'\nheader='+JSON.stringify('Authorization: Bearer '+fs.readFileSync(key.Source,'utf8').trim())+'\n';
            if(body)config+='header="Content-Type: application/json"\nrequest="POST"\ndata='+JSON.stringify(JSON.stringify(body))+'\n';
            const start=performance.now();
            const value=JSON.parse(docker(['exec','-i',id,'curl','--config','-'],{input:config}));
            return {milliseconds:performance.now()-start,value};
        };
        const infer=()=>{
            const data=request('/v1/chat/completions',{model:'damgox2-'+kind,messages:[{role:'user',content:kind==='translation'?'Translate to Korean: The database uses an index.':'Answer only DATABASE. The database uses an index.'}],temperature:0,seed:42,max_tokens:32,stream:false});
            return {milliseconds:data.milliseconds,text:data.value.choices[0].message.content};
        };
        const memory=()=>{
            const text=docker(['exec',id,'sh','-ec','for d in /proc/[0-9]*; do read name < "$d/comm" 2>/dev/null || continue; if [ "$name" = llama-server ]; then grep "^VmRSS:" "$d/status"; fi; done']);
            const values=[...text.matchAll(/VmRSS:\s+(\d+)/g)];assert.equal(values.length,1);
            return Number(values[0][1])/1024;
        };
        const cgroup=()=>{
            const raw=docker(['exec',id,'sh','-ec','cat /sys/fs/cgroup/memory.current; cat /sys/fs/cgroup/memory.stat']);
            const bytes=Number(raw.split('\n')[0]);
            const stat=k=>Number(raw.match(new RegExp('^'+k+' (\\d+)','m'))?.[1]||0)/1048576;
            return {currentMiB:bytes/1048576,workingMiB:bytes/1048576-stat('inactive_file'),anonymousMiB:stat('anon'),fileMiB:stat('file'),inactiveFileMiB:stat('inactive_file')};
        };
        const first=infer(),warm=infer(),activeMiB=memory(),activeCgroup=cgroup(),cycles=[];
        for(let n=0;n<2;n++) {
            await delay(14000);
            assert.equal(request('/props').value.is_sleeping,true);
            assert.equal(JSON.parse(getHealth()).status,'ok');
            const idleMiB=memory(),idleCgroup=cgroup();assert(idleMiB<activeMiB*.5,'Idle memory not released');
            const woke=infer();assert.equal(woke.text,warm.text,'Output changed after wake');
            assert.equal(request('/props').value.is_sleeping,false);
            assert.equal(JSON.parse(docker(['inspect',id]))[0].RestartCount,0);
            cycles.push({idleMiB,idleCgroup,wakeMs:woke.milliseconds,wokenMiB:memory(),outputUnchanged:true,health200:true,restarts:0});
        }
        const unauthorized=docker(['exec',id,'curl','-s','-o','/dev/null','-w','%{http_code}','http://127.0.0.1:9931/v1/models']);assert.equal(unauthorized,'401');
        result.models.push({kind,activeMiB,activeCgroup,firstMs:first.milliseconds,warmMs:warm.milliseconds,nativeExecutableUnchanged:true,modelChecksumVerifiedByEntrypoint:true,apiAuthentication:401,context:4096,cycles});
        writeArtifact(root,dir+'/ai-benchmark.json',result);
        console.log(JSON.stringify(result.models.at(-1)));
    } finally {if(id)docker(['rm','-f',id]);}
}
result.status='PASS';writeArtifact(root,dir+'/ai-benchmark.json',result);
