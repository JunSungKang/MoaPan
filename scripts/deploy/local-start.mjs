import {spawnSync} from 'node:child_process';
const ready=spawnSync('docker',['info','--format','{{.ServerVersion}}'],{encoding:'utf8',timeout:10000,killSignal:'SIGKILL'});
if(ready.error||ready.status!==0){console.error('Docker Engine is not ready (10-second preflight failed). Compose was not started. Diagnose Docker Desktop; do not reset database volumes.');process.exit(2);}
console.log('Docker Engine ready: '+ready.stdout.trim());
const result=spawnSync('docker',['compose','up','-d','--no-build','--wait','--wait-timeout','90'],{stdio:'inherit',timeout:120000,killSignal:'SIGKILL'});
if(result.error){console.error('Compose did not complete within 120 seconds. Inspect service state and logs; this is not a successful start.');process.exit(3);}
process.exit(result.status??3);
