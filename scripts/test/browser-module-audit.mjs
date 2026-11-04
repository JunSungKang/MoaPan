import fs from 'node:fs';
import path from 'node:path';
const root=fs.realpathSync(new URL('../..',import.meta.url)),staticRoot=path.join(root,'src/main/resources/static');
const walk=directory=>fs.readdirSync(directory,{withFileTypes:true}).flatMap(entry=>entry.isDirectory()?walk(path.join(directory,entry.name)):[path.join(directory,entry.name)]);
const files=walk(staticRoot),modules=files.filter(file=>file.endsWith('.js')),html=files.filter(file=>file.endsWith('.html'));
const edges=new Map(),entries=[];
for(const file of modules){
 const source=fs.readFileSync(file,'utf8'),imports=[];
 for(const match of source.matchAll(/(?:\bfrom\s*|\bimport\s*\(\s*|\bimport\s*)(['"])(\.{1,2}\/[^'"]+\.js)\1/g))imports.push(path.resolve(path.dirname(file),match[2]));
 for(const target of imports)if(!modules.includes(target))throw Error('Missing or nonlocal module '+path.relative(root,file)+' -> '+target);
 edges.set(file,imports);
}
for(const file of html)for(const match of fs.readFileSync(file,'utf8').matchAll(/<script\b[^>]*\bsrc=['"]([^'"]+)['"]/gi)){
 const target=match[1].startsWith('/')?path.join(staticRoot,match[1]):path.resolve(path.dirname(file),match[1]);
 if(!modules.includes(target))throw Error('Missing HTML entry module '+path.relative(root,file));entries.push(target);
}
const reachable=new Set();function visit(file){if(reachable.has(file))return;reachable.add(file);for(const dependency of edges.get(file)||[])visit(dependency);}
entries.forEach(visit);const unused=modules.filter(file=>!reachable.has(file));
const result={checkedAt:new Date().toISOString(),html:html.length,modules:modules.length,reachable:reachable.size,unused:unused.map(f=>path.relative(root,f)),scope:'Literal ES import/re-export/dynamic-import graph rooted at every static HTML. Framework hooks and runtime event callbacks are not classified as dead by name matching.'};
const output=path.join(root,'build/reports/web-quality');fs.mkdirSync(output,{recursive:true});fs.writeFileSync(path.join(output,'browser-modules.json'),JSON.stringify(result,null,2)+'\n');
if(unused.length)throw Error('Unreachable browser modules: '+result.unused.join(', '));
console.log(`Browser module graph: ${html.length} HTML, ${modules.length}/${reachable.size} reachable, no missing modules.`);
