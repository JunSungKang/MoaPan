import fs from 'node:fs';
import {createHash} from 'node:crypto';
// Narrow, expiring applicability decision. Never suppresses another package/version/CVE.
export function documentedNonExposure(v){
 if(v.VulnerabilityID!=='CVE-2026-116587'||v.PkgName!=='github.com/go-jose/go-jose/v3'||v.InstalledVersion!=='v3.0.5')return false;
 const review=JSON.parse(fs.readFileSync('docs/05-test/evidence/jose-v3-applicability.json','utf8'));
 if(Date.now()>=Date.parse(review.expiresAt))return false;
 return Object.entries(review.configurationSha256).every(([p,hash])=>createHash('sha256').update(fs.readFileSync(p)).digest('hex')===hash);
}
// Source-version scanner does not understand this local backport; never apply to another scope.
export function documentedSourceBackport(v){
 if(v.PkgName!=='github.com/go-jose/go-jose/v3'||v.InstalledVersion!=='v3.0.5'||!['CVE-2026-116587','CVE-2026-116585','CVE-2026-116586'].includes(v.VulnerabilityID))return false;
 const review=JSON.parse(fs.readFileSync('docs/05-test/evidence/jose-v3-backport.json','utf8'));
 const expires=Date.parse(review.expiresAt);
 const required=['Dockerfile','scripts/deploy/proxy/main.go','scripts/deploy/proxy/go.mod','scripts/deploy/proxy/go.sum',...['README.md','cipher_security_test.go','jose-v3-security.patch','jose_security_test.go','json_security_test.go','modified.sha256','original.sha256','public_api_test.go'].map(p=>'scripts/deploy/proxy/patches/'+p)];
 if(review.status!=='fixed'||!Number.isFinite(expires)||Date.now()>=expires||review.runtimeSourceChecksumsVerified!==6||review.tests?.upstreamPackages!==5||review.tests?.unpatchedPublicApiFailures!==5)return false;
 if(!review.sourceSha256||Object.keys(review.sourceSha256).length!==required.length||!required.every(p=>/^[a-f0-9]{64}$/.test(review.sourceSha256[p]||'')))return false;
 return Object.entries(review.sourceSha256).every(([p,hash])=>createHash('sha256').update(fs.readFileSync(p)).digest('hex')===hash);
}
