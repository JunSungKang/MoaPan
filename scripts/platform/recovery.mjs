import {pipeline} from 'node:stream/promises';
import fs from 'node:fs';

export function legacyWorkerSafe(query,legacy){
  const literals={old_provider:legacy.translationProvider,old_translation_policy:legacy.translationPolicy,old_classification_model:legacy.classificationPolicy};
  for(const value of Object.values(literals))if(typeof value!=='string'||!value||value.length>160)throw Error('Reviewed legacy identity required');
  const template=fs.readFileSync(new URL('./legacy-worker-safe.sql',import.meta.url),'utf8');
  const sql=template.replace(/:([a-z_]+)/g,(_,name)=>{if(!(name in literals))throw Error('Unknown recovery bind');return "'"+literals[name].replaceAll("'","''")+"'";});
  return query(sql)==='0';
}

/** Preserve the attempt even if the remote commit succeeds but its acknowledgement is lost. */
export class ReviewedPolicyChange {
  attempted=false;
  apply(action){this.attempted=true;return action();}
  rollback(reverse,verify){
    if(!this.attempted)return;
    // The reverse acknowledgement may also be lost; the verified DB state is authoritative.
    try {reverse();} catch { /* Verification below must still prove the old policy is safe. */ }
    if(!verify())throw Error('Policy rollback is not verified; old worker must remain stopped');
    this.attempted=false;
  }
}

/** Supervise both sides, including early SQL failure, EPIPE, timeout and failed exec. */
export async function transferChildren(producer,consumer,{timeoutMs=120000,killGraceMs=1000,signal}={}) {
  const observe=child=>new Promise(resolve=>{
    child.once('error',()=>resolve({code:-1}));child.once('close',code=>resolve({code}));
  });
  const exits=[observe(producer),observe(consumer)],allClosed=Promise.all(exits);
  producer.stderr?.resume();consumer.stderr?.resume();consumer.stdout?.resume();
  let deadline,killDeadline,cleanupDeadline,abort;
  try {
    await Promise.race([
      Promise.all([...exits.map(p=>p.then(({code})=>{if(code!==0)throw Error('Stream child failed');})),pipeline(producer.stdout,consumer.stdin)]),
      new Promise((_,reject)=>{deadline=setTimeout(()=>reject(Error('Stream transfer deadline')),timeoutMs);}),
      new Promise((_,reject)=>{abort=()=>reject(Error('Stream transfer cancelled'));if(signal?.aborted)abort();else signal?.addEventListener('abort',abort,{once:true});})
    ]);
  } catch {
    producer.stdout?.destroy();consumer.stdin?.destroy();
    for(const child of [producer,consumer])child.kill('SIGTERM');
    killDeadline=setTimeout(()=>{for(const child of [producer,consumer])child.kill('SIGKILL');},killGraceMs);
    await Promise.race([allClosed,new Promise(resolve=>{cleanupDeadline=setTimeout(resolve,killGraceMs+1000);})]);
    for(const child of [producer,consumer])child.kill('SIGKILL');
    throw Error('Encrypted stream transfer failed; children terminated and sensitive diagnostics withheld');
  } finally {clearTimeout(deadline);clearTimeout(killDeadline);clearTimeout(cleanupDeadline);signal?.removeEventListener('abort',abort);}
}
