// Explicit, offline volume migration. Never stops services or deletes source data.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';

export const volumeSuffixes=Object.freeze(['caddy_config','caddy_data','classification_models','database','database_v1','public_caddy_config','public_caddy_data','translation_models']);
export function volumePlan(names,containers){
  const pairs=volumeSuffixes.map(s=>({source:'moapan_'+s,target:'damgox2_'+s,suffix:s}));
  for(const p of pairs){
    if(!names.includes(p.source))throw Error('Missing source volume: '+p.source);
    if(names.includes(p.target))throw Error('Destination already exists: '+p.target);
    if(containers.some(c=>c.State.Running&&(c.Mounts||[]).some(m=>m.Type==='volume'&&m.Name===p.source)))throw Error('Source volume is in use: '+p.source);
  }
  return pairs;
}
export function equalSnapshots(before,after){
  if(JSON.stringify(before)!==JSON.stringify(after))throw Error('Data fingerprint mismatch; do not remove source resources');
  return true;
}
export function evidenceDestination(root,output){
  const canonical=fs.realpathSync(root),local=fs.realpathSync(path.join(canonical,'.local'));
  if(local!==path.join(canonical,'.local'))throw Error('Workspace .local must not redirect outside its root');
  const requested=path.resolve(canonical,output||'');
  if(!requested.startsWith(local+'/'))throw Error('Evidence must be inside workspace .local/');
  const parent=fs.realpathSync(path.dirname(requested));
  if(parent!==local&&!parent.startsWith(local+'/'))throw Error('Evidence parent escapes workspace .local/');
  const destination=path.join(parent,path.basename(requested));
  try{fs.lstatSync(destination);throw Error('Evidence already exists');}
  catch(error){if(error.code!=='ENOENT')throw error;}
  return destination;
}
const docker=(...args)=>{
  try{return execFileSync('docker',args,{encoding:'utf8',stdio:['pipe','pipe','pipe'],maxBuffer:64*1024*1024});}
  catch{throw Error('Docker migration operation failed; output withheld to protect database rows and credentials');}
};
export function databaseSnapshot(container,user,database){
  const sql=s=>docker('exec',container,'psql','-X','-U',user,'-d',database,'-At','--set','ON_ERROR_STOP=1','-c',s).trim();
  const tables=sql("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename").split('\n').filter(Boolean);
  return tables.map(table=>{
    if(!/^[a-z_][a-z_0-9]*$/.test(table))throw Error('Unexpected table identifier');
    const rows=sql(`COPY (SELECT to_jsonb(t)::text FROM public.${table} t ORDER BY to_jsonb(t)::text COLLATE "C") TO STDOUT`);
    return {table,count:Number(sql('SELECT count(*) FROM public.'+table)),sha256:createHash('sha256').update(rows).digest('hex')};
  });
}
export function copyVolumes(image){
  if(!/^sha256:[a-f0-9]{64}$/.test(image))throw Error('Use a verified immutable utility image ID');
  const names=docker('volume','ls','--format','{{.Name}}').trim().split('\n');
  const ids=docker('ps','-aq').trim().split('\n').filter(Boolean);
  const containers=ids.length?JSON.parse(docker('inspect',...ids)):[];
  const plan=volumePlan(names,containers),results=[];
  for(const p of plan){
    docker('volume','create','--label','com.docker.compose.project=damgox2','--label','com.docker.compose.volume='+p.suffix,p.target);
    const common=['run','--rm','--network','none','--read-only','--user','0:0','--entrypoint','sh','--cap-drop','ALL','--cap-add','CHOWN','--cap-add','DAC_OVERRIDE','--cap-add','FOWNER','--security-opt','no-new-privileges:true'];
    docker(...common,'-v',p.source+':/from:ro','-v',p.target+':/to',image,'-euc','set -o pipefail; test -z "$(ls -A /to)"; tar cpf - -C /from . | tar xpf - -C /to');
    const manifest='cd /data; find . -xdev -print | LC_ALL=C sort | while IFS= read -r f; do printf "%s " "$f"; stat -c "%f:%u:%g" "$f"; if [ -L "$f" ]; then readlink "$f"; elif [ -f "$f" ]; then sha256sum "$f"; fi; done';
    const fingerprint=name=>createHash('sha256').update(docker(...common,'-v',name+':/data:ro',image,'-euc',manifest)).digest('hex');
    const source=fingerprint(p.source),target=fingerprint(p.target);
    if(source!==target)throw Error('Volume copy verification failed: '+p.suffix+'; sources preserved');
    results.push({...p,sha256:source});
  }
  return results;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  const root=fs.realpathSync(path.join(path.dirname(fileURLToPath(import.meta.url)),'../..'));
  const [image,output]=process.argv.slice(2),destination=evidenceDestination(root,output);
  fs.writeFileSync(destination,JSON.stringify({verifiedAt:new Date().toISOString(),volumes:copyVolumes(image)},null,2)+'\n',{mode:0o600,flag:'wx'});
  console.log('All 8 offline volume copies verified; source data retained.');
}
