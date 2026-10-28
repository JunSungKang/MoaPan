import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
const root=fs.realpathSync(new URL('..',import.meta.url));
function run(fake){
  const base=path.join(root,'.local/tmp');
  assert(base.startsWith(root+path.sep));fs.mkdirSync(base,{recursive:true});
  const dir=fs.mkdtempSync(path.join(base,'local-start-test-'));
  try{
    fs.writeFileSync(path.join(dir,'docker'),'#!'+process.execPath+'\n'+fake,{mode:0o700});
    return spawnSync(process.execPath,[path.join(root,'scripts/local-start.mjs')],{cwd:root,env:{...process.env,PATH:dir+path.delimiter+process.env.PATH},encoding:'utf8',timeout:15000,killSignal:'SIGKILL'});
  }finally{fs.rmSync(dir,{recursive:true,force:true});}
}
test('unresponsive engine terminates preflight and never invokes Compose',()=>{
 const start=Date.now();const result=run("if(process.argv[2]==='info')setTimeout(()=>{},12000);else{console.log('COMPOSE_INVOKED');process.exit(0)}");
 assert.equal(result.error,undefined);assert.equal(result.status,2);assert.match(result.stderr,/10-second preflight failed/);assert.doesNotMatch(result.stdout,/COMPOSE_INVOKED/);assert(Date.now()-start<14000);
});
test('Compose failure is returned rather than reported as healthy',()=>{
 const result=run("if(process.argv[2]==='info')console.log('synthetic-engine');else process.exit(7)");assert.equal(result.status,7);
});
test('healthy engine and Compose return success',()=>{
 const result=run("if(process.argv[2]==='info')console.log('synthetic-engine');else if(!process.argv.includes('--wait'))process.exit(8)");assert.equal(result.status,0);
});
