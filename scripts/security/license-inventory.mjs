// Resolve license declarations from the actual runtime SBOM and primary Maven Central POMs.
// Maven is not used as a build tool. Unknown/dual license declarations require review.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
const root=fs.realpathSync(process.cwd());
const destination=path.resolve(root,'build/reports/security/licenses.json');
if(!destination.startsWith(root+'/'))throw Error('Output outside workspace');
const bom=JSON.parse(fs.readFileSync('build/reports/sbom.cdx.json','utf8'));
const cache=new Map();
const plain=s=>s.replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g,'$1').replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>').trim();
const field=(s,key)=>plain(s.match(new RegExp('<'+key+'>([\\s\\S]*?)</'+key+'>'))?.[1]??'');
async function declarations(group,name,version,depth=0){
 if(depth>6||![group,name,version].every(s=>typeof s==='string'&&/^[a-zA-Z0-9_.-]+$/.test(s)))return {licenses:[],error:'unresolved_parent'};
 const coordinate=group+':'+name+':'+version;
 if(cache.has(coordinate))return cache.get(coordinate);
 const pending=(async()=>{
  const url='https://repo.maven.apache.org/maven2/'+group.replaceAll('.','/')+'/'+name+'/'+version+'/'+name+'-'+version+'.pom';
  try{
   const response=await fetch(url,{redirect:'error',signal:AbortSignal.timeout(20000)});
   if(!response.ok)return {url,licenses:[],error:'http_'+response.status};
   const bytes=Buffer.from(await response.arrayBuffer());if(bytes.length>1048576)throw Error('metadata_oversized');
   const xml=bytes.toString('utf8');
   const section=xml.match(/<licenses>([\s\S]*?)<\/licenses>/)?.[1]??'';
   const licenses=[...section.matchAll(/<license>([\s\S]*?)<\/license>/g)].map(m=>({name:field(m[1],'name'),url:field(m[1],'url')}));
   const evidence={url,pomSha256:createHash('sha256').update(bytes).digest('hex')};
   if(licenses.length)return {...evidence,licenses};
   const parent=xml.match(/<parent>([\s\S]*?)<\/parent>/)?.[1];
   if(!parent)return {...evidence,licenses:[],error:'license_not_declared'};
   const inherited=await declarations(field(parent,'groupId'),field(parent,'artifactId'),field(parent,'version'),depth+1);
   return {...evidence,licenses:inherited.licenses,parent:inherited};
  }catch{return {url,licenses:[],error:'metadata_lookup_failed'};}
 })();cache.set(coordinate,pending);return pending;
}
const components=[];
for(let i=0;i<bom.components.length;i+=4){
 components.push(...await Promise.all(bom.components.slice(i,i+4).map(async c=>{
  const declared=await declarations(c.group,c.name,c.version);
  return {group:c.group,name:c.name,version:c.version,jarSha256:c.hashes?.find(h=>h.alg==='SHA-256')?.content,...declared,
   status:declared.licenses.length?'DECLARED_REQUIRES_REVIEW':'UNKNOWN'};
 })));
}
if(process.argv.includes('--collect-notices')){
 for(const component of components){
  const base=path.join(root,'.local/gradle-home/caches/modules-2/files-2.1',component.group,component.name,component.version);
  const candidates=fs.readdirSync(base).map(h=>path.join(base,h,component.name+'-'+component.version+'.jar')).filter(p=>fs.existsSync(p));
  const jar=candidates.find(p=>createHash('sha256').update(fs.readFileSync(p)).digest('hex')===component.jarSha256);
  if(!jar)throw Error('No verified runtime JAR: '+component.name);
  const entries=execFileSync('unzip',['-Z1',jar],{encoding:'utf8'}).split('\n').filter(s=>/^META-INF\/(?:LICENSE|NOTICE|COPYING)(?:[._-][a-zA-Z0-9_.-]+)?$/i.test(s));
  component.embeddedNotices=[];
  for(const entry of entries){
   const bytes=execFileSync('unzip',['-p',jar,entry],{maxBuffer:2097152});
   const target=path.resolve(root,'licenses/java',component.group,component.name,component.version,path.basename(entry));
   if(!target.startsWith(root+'/licenses/java/'))throw Error('Invalid notice destination');
   fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,bytes);
   component.embeddedNotices.push({entry,path:path.relative(root,target),sha256:createHash('sha256').update(bytes).digest('hex')});
  }
  // Exact-version discrepancy: retain the POM declaration, alongside primary tag/JAR evidence.
  if(component.group==='org.springframework.session'&&['spring-session-core','spring-session-jdbc'].includes(component.name)&&component.version==='4.1.1'){
   const entry=component.embeddedNotices.find(x=>/LICENSE/i.test(x.entry));
   if(!entry||!fs.readFileSync(entry.path,'utf8').includes('Apache License'))throw Error('Unresolved Spring Session license discrepancy');
   component.review={resolvedLicense:'Apache-2.0',metadataDiscrepancy:true,reason:'POM says Broadcom Foundation License; exact-version JAR LICENSE and official4.1.1 source tag both say Apache2.0',source:'https://raw.githubusercontent.com/spring-projects/spring-session/4.1.1/LICENSE.txt'};
  }
 }
}
fs.mkdirSync(path.dirname(destination),{recursive:true});
fs.writeFileSync(destination,JSON.stringify({checkedAt:new Date().toISOString(),scope:'runtimeClasspath; declared metadata, not a legal opinion or a complete OS license audit',components},null,2)+'\n');
console.log('Runtime license declarations:',components.length,'unknown:',components.filter(c=>c.status==='UNKNOWN').length);
if(components.some(c=>c.status==='UNKNOWN'))process.exitCode=2;
