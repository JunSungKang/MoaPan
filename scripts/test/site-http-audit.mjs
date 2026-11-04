// Read-only discovery audit: no credentials, article text, cookies or search terms are retained.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
const root=fs.realpathSync(new URL('../..',import.meta.url));
const base=new URL(process.env.MOAPAN_AUDIT_BASE_URL||'http://localhost:8080');
assert(!base.username&&!base.password&&!base.search&&!base.hash&&base.pathname==='/','Origin only');
assert(base.protocol==='https:'||(base.protocol==='http:'&&['localhost','127.0.0.1','[::1]'].includes(base.hostname)),'HTTPS required outside loopback');
const publicPages=['index','about','privacy','terms'];
const pages=[...publicPages,'login','signup','settings','account','admin','newsletter','newsletter-editor'];
const attr=(tag,name)=>tag?.match(new RegExp('\\b'+name+'\\s*=\\s*["\']([^"\']*)["\']','i'))?.[1]||null;
function metadata(html){
 const meta=[...html.matchAll(/<meta\b[^>]*>/gi)].map(m=>m[0]),links=[...html.matchAll(/<link\b[^>]*>/gi)].map(m=>m[0]);
 return {title:html.match(/<title>([^]*?)<\/title>/i)?.[1]||null,description:attr(meta.find(t=>attr(t,'name')==='description'),'content'),robots:attr(meta.find(t=>attr(t,'name')==='robots'),'content'),lang:attr(html.match(/<html\b[^>]*>/i)?.[0],'lang'),viewport:attr(meta.find(t=>attr(t,'name')==='viewport'),'content'),canonical:attr(links.find(t=>attr(t,'rel')==='canonical'),'href'),staticArticleCount:(html.match(/<article\b/gi)||[]).length};
}
async function get(p){
 const response=await fetch(new URL(p,base),{redirect:'manual',signal:AbortSignal.timeout(15000)});
 const bytes=new Uint8Array(await response.arrayBuffer());assert(bytes.length<=524288,'Audit response exceeds bound');
 return {status:response.status,contentType:response.headers.get('content-type'),location:response.headers.get('location'),canonicalHeader:response.headers.get('link'),robotsHeader:response.headers.get('x-robots-tag'),contentLanguage:response.headers.get('content-language'),text:new TextDecoder().decode(bytes)};
}
const result={checkedAt:new Date().toISOString(),origin:base.origin,pages:[],checks:{},limitations:['Actual Google/Naver/Bing indexing and rank require portal evidence and remain UNKNOWN.','User-agent imitation and public search samples do not prove access from a real engine crawler.']};
for(const name of pages){
 const p='/'+name+'.html',r=await get(p),publicPage=publicPages.includes(name),auth=['login','signup'].includes(name);
 assert.equal(r.status,name==='newsletter'?404:publicPage||auth?200:302,p+' unexpected HTTP status');
 result.pages.push({path:p,publicPage,...Object.fromEntries(Object.entries(r).filter(([k])=>k!=='text')),metadata:r.status===200?metadata(r.text):null});
}
const rootPage=await get('/');result.root={...Object.fromEntries(Object.entries(rootPage).filter(([k])=>k!=='text')),metadata:metadata(rootPage.text)};
const robots=await get('/robots.txt'),sitemap=await get('/sitemap.xml');assert.equal(robots.status,200);assert.equal(sitemap.status,200);
result.checks.robots={status:robots.status,sitemapReference:robots.text.match(/^Sitemap:\s*(.+)$/m)?.[1]||null};
result.checks.sitemap={status:sitemap.status,urls:[...sitemap.text.matchAll(/<loc>([^<]+)<\/loc>/g)].map(m=>m[1])};
const edition=await get('/api/v1/public/news/edition');assert.equal(edition.status,200);const payload=JSON.parse(edition.text);
result.checks.publicEdition={status:edition.status,returnedItems:payload.items?.length,candidateCount:payload.candidateCount};
const notFound=await get('/audit-no-such-page.html');assert.equal(notFound.status,404);result.checks.notFound={status:notFound.status,robotsHeader:notFound.robotsHeader};
for(const p of ['/api/v1/newsletters','/api/v1/admin/sites']){const r=await get(p);assert.equal(r.status,p==='/api/v1/newsletters'?404:401);result.checks[p]={status:r.status,robotsHeader:r.robotsHeader};}
result.absoluteHtmlCanonicals={passed:result.pages.filter(p=>p.publicPage&&/^https?:\/\//.test(p.metadata.canonical||'')).length,total:publicPages.length};
const output=path.join(root,'build/reports/site-http-audit.json');fs.mkdirSync(path.dirname(output),{recursive:true});fs.writeFileSync(output,JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify({pages:result.pages.length,publicPages:publicPages.length,absoluteHtmlCanonicals:result.absoluteHtmlCanonicals,actualPortalIndex:'UNKNOWN',output:'build/reports/site-http-audit.json'}));
