import fs from 'node:fs';
import path from 'node:path';
import {randomBytes} from 'node:crypto';
import {fileURLToPath} from 'node:url';

const root=fs.realpathSync(fileURLToPath(new URL('../../',import.meta.url)));
export function resolveKeyDirectory(requested='.local/mail'){
  const directory=path.resolve(root,requested);
  if(!directory.startsWith(root+path.sep))throw new Error('Email key directory must stay inside this workspace');
  return directory;
}

function prepare(directory){
  let current=root;
  for(const part of path.relative(root,directory).split(path.sep)){
    current=path.join(current,part);
    try{const stat=fs.lstatSync(current);if(!stat.isDirectory()||stat.isSymbolicLink())throw new Error('Unsafe key directory');}
    catch(error){if(error.code!=='ENOENT')throw error;fs.mkdirSync(current,{mode:0o700});}
  }
  if(fs.realpathSync(directory)!==directory||(fs.statSync(directory).mode&0o077)!==0)throw new Error('Private key directory permissions required');
  const keyFile=path.join(directory,'data.key');let created=false;
  try{fs.writeFileSync(keyFile,randomBytes(32).toString('base64')+'\n',{flag:'wx',mode:0o400});created=true;}
  catch(error){if(error.code!=='EEXIST')throw error;}
  const descriptor=fs.openSync(keyFile,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);
  try{
    const stat=fs.fstatSync(descriptor);
    if(!stat.isFile()||stat.nlink!==1)throw new Error('Invalid key file');
    const encoded=fs.readFileSync(descriptor,'utf8').trim();
    if(!/^[A-Za-z0-9+/]{43}=$/.test(encoded)||Buffer.from(encoded,'base64').length!==32||Buffer.from(encoded,'base64').toString('base64')!==encoded)throw new Error('Invalid existing email key');
    // Local Compose file secrets retain host permissions. The private parent protects the
    // host copy while this read-only mode lets UID10001 read its individual bind mount.
    fs.fchmodSync(descriptor,0o444);
  }finally{fs.closeSync(descriptor);}
  console.log(created?'Email data key created; back it up with the encrypted database.':'Existing email data key validated and preserved.');
}

if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  try{
    const args=process.argv.slice(2);
    if(args.length!==0&&(args.length!==2||args[0]!=='--directory'))throw new Error('Invalid arguments');
    prepare(resolveKeyDirectory(args[1]));
  }catch{console.error('Email key preparation failed; check workspace paths, permissions and existing key format. Existing keys are never overwritten.');process.exitCode=1;}
}
