import fs from 'node:fs';
import {createHash} from 'node:crypto';
// Narrow, expiring applicability decision. Never suppresses another package/version/CVE.
export function documentedNonExposure(v){
 if(v.VulnerabilityID!=='CVE-2026-116587'||v.PkgName!=='github.com/go-jose/go-jose/v3'||v.InstalledVersion!=='v3.0.5')return false;
 const review=JSON.parse(fs.readFileSync('docs/05-test/evidence/jose-v3-applicability.json','utf8'));
 if(Date.now()>=Date.parse(review.expiresAt))return false;
 return Object.entries(review.configurationSha256).every(([p,hash])=>createHash('sha256').update(fs.readFileSync(p)).digest('hex')===hash);
}
