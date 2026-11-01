import fs from 'node:fs';
import path from 'node:path';

// Deliberately limited to this project's flat rules and @media blocks; fail on unknown syntax.
function split(text, delimiter) {
  const out=[];let start=0,quote='',depth=0;
  for(let i=0;i<text.length;i++){const c=text[i];if(quote){if(c==='\\')i++;else if(c===quote)quote='';continue;}if(c==='"'||c==="'")quote=c;else if(c==='('||c==='[')depth++;else if(c===')'||c===']')depth--;else if(c===delimiter&&depth===0){out.push(text.slice(start,i).trim());start=i+1;}}
  out.push(text.slice(start).trim());return out.filter(Boolean);
}
export function parseCss(text,context=[]) {
  text=text.replace(/\/\*[\s\S]*?\*\//g,'');const rules=[];let start=0;
  while(start<text.length){while(/\s/.test(text[start]||'')&&start<text.length)start++;if(start===text.length)break;const brace=text.indexOf('{',start);if(brace<0)throw Error('Unsupported CSS tail');const selector=text.slice(start,brace).trim();let i=brace+1,depth=1,quote='';
    for(;i<text.length&&depth;i++){const c=text[i];if(quote){if(c==='\\')i++;else if(c===quote)quote='';}else if(c==='"'||c==="'")quote=c;else if(c==='{')depth++;else if(c==='}')depth--;}
    if(depth)throw Error('Unclosed CSS block');const body=text.slice(brace+1,i-1);if(selector.startsWith('@')){if(!selector.startsWith('@media ' )&&!selector.startsWith('@media('))throw Error('Unsupported CSS at-rule '+selector);rules.push(...parseCss(body,[...context,selector.replace(/\s+/g,' ')]));}
    else {const declarations=split(body,';').map(item=>{const colon=item.indexOf(':');if(colon<1)throw Error('Invalid CSS declaration');return {property:item.slice(0,colon).trim(),value:item.slice(colon+1).trim()};});for(const part of split(selector,','))rules.push({context,selector:part,declarations:declarations.map(d=>({...d}))});}start=i;
  }return rules;
}
function canonicalSelector(text){
 let out='',quote='',brackets=0,parens=0;for(let i=0;i<text.length;i++){const c=text[i];if(c==='\\'){out+=c+(text[++i]||'');continue;}if(quote){out+=c;if(c===quote)quote='';continue;}if(c==='"'||c==="'"){quote=c;out+=c;continue;}if(c==='[')brackets++;if(c===']')brackets--;if(c==='(')parens++;if(c===')')parens--;if(!brackets&&!parens&&'>+~'.includes(c)){out=out.trimEnd()+c;while(/\s/.test(text[i+1]||'')&&i+1<text.length)i++;}else if(/\s/.test(c)){if(!out.endsWith(' '))out+=' ';}else out+=c;}
 return out.trim();
}
function canonicalMedia(text){
 // Preserve quoted strings and word boundaries (screen and != screenand).
 return text.split(/((?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'))/g).map((part,i)=>i%2?part:part.replace(/\s+/g,' ').replace(/\s*:\s*/g,':').replace(/\(\s*/g,'(').replace(/\s*\)/g,')').replace(/^@media\s*\(/,'@media(')).join('').trim();
}
function covers(property){
 const sides=['top','right','bottom','left'];
 const groups={font:['font-family','font-size','font-style','font-weight','font-stretch','font-variant','line-height'],background:['background-color','background-image','background-position','background-size','background-repeat','background-origin','background-clip','background-attachment'],gap:['row-gap','column-gap'],overflow:['overflow-x','overflow-y'],flex:['flex-grow','flex-shrink','flex-basis'],'list-style':['list-style-type','list-style-position','list-style-image'],'text-decoration':['text-decoration-line','text-decoration-style','text-decoration-color','text-decoration-thickness']};
 if(groups[property])return groups[property];
 if(['margin','padding'].includes(property))return sides.map(s=>property+'-'+s);
 if(property==='border')return sides.flatMap(s=>['width','style','color'].map(k=>'border-'+s+'-'+k));
 if(/^border-(top|right|bottom|left)$/.test(property))return ['width','style','color'].map(k=>property+'-'+k);
 if(/^border-(width|style|color)$/.test(property))return sides.map(s=>'border-'+s+'-'+property.split('-')[1]);
 return [property];
}
export function dominatedDeclarations(rules){
 const seen=new Map(),removed=[];
 for(let i=rules.length-1;i>=0;i--){const rule=rules[i],key=JSON.stringify([rule.context.map(canonicalMedia),canonicalSelector(rule.selector)]);let next=seen.get(key);if(!next)seen.set(key,next=new Map());
  for(let j=rule.declarations.length-1;j>=0;j--){const d=rule.declarations[j],important=/!important\s*$/.test(d.value),keys=covers(d.property);if(keys.every(k=>next.has(k)&&(!important||next.get(k))))removed.push({rule:i,declaration:j,context:rule.context,selector:rule.selector,...d});else for(const k of keys)next.set(k,important||next.get(k)||false);}
 }return removed;
}
export function normalizeCss(source){
 const rules=parseCss(source),removed=dominatedDeclarations(rules),dead=new Set(removed.map(d=>d.rule+':'+d.declaration)),groups=[];
 for(let i=0;i<rules.length;i++){const r=rules[i],declarations=r.declarations.filter((_,j)=>!dead.has(i+':'+j));if(!declarations.length)continue;const context=JSON.stringify(r.context),body=declarations.map(d=>d.property+': '+d.value+';').join(' '),last=groups.at(-1);if(last&&last.context===context&&last.body===body)last.selectors.push(r.selector);else groups.push({context,contexts:r.context,body,selectors:[r.selector]});}
 let css='/* Shared primitives and domain rules; responsive/print conditions are intentional. */\n',open=[];
 for(const g of groups){let common=0;while(common<open.length&&common<g.contexts.length&&open[common]===g.contexts[common])common++;for(let j=open.length;j>common;j--)css+='  '.repeat(j-1)+'}\n';for(let j=common;j<g.contexts.length;j++)css+='  '.repeat(j)+g.contexts[j]+' {\n';open=g.contexts;css+='  '.repeat(open.length)+g.selectors.join(', ' )+' { '+g.body+' }\n';}
 for(let j=open.length;j>0;j--)css+='  '.repeat(j-1)+'}\n';return {css,removed};
}
export function auditStyles(root='src/main/resources/static/assets/css'){const findings=[],files=fs.readdirSync(root).filter(f=>f.endsWith('.css'));if(!files.length)throw Error('No stylesheets analyzed');for(const name of files){const file=path.join(root,name);const rules=parseCss(fs.readFileSync(file,'utf8'));if(!rules.length)throw Error('Empty stylesheet '+file);for(const finding of dominatedDeclarations(rules))findings.push({file,...finding});}return findings;}
export function validateStylesheetLinks(html){
 const links=[];for(const tag of html.matchAll(/<link\b[^>]*>/gi)){const attrs=new Map([...tag[0].matchAll(/\b(rel|href)\s*=\s*(["'])(.*?)\2/gi)].map(m=>[m[1].toLowerCase(),m[3]]));if((attrs.get('rel')||'').split(/\s+/).includes('stylesheet')){if(!attrs.has('href'))throw Error('Stylesheet without href');links.push(attrs.get('href'));}}
 const keys=links.map(href=>{const u=new URL(href,'https://resource.invalid/');u.hash='';return u.href;});if(new Set(keys).size!==keys.length)throw Error('Repeated stylesheet request');return links;
}
if(process.argv[1]===new URL(import.meta.url).pathname){if(process.argv.slice(2).some(a=>a!=='--fix'))throw Error('Usage: style-structure.mjs [--fix]');if(process.argv.includes('--fix')){const workspace=fs.realpathSync('.'),dir=path.join(workspace,'src/main/resources/static/assets/css');for(const name of fs.readdirSync(dir).filter(f=>f.endsWith('.css'))){const file=fs.realpathSync(path.join(dir,name));if(!file.startsWith(workspace+path.sep))throw Error('Stylesheet outside workspace');fs.writeFileSync(file,normalizeCss(fs.readFileSync(file,'utf8')).css);}}for(const name of fs.readdirSync('src/main/resources/static').filter(f=>f.endsWith('.html')))for(const href of validateStylesheetLinks(fs.readFileSync('src/main/resources/static/'+name,'utf8')))if(!fs.existsSync('src/main/resources/static'+href))throw Error('Missing stylesheet '+href);const findings=auditStyles();if(findings.length){console.error(JSON.stringify(findings,null,2));process.exitCode=1;}else console.log('CSS structure: no dominated declarations within matching selector/media contexts.');}
