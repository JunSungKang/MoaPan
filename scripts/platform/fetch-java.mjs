import fs from 'node:fs';import {createHash} from 'node:crypto';import {execFileSync} from 'node:child_process';
const [arch]=process.argv.slice(2),inventory=JSON.parse(fs.readFileSync('/runtime-versions.json'));
if(!['amd64','arm64'].includes(arch))throw Error('Unsupported target architecture');
for(const type of ['jdk','jre']) {
 const p=inventory.java.find(x=>x.os==='linux'&&x.arch===(arch==='arm64'?'aarch64':'x64')&&x.type===type);
 if(!p)throw Error('Missing official runtime pin');
 fs.mkdirSync('/downloads',{recursive:true});const file='/downloads/'+p.sha256+'.tar.gz';
 const offline='/official-java-input/'+p.sha256+'.tar.gz';
 if(!fs.existsSync(file)&&fs.existsSync(offline))fs.copyFileSync(offline,file);
 if(!fs.existsSync(file)) {
  // Retry the same official artifact with a stable cache key if a stale release redirect returns 504.
  let downloaded=false;
  for(const url of [p.url,p.url.replace('%2B','+'),p.url+'?damgox2_sha256='+p.sha256]) {
   try {execFileSync('curl',['--fail','--silent','--show-error','--location','--proto','=https','--tlsv1.2','--max-time','180','--retry','2','--output',file+'.partial',url]);fs.renameSync(file+'.partial',file);downloaded=true;break;}catch{fs.rmSync(file+'.partial',{force:true});}
  }
  if(!downloaded)throw Error('Official Temurin download unavailable');
 }
 if(createHash('sha256').update(fs.readFileSync(file)).digest('hex')!==p.sha256)throw Error('Temurin checksum mismatch');
 const dir='/opt/java/'+type;fs.mkdirSync(dir,{recursive:true});execFileSync('tar',['--no-same-owner','-xzf',file,'-C',dir,'--strip-components=1']);
}
