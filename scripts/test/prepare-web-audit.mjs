// Test tools only; no frontend build, production Node server or remote telemetry.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
const root=fs.realpathSync(new URL('../..',import.meta.url));
const source=path.join(root,'scripts/test/web-audit-tools'),target=path.join(root,'.local/web-audit-tools');
for(const directory of [target,path.join(root,'.local/tmp'),path.join(root,'.local/tool-home')]){
 fs.mkdirSync(directory,{recursive:true});if(!fs.realpathSync(directory).startsWith(root+path.sep))throw Error('Audit tool path outside workspace');
}
const lock=fs.readFileSync(path.join(source,'package-lock.json'));
const checksum=crypto.createHash('sha256').update(lock).digest('hex'),marker=path.join(target,'.verified-lock-sha256');
const installed=fs.existsSync(marker)&&fs.readFileSync(marker,'utf8')===checksum&&fs.existsSync(path.join(target,'node_modules/axe-core/axe.min.js'));
if(!installed){
 for(const name of ['package.json','package-lock.json'])fs.copyFileSync(path.join(source,name),path.join(target,name));
 execFileSync('npm',['ci','--prefix',target,'--ignore-scripts','--no-audit','--no-fund','--registry=https://registry.npmjs.org'],{stdio:'inherit',env:{...process.env,HOME:path.join(root,'.local/tool-home'),TMPDIR:path.join(root,'.local/tmp'),npm_config_cache:path.join(root,'.local/npm-cache')}});
 fs.writeFileSync(marker,checksum);
}
for(const [name,version]of [['lighthouse','13.6.0'],['axe-core','4.14.0']])if(JSON.parse(fs.readFileSync(path.join(target,'node_modules',name,'package.json'))).version!==version)throw Error('Audit tool version mismatch');
console.log('Pinned Lighthouse/axe test tools ready; production dependencies unchanged.');
