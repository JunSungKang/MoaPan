import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {spawnSync} from 'node:child_process';
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {resolveKeyDirectory} from '../deploy/prepare-email-key.mjs';

const root=fs.realpathSync(fileURLToPath(new URL('../../',import.meta.url)));
function fixture(run){
  const output=path.join(root,'build');
  try{const stat=fs.lstatSync(output);if(!stat.isDirectory()||stat.isSymbolicLink())throw new Error('Unsafe test output path');}
  catch(error){if(error.code!=='ENOENT')throw error;fs.mkdirSync(output);}
  const dir=fs.mkdtempSync(path.join(output,'email-key-test-'));try{run(dir);}finally{fs.rmSync(dir,{recursive:true,force:true});}
}
function invoke(dir){return spawnSync(process.execPath,['scripts/deploy/prepare-email-key.mjs','--directory',path.relative(root,dir)],{cwd:root,encoding:'utf8'});}

test('a new key is canonical 32-byte base64, readable in its private directory and never printed',()=>fixture(parent=>{
  const dir=path.join(parent,'new'),result=invoke(dir);assert.equal(result.status,0,result.stderr);
  const encoded=fs.readFileSync(path.join(dir,'data.key'),'utf8').trim();assert.equal(Buffer.from(encoded,'base64').length,32);assert.equal(Buffer.from(encoded,'base64').toString('base64'),encoded);
  assert.equal(fs.statSync(dir).mode&0o777,0o700);assert.equal(fs.statSync(path.join(dir,'data.key')).mode&0o777,0o444);assert(!result.stdout.includes(encoded));
}));
test('repeated preparation preserves the exact existing encryption key',()=>fixture(parent=>{
  const dir=path.join(parent,'key');assert.equal(invoke(dir).status,0);const before=fs.readFileSync(path.join(dir,'data.key'));assert.equal(invoke(dir).status,0);assert.deepEqual(fs.readFileSync(path.join(dir,'data.key')),before);
}));
test('an invalid existing key fails without replacement or disclosure',()=>fixture(dir=>{
  const file=path.join(dir,'data.key');fs.writeFileSync(file,'synthetic-invalid-key');const before=fs.readFileSync(file),result=invoke(dir);assert.notEqual(result.status,0);assert.deepEqual(fs.readFileSync(file),before);assert(!result.stderr.includes('synthetic-invalid-key'));
}));
test('workspace escape is rejected by the pure resolver before any filesystem mutation',()=>{
  assert.throws(()=>resolveKeyDirectory('../outside-workspace'));assert.throws(()=>resolveKeyDirectory(root));
});
test('symbolic links cannot redirect key directories or key files',()=>fixture(parent=>{
  const target=path.join(parent,'target');fs.mkdirSync(target,{mode:0o700});const alias=path.join(parent,'alias');fs.symlinkSync(target,alias);assert.notEqual(invoke(alias).status,0);assert.equal(fs.readdirSync(target).length,0);
  const secret=path.join(parent,'existing');fs.writeFileSync(secret,Buffer.alloc(32,7).toString('base64'));fs.symlinkSync(secret,path.join(target,'data.key'));const before=fs.readFileSync(secret);assert.notEqual(invoke(target).status,0);assert.deepEqual(fs.readFileSync(secret),before);
}));
test('a restrictive operator umask still produces a readable container secret in a private directory',()=>fixture(parent=>{
  const dir=path.join(parent,'key'),script=path.join(root,'scripts/deploy/prepare-email-key.mjs');
  const boot='process.umask(0o077);process.argv=[process.execPath,'+JSON.stringify(script)+',"--directory",'+JSON.stringify(path.relative(root,dir))+'];await import('+JSON.stringify(pathToFileURL(script).href)+');';
  const result=spawnSync(process.execPath,['--input-type=module','-e',boot],{cwd:root,encoding:'utf8'});assert.equal(result.status,0,result.stderr);assert.equal(fs.statSync(dir).mode&0o777,0o700);assert.equal(fs.statSync(path.join(dir,'data.key')).mode&0o777,0o444);
}));
test('hard-linked key files are rejected without changing the linked file',()=>fixture(parent=>{
  const target=path.join(parent,'key');fs.mkdirSync(target,{mode:0o700});const original=path.join(parent,'original');fs.writeFileSync(original,Buffer.alloc(32,7).toString('base64'));fs.linkSync(original,path.join(target,'data.key'));const before=fs.readFileSync(original),mode=fs.statSync(original).mode;assert.notEqual(invoke(target).status,0);assert.deepEqual(fs.readFileSync(original),before);assert.equal(fs.statSync(original).mode,mode);
}));
