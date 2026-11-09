import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import {spawn} from 'node:child_process';
import {transferChildren,ReviewedPolicyChange} from '../platform/recovery.mjs';
import {javaArtifactDirectory} from '../platform/prepare-java.mjs';
const child=script=>spawn(process.execPath,['-e',script],{stdio:['pipe','pipe','pipe']});
test('encrypted stream success requires both child exits and complete pipe',async()=>{
 const p=child('process.stdout.end("synthetic verified stream")'),c=child('let s="";process.stdin.on("data",x=>s+=x);process.stdin.on("end",()=>process.exit(s==="synthetic verified stream"?0:1))');
 await transferChildren(p,c,{timeoutMs:3000});assert.equal(p.exitCode,0);assert.equal(c.exitCode,0);
});
test('early SQL error terminates a backpressured producer and returns bounded failure',async()=>{
 const p=child('const b=Buffer.alloc(1048576);function send(){while(process.stdout.write(b)){} }process.stdout.on("drain",send);send()'),c=child('process.exit(9)');
 const started=Date.now();await assert.rejects(transferChildren(p,c,{timeoutMs:3000,killGraceMs:50}),/transfer failed/);assert(Date.now()-started<2500);assert(p.exitCode!==null||p.signalCode!==null);
});
test('stalled transfer times out and reaps both children',async()=>{
 const p=child('setInterval(()=>{},1000)'),c=child('process.stdin.resume();setInterval(()=>{},1000)');
 await assert.rejects(transferChildren(p,c,{timeoutMs:200,killGraceMs:50}),/transfer failed/);assert(p.exitCode!==null||p.signalCode!==null);assert(c.exitCode!==null||c.signalCode!==null);
});
test('cancellation terminates both stream children without waiting for the deadline',async()=>{
 const p=child('setInterval(()=>{},1000)'),c=child('process.stdin.resume();setInterval(()=>{},1000)'),controller=new AbortController();
 const transfer=transferChildren(p,c,{signal:controller.signal,timeoutMs:3000,killGraceMs:50});controller.abort();
 await assert.rejects(transfer,/transfer failed/);assert(p.exitCode!==null||p.signalCode!==null);assert(c.exitCode!==null||c.signalCode!==null);
});
test('lost commit acknowledgement still reverses and verifies before old worker start',()=>{
 const change=new ReviewedPolicyChange();let actual='java27',started=false;
 assert.throws(()=>change.apply(()=>{actual='java25';throw Error('lost acknowledgement');}));
 change.rollback(()=>{actual='java27';},()=>actual==='java27');started=true;assert.equal(started,true);assert.equal(actual,'java27');
});
test('unverified or failed reverse prevents old worker start',()=>{
 for(const reverse of [()=>{},()=>{throw Error('lost reverse acknowledgement');}]){
  const change=new ReviewedPolicyChange();let actual='java27',started=false;change.apply(()=>{actual='java25';});
  assert.throws(()=>{change.rollback(reverse,()=>actual==='java27');started=true;});assert.equal(started,false);
 }
});
test('lost reverse acknowledgement permits recovery only when DB state proves reversal',()=>{
 const change=new ReviewedPolicyChange();let actual='java27';change.apply(()=>{actual='java25';});
 change.rollback(()=>{actual='java27';throw Error('lost reverse acknowledgement');},()=>actual==='java27');
 assert.equal(change.attempted,false);assert.equal(actual,'java27');
});
test('a symlink parent is rejected before any outside directory is created',()=>{
 const root=fs.realpathSync('.');fs.mkdirSync(root+'/.local/tmp',{recursive:true});const dir=fs.mkdtempSync(root+'/.local/tmp/java-path-');
 try{
  fs.mkdirSync(dir+'/scope');fs.mkdirSync(dir+'/outside');fs.symlinkSync(dir+'/outside',dir+'/scope/escape','dir');
  assert.throws(()=>javaArtifactDirectory(dir+'/scope','escape/new/deep'),/escapes workspace/);assert(!fs.existsSync(dir+'/outside/new'));
  assert.equal(javaArtifactDirectory(dir+'/scope','safe/deep'),dir+'/scope/safe/deep');assert(!fs.existsSync(dir+'/scope/safe'));
 }finally{fs.rmSync(dir,{recursive:true});}
});
