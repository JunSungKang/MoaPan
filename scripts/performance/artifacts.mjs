import fs from 'node:fs';
import path from 'node:path';
import {javaArtifactDirectory} from '../platform/prepare-java.mjs';

// Reuse the existing pre-write canonical-parent check; reject redirected leaves too.
export function artifactPath(workspace,requested) {
    const root=fs.realpathSync(workspace),lexical=path.resolve(root,requested);
    if(!lexical.startsWith(root+'/.local/'))throw Error('Performance artifacts must stay in workspace .local');
    const parent=javaArtifactDirectory(root,path.dirname(lexical));
    if(!parent.startsWith(root+'/.local/'))throw Error('Performance artifact directory redirects');
    const target=path.join(parent,path.basename(lexical));
    try{const stat=fs.lstatSync(target);if(!stat.isFile()||stat.isSymbolicLink())throw Error('Performance artifact must be a regular file');}catch(e){if(e.code!=='ENOENT')throw e;}
    fs.mkdirSync(parent,{recursive:true});
    return target;
}
export function writeArtifact(workspace,requested,value) {
    const target=artifactPath(workspace,requested);
    const fd=fs.openSync(target,fs.constants.O_CREAT|fs.constants.O_TRUNC|fs.constants.O_WRONLY|fs.constants.O_NOFOLLOW,0o600);
    try{fs.writeFileSync(fd,JSON.stringify(value,null,2)+'\n');}finally{fs.closeSync(fd);}
}
