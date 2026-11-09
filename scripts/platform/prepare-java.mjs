import fs from 'node:fs';import path from 'node:path';import {createHash} from 'node:crypto';
const root=fs.realpathSync('.'),destination=path.resolve(process.argv[2]||'.local/platform-java-dist');
if(!destination.startsWith(root+'/'))throw Error('Java artifacts must stay in the workspace');
fs.mkdirSync(destination,{recursive:true});if(!fs.realpathSync(destination).startsWith(root+'/'))throw Error('Java artifact destination escapes workspace');
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
