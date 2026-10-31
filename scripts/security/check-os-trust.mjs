import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {X509Certificate,createHash} from 'node:crypto';
const prohibited=['emSign ECC Root CA - G3','AAA Certificate Services','emSign Root CA - G1','emSign Root CA - G2'];
for(const image of process.argv.slice(2)){
 const digest=execFileSync('docker',['image','inspect','--format','{{.Id}}',image],{encoding:'utf8'}).trim();
 const bundle=execFileSync('docker',['run','--rm','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges:true','--entrypoint','cat',digest,'/etc/ssl/certs/ca-certificates.crt'],{encoding:'utf8',maxBuffer:2*1024*1024});
 const certificates=(bundle.match(/-----BEGIN CERTIFICATE-----[^]*?-----END CERTIFICATE-----/g)||[]).map(pem=>new X509Certificate(pem));
 assert(certificates.length>=50,'OS certificate bundle missing or unrecognized');
 const present=certificates.filter(c=>prohibited.some(name=>c.subject.split('\n').includes('CN='+name)));
 assert.equal(present.length,0,`Official mitigation roots remain in ${digest}`);
 console.log(JSON.stringify({image:digest,roots:certificates.length,bundleSha256:createHash('sha256').update(bundle).digest('hex'),officialFourRoots:'ABSENT'}));
}
