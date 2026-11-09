import fs from 'node:fs';
import {verifyJoseValidation,advisories} from './jose-validation.mjs';
// Source-version scanner does not understand this local backport; never apply to another scope.
export function documentedSourceBackport(v){
 if(v.PkgName!=='github.com/go-jose/go-jose/v3'||v.InstalledVersion!=='v3.0.5'||!Object.values(advisories).includes(v.VulnerabilityID))return false;
 try{verifyJoseValidation(JSON.parse(fs.readFileSync('build/reports/security/jose-validation.json','utf8')));return true;}catch{return false;}
}
