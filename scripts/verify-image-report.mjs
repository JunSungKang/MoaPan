import fs from 'node:fs';
import assert from 'node:assert/strict';
const [file,digest]=process.argv.slice(2),r=JSON.parse(fs.readFileSync(file,'utf8'));
assert.equal(r.Metadata?.ImageID,digest,'Scanner image identity differs from selected image');
assert(r.Results?.some(x=>x.Class==='os-pkgs'&&x.Packages?.length>0),'OS packages were not recognized');
assert.equal(r.Results.flatMap(x=>x.Vulnerabilities||[]).filter(x=>['HIGH','CRITICAL'].includes(x.Severity)).length,0,'Unresolved HIGH/CRITICAL image findings');
assert.equal(r.Results.flatMap(x=>x.Secrets||[]).length,0,'Image secrets detected');
console.log(`Image scan gate passed: ${digest}`);
