import fs from 'node:fs';import path from 'node:path';import {createHash} from 'node:crypto';import {fileURLToPath} from 'node:url';
export function javaArtifactDirectory(workspace,requested){
 const root=fs.realpathSync(workspace),destination=path.resolve(root,requested);
 if(!destination.startsWith(root+'/'))throw Error('Java artifacts must stay in the workspace');
 const missing=[];let parent=destination;
 for(;;){try{fs.lstatSync(parent);break;}catch(e){if(e.code!=='ENOENT')throw e;missing.unshift(path.basename(parent));parent=path.dirname(parent);}}
 const canonical=fs.realpathSync(parent);
 if(canonical!==root&&!canonical.startsWith(root+'/'))throw Error('Java artifact parent escapes workspace');
 if(!fs.statSync(canonical).isDirectory())throw Error('Java artifact parent is not a directory');
 return path.join(canonical,...missing);
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
const root=fs.realpathSync('.'),destination=javaArtifactDirectory(root,process.argv[2]||'.local/platform-java-dist');
fs.mkdirSync(destination,{recursive:true});
const arch=process.argv[3]||'aarch64';if(!['aarch64','x64'].includes(arch))throw Error('Unsupported architecture');
const inventory=JSON.parse(fs.readFileSync('scripts/platform/runtime-versions.json'));
for(const pin of inventory.java.filter(x=>x.os==='linux'&&x.arch===arch)) {
 const file=destination+'/'+pin.sha256+'.tar.gz';
 if(!fs.existsSync(file)) {
  let downloaded=false;
  for(const url of [pin.url,pin.url.replace('%2B','+')]) {
   const response=await fetch(url,{signal:AbortSignal.timeout(180000)});if(!response.ok)continue;
   const bytes=Buffer.from(await response.arrayBuffer());if(createHash('sha256').update(bytes).digest('hex')!==pin.sha256)throw Error('Official Temurin checksum mismatch');
   fs.writeFileSync(file,bytes,{flag:'wx',mode:0o600});downloaded=true;break;
  }
  if(!downloaded)throw Error('Official Temurin download unavailable');
 }
 if(createHash('sha256').update(fs.readFileSync(file)).digest('hex')!==pin.sha256)throw Error('Cached Temurin checksum mismatch');
 console.log('Verified official Linux '+arch+' '+pin.type+' '+pin.version+' SHA256='+pin.sha256);
}
}
