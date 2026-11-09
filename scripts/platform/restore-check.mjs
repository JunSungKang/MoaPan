import fs from 'node:fs';
import assert from 'node:assert/strict';
import {execFileSync,spawn} from 'node:child_process';
import {randomBytes} from 'node:crypto';
import {databaseSnapshot} from '../deploy/infrastructure-migration.mjs';
import {transferChildren} from './recovery.mjs';

/** Restore only to a temporary, non-published, tmpfs database; never restore onto production. */
export async function restoreCheck({root,image,backup,expected,signal}) {
  assert(backup.startsWith(root+'/.local/backups/'));assert(fs.statSync(backup).size>0);
  const run=(args,options={})=>{try{return execFileSync('docker',args,{encoding:'utf8',stdio:['pipe','pipe','pipe'],maxBuffer:67108864,...options}).trim();}catch{throw Error('Isolated encrypted restore failed; sensitive diagnostics withheld');}};
  const network='damgox2-restore-check-'+process.pid;let db;
  run(['network','create','--internal',network]);
  try {
    const env={...process.env,POSTGRES_PASSWORD:randomBytes(32).toString('hex'),POSTGRES_APP_PASSWORD:randomBytes(32).toString('hex')};
    db=run(['run','-d','--network',network,'--read-only','--user','postgres','--cap-drop','ALL','--security-opt','no-new-privileges:true','--tmpfs','/var/lib/postgresql:rw,mode=1777,size=512m','--tmpfs','/var/run/postgresql:rw,mode=1777','--tmpfs','/tmp:size=32m,mode=1777','--memory','512m','--cpus','1','-e','PGDATA=/var/lib/postgresql/restore','-e','POSTGRES_USER=damgox2_migrate','-e','POSTGRES_DB=damgox2','-e','POSTGRES_PASSWORD','-e','POSTGRES_APP_PASSWORD',image],{env});
    let ready=false;
    for(let i=0;i<90;i++){signal?.throwIfAborted();try{run(['exec',db,'pg_isready','-q','-U','damgox2_migrate','-d','damgox2']);ready=true;break;}catch{}await new Promise(r=>setTimeout(r,500));}
    assert(ready,'Restore database readiness');
    signal?.throwIfAborted();
    const decrypt=spawn(root+'/.local/bin/age',['--decrypt','-i',root+'/.local/backups/keys/delivery-security-20261108.identity',backup],{stdio:['ignore','pipe','pipe']});
    const restore=spawn('docker',['exec','-i',db,'psql','-X','-U','damgox2_migrate','-d','damgox2','--set','ON_ERROR_STOP=1'],{stdio:['pipe','pipe','pipe']});
    await transferChildren(decrypt,restore,{signal});
    const restored=databaseSnapshot(db,'damgox2_migrate','damgox2');assert.deepEqual(restored,expected,'Restored row counts/content fingerprints differ');
    return {status:'PASS',tables:restored.length,allRowsAndCountsMatch:true,encryptedStreaming:true,plaintextDumpWritten:false,isolatedTmpfs:true};
  } finally {if(db)run(['rm','-f',db]);run(['network','rm',network]);}
}
