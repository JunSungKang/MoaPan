import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {execFileSync,spawnSync} from 'node:child_process';
import {frozenConfigurations,serializeConfiguration,FrozenDeployment} from '../performance/deployment-state.mjs';
import {memoryOverride} from '../performance/deploy.mjs';
import {artifactPath} from '../performance/artifacts.mjs';

const roles=['web','worker','translator','classifier'];
function fixture() {
    const config={services:{},networks:{},volumes:{},secrets:{}},current={};
    for(const role of roles){const name='damgox2-'+role;config.services[name]={image:'sha256:'+'a'.repeat(64),environment:{DECLARED:'before'},network_mode:'none'};current[role]={Image:'sha256:'+'a'.repeat(64),Config:{Env:['DECLARED=before','OPERATOR_ONLY=synthetic$value$$literal','SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=10000']}};}
    return {config,current};
}
test('F1: preserves container-only ENV and custom JVM options through Compose serialization',()=>{
    const {config,current}=fixture();current.web.Config.Env.push('JAVA_TOOL_OPTIONS=-Xmx320m -Dcustom=value');
    const snapshots=frozenConfigurations(config,current,memoryOverride('sha256:'+'b'.repeat(64),'sha256:'+'c'.repeat(64)));
    for(const state of [snapshots.before,snapshots.after])for(const s of Object.values(state.services)){assert.equal(s.environment.OPERATOR_ONLY,'synthetic$value$$literal');assert.equal(s.environment.SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT,'10000');}
    assert.equal(snapshots.after.services['damgox2-web'].environment.JAVA_TOOL_OPTIONS,'-Xmx320m -Dcustom=value');
    const serialized=JSON.parse(serializeConfiguration(snapshots.after));assert.equal(serialized.services['damgox2-web'].environment.OPERATOR_ONLY,'synthetic$$value$$$$literal');
});
test('F2: recovery never reads changed or unavailable shared config after partial replacement',()=>{
    const {config,current}=fixture(),state=frozenConfigurations(config,current,memoryOverride('sha256:'+'b'.repeat(64),'sha256:'+'c'.repeat(64)));
    let available=true;const running=structuredClone(state.before.services);
    const transition=new FrozenDeployment({...state,expectedHash:'original',readHash:()=>{if(!available)throw Error('Shared file unreadable');return 'original';},execute:(snapshot,role)=>{running['damgox2-'+role]=structuredClone(snapshot.services['damgox2-'+role]);}});
    for(const role of roles)delete running['damgox2-'+role]; // all four have been stopped
    transition.apply('web');available=false;
    assert.throws(()=>transition.apply('translator'));
    config.services['damgox2-web'].environment.DECLARED='changed-on-disk';
    for(const role of roles)transition.restore(role);
    assert.deepEqual(running,state.before.services);
});
test('Docker failure injection restores all four synthetic services without shared config', {timeout:90000},()=>{
    const project='damgox2-frozen-fixture-'+process.pid,names=roles.map(r=>project+'-'+r);
    const call=(args,input)=>execFileSync('docker',args,{input,encoding:'utf8',stdio:['pipe','pipe','pipe'],timeout:30000});
    const pin=JSON.parse(fs.readFileSync('scripts/platform/runtime-versions.json')).ubuntu.image;
    try{call(['image','inspect',pin]);}catch{call(['pull',pin]);}
    const image=JSON.parse(call(['image','inspect',pin]))[0].Id;
    const configuration={name:project,services:{},networks:{},volumes:{},secrets:{}};
    for(const [index,role]of roles.entries())configuration.services['damgox2-'+role]={image,container_name:names[index],command:['sleep','300'],environment:{DECLARED:'before'},network_mode:'none',read_only:true,user:'10001:10001',cap_drop:['ALL'],security_opt:['no-new-privileges:true'],mem_limit:32*1024*1024,pids_limit:8};
    const initial=structuredClone(configuration);
    for(const s of Object.values(initial.services))s.environment.OPERATOR_ONLY='synthetic$value$$literal';
    const execute=(snapshot,role)=>call(['compose','--env-file','/dev/null','-p',project,'-f','-','up','-d','--no-build','--no-deps','--pull','never','damgox2-'+role],serializeConfiguration(snapshot));
    let directory;
    try {
        const parent=artifactPath(fs.realpathSync('.'),'.local/tmp/frozen-config-control');
        directory=fs.mkdtempSync(parent+'-');
        const shared=directory+'/compose.json';fs.writeFileSync(shared,serializeConfiguration(configuration));
        for(const role of roles)execute(initial,role);
        const current=Object.fromEntries(roles.map((r,index)=>[r,JSON.parse(call(['inspect',names[index]]))[0]]));
        const state=frozenConfigurations(configuration,current,memoryOverride(image,image));
        const original=fs.readFileSync(shared,'utf8');
        const transition=new FrozenDeployment({...state,expectedHash:original,readHash:()=>fs.readFileSync(shared,'utf8'),execute});
        call(['stop','--timeout','1',...names]);
        transition.apply('web');fs.writeFileSync(shared,'changed after web replacement');
        assert.throws(()=>transition.apply('translator'));
        fs.unlinkSync(shared); // the shared file is now completely unavailable
        for(const role of roles)transition.restore(role);
        assert(!fs.existsSync(shared));
        for(const [index,role]of roles.entries()){
            const c=JSON.parse(call(['inspect',names[index]]))[0];assert(c.State.Running);
            assert.equal(c.Image,current[role].Image);
            assert.deepEqual([...c.Config.Env].sort(),[...current[role].Config.Env].sort());
            assert.deepEqual(c.Config.Cmd,current[role].Config.Cmd);
            assert(c.Config.Env.includes('OPERATOR_ONLY=synthetic$value$$literal'));
        }
    } finally {for(const name of names)spawnSync('docker',['rm','-f',name],{stdio:'pipe',timeout:15000});if(directory)fs.rmSync(directory,{recursive:true});}
});
