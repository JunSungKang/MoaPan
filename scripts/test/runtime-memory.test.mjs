import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {execFileSync} from 'node:child_process';
import {memoryOverride,certificateIdentity,requestedMounts} from '../performance/deploy.mjs';
import {artifactPath,writeArtifact} from '../performance/artifacts.mjs';

function launch(role,overrides={}) {
    const root=fs.realpathSync('.'),parent=root+'/.local/tmp';
    fs.mkdirSync(parent,{recursive:true});
    const dir=fs.mkdtempSync(parent+'/memory-entrypoint-');
    try {
        const probe=`#!/bin/sh\nexec "$DAMGOX2_PROBE_NODE" -e 'console.log(JSON.stringify({args:process.argv.slice(1),java:process.env.JAVA_TOOL_OPTIONS,max:process.env.DAMGOX2_DB_POOL_MAX,min:process.env.DAMGOX2_DB_POOL_MIN,arenas:process.env.MALLOC_ARENA_MAX}))' -- "$@"\n`;
        fs.writeFileSync(dir+'/java',probe,{mode:0o755});
        return JSON.parse(execFileSync('sh',['scripts/performance/java-entrypoint.sh','--synthetic-argument=a b'],{encoding:'utf8',env:{...process.env,PATH:dir+':'+process.env.PATH,DAMGOX2_PROBE_NODE:process.execPath,DAMGOX2_ROLE:role,JAVA_TOOL_OPTIONS:'',DAMGOX2_DB_POOL_MAX:'',DAMGOX2_DB_POOL_MIN:'',MALLOC_ARENA_MAX:'',...overrides}}));
    } finally { fs.rmSync(dir,{recursive:true}); }
}
test('separate role budgets keep nested-transaction and scheduler connection headroom',()=>{
    const web=launch('web'),worker=launch('worker');
    assert.match(web.java,/-Xmx256m/);assert.match(worker.java,/-Xmx192m/);
    assert.equal(web.max,'8');assert.equal(web.min,'2');
    assert.equal(worker.max,'6');assert.equal(worker.min,'1');
    assert.match(web.java,/-XX:\+UseCompactObjectHeaders/);
    assert.deepEqual(worker.args,['-jar','/app/damgox2.jar','--synthetic-argument=a b']);
    assert.equal(web.arenas,'2');
});
test('explicit operator budgets and JVM choices are preserved without shell evaluation',()=>{
    const r=launch('worker',{JAVA_TOOL_OPTIONS:'-Xmx320m -Dsynthetic.value=$(false)',DAMGOX2_DB_POOL_MAX:'10',DAMGOX2_DB_POOL_MIN:'3',MALLOC_ARENA_MAX:'4'});
    assert.equal(r.java,'-Xmx320m -Dsynthetic.value=$(false)');assert.equal(r.max,'10');assert.equal(r.min,'3');assert.equal(r.arenas,'4');
});
test('deployment override has only immutable app images and public resource controls',()=>{
    assert.throws(()=>memoryOverride('latest'));
    const value=memoryOverride('sha256:'+'a'.repeat(64));
    assert.deepEqual(Object.keys(value.services),['damgox2-web','damgox2-worker']);
    for(const service of Object.values(value.services))assert.deepEqual(Object.keys(service.environment),['JAVA_TOOL_OPTIONS','DAMGOX2_DB_POOL_MAX','DAMGOX2_DB_POOL_MIN']);
    assert.equal(value.services['damgox2-web'].image,value.services['damgox2-worker'].image);
    const both=memoryOverride('sha256:'+'a'.repeat(64),'sha256:'+'b'.repeat(64));
    assert.equal(Object.keys(both.services).length,4);
    assert.deepEqual(both.services['damgox2-translator'].environment,{DAMGOX2_AI_IDLE_SECONDS:'300'});
    assert.equal(both.services['damgox2-translator'].image,both.services['damgox2-classifier'].image);
    assert.throws(()=>memoryOverride('sha256:'+'a'.repeat(64),'latest'));
});
test('AI idle input rejects overflow and shell syntax before any secret or model access',()=>{
    for(const kind of ['translation','classification'])for(const idle of ['-2','0','86401','not-a-number','2;exit 0','$(exit 0)']){
        let failed=false;
        try{execFileSync('sh',['scripts/'+kind+'/serve.sh'],{encoding:'utf8',stdio:'pipe',env:{...process.env,DAMGOX2_AI_IDLE_SECONDS:idle,TRANSLATION_THREADS:'2',CLASSIFICATION_THREADS:'2'}});}catch(e){failed=true;assert(!e.stdout?.toString().includes('key missing'));}
        assert(failed,'Invalid idle period must not start the model');
    }
});
test('trust identity uses certificate contents independently of store salt and order',()=>{
    const root=fs.realpathSync('.'),parent=root+'/.local/tmp';fs.mkdirSync(parent,{recursive:true});
    const dir=fs.mkdtempSync(parent+'/memory-ca-test-');
    try {
        const cert=name=>{execFileSync('openssl',['req','-x509','-newkey','ec','-pkeyopt','ec_paramgen_curve:P-256','-nodes','-keyout',dir+'/'+name+'.key','-out',dir+'/'+name+'.pem','-days','1','-subj','/CN=Synthetic-'+name],{stdio:'pipe',env:{...process.env,RANDFILE:dir+'/random-state',TMPDIR:dir}});return fs.readFileSync(dir+'/'+name+'.pem','utf8');};
        const a=cert('A'),b=cert('B');const first=(a+b).repeat(25),otherOrder=(b+a).repeat(25);
        assert.equal(certificateIdentity(first),certificateIdentity(otherOrder));
        assert.notEqual(certificateIdentity(first),certificateIdentity(a.repeat(50)));
        assert.throws(()=>certificateIdentity('unrecognized store'));
    } finally {fs.rmSync(dir,{recursive:true});}
});
test('deployment compares backing volume and secret paths as well as read-only policy',()=>{
    const config={volumes:{models:{name:'synthetic-models'}},secrets:{key:{file:'/synthetic/key'}}};
    const a={volumes:[{type:'volume',source:'models',target:'/models',read_only:true}],secrets:[{source:'key',target:'key'}]};
    assert.deepEqual(requestedMounts(a,config),[{type:'volume',source:'synthetic-models',target:'/models',readOnly:true},{type:'bind',source:'/synthetic/key',target:'/run/secrets/key',readOnly:true}]);
    assert.notDeepEqual(requestedMounts(a,config),requestedMounts(a,{...config,secrets:{key:{file:'/different/key'}}}));
});
test('artifact directory and leaf symlinks are rejected before creating or overwriting files',()=>{
    const parent=fs.realpathSync('.')+'/.local/tmp';fs.mkdirSync(parent,{recursive:true});const dir=fs.mkdtempSync(parent+'/memory-boundary-');
    try {
        const root=dir+'/workspace',outside=dir+'/outside';fs.mkdirSync(root+'/.local',{recursive:true});fs.mkdirSync(outside);
        fs.writeFileSync(outside+'/keep','preserved');fs.symlinkSync(outside,root+'/.local/escape');fs.symlinkSync(outside+'/keep',root+'/.local/leaf');
        assert.throws(()=>artifactPath(root,'.local/escape/new/report.json'));assert(!fs.existsSync(outside+'/new'));
        assert.throws(()=>writeArtifact(root,'.local/leaf',{wrong:true}));assert.equal(fs.readFileSync(outside+'/keep','utf8'),'preserved');
        writeArtifact(root,'.local/valid/report.json',{ok:true});assert.deepEqual(JSON.parse(fs.readFileSync(root+'/.local/valid/report.json')),{ok:true});
    } finally {fs.rmSync(dir,{recursive:true});}
});
