import fs from 'node:fs';
import path from 'node:path';
import lighthouse from '../../.local/web-audit-tools/node_modules/lighthouse/core/index.js';
import {launch} from '../../.local/web-audit-tools/node_modules/chrome-launcher/dist/index.js';
const root=fs.realpathSync(new URL('../..',import.meta.url));
const [base,stage='final']=process.argv.slice(2),url=new URL(base);
if(!['http:','https:'].includes(url.protocol)||!['localhost','127.0.0.1'].includes(url.hostname)||url.username||url.password||url.search||url.hash)throw Error('Disposable loopback audit origin required');
if(!['baseline','preflight','final'].includes(stage))throw Error('Invalid audit stage');
const output=path.join(root,'build/reports/web-quality',stage);fs.mkdirSync(output,{recursive:true});
const executable=process.env.CHROME_PATH;
if(!executable||!fs.existsSync(executable))throw Error('Verified Playwright Chromium path required from WebQualityAudit');
const results=[];
for(const profile of ['mobile','desktop'])for(const page of stage!=='final'?['index']:['index','about','privacy','terms']){
 const browser=await launch({chromePath:executable,chromeFlags:['--headless','--disable-gpu','--no-first-run','--no-default-browser-check'],handleSIGINT:false});
 try{
  const config=profile==='desktop'?(await import('../../.local/web-audit-tools/node_modules/lighthouse/core/config/lr-desktop-config.js')).default:undefined;
  const result=await lighthouse(new URL(page+'.html',url).href,{port:browser.port,logLevel:'error',output:'json',enableErrorReporting:false,onlyCategories:['performance','accessibility','best-practices','seo']},config);
  if(result.lhr.runtimeError)throw Error(JSON.stringify(result.lhr.runtimeError));
  fs.writeFileSync(path.join(output,profile+'-'+page+'.json'),result.report);
  const summary={profile,page,scores:Object.fromEntries(Object.entries(result.lhr.categories).map(([k,v])=>[k,Math.round(v.score*100)])),metrics:Object.fromEntries(['first-contentful-paint','largest-contentful-paint','total-blocking-time','cumulative-layout-shift','speed-index'].map(k=>[k,result.lhr.audits[k].numericValue])),failures:Object.entries(result.lhr.audits).filter(([,a])=>a.score!==null&&a.score<1).map(([id,a])=>({id,title:a.title,score:a.score}))};
  results.push(summary);console.log(JSON.stringify(summary));
 }finally{await browser.kill();}
}
fs.writeFileSync(path.join(output,'lighthouse-summary.json'),JSON.stringify({checkedAt:new Date().toISOString(),version:'13.6.0',scope:'Disposable real Spring/PostgreSQL with synthetic content, local Chromium; not live deployment or CrUX.',results},null,2)+'\n');
if(stage==='final'&&results.some(result=>Object.values(result.scores).some(score=>score!==100)))throw Error('100-point target not met; inspect every recorded score and metric.');
