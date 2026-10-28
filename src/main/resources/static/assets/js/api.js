import {deletePreferences} from './preferences.js';
let csrf;
export async function request(path, options = {}) {
  const method=options.method||'GET';const headers={'Accept':'application/json',...(options.headers||{})};
  if(!['GET','HEAD'].includes(method)){ if(!csrf) await refreshCsrf(); headers['X-CSRF-TOKEN']=csrf;headers['Content-Type']='application/json'; }
  const response=await fetch(path,{...options,method,headers,credentials:'same-origin',cache:'no-store',body:options.body===undefined?undefined:JSON.stringify(options.body)});
  if(!response.ok){ if(response.status===401&&!path.includes('/auth/login')){ const id=sessionStorage.getItem('moapan-account');if(id)await deletePreferences(id).catch(()=>{});sessionStorage.removeItem('moapan-account');window.dispatchEvent(new Event('moapan-auth-lost')); } const messages={400:'입력 형식이나 요청이 올바르지 않습니다.',401:'로그인이 필요하거나 인증 정보가 올바르지 않습니다.',403:'권한 또는 보안 토큰을 확인하세요.',409:'상태가 변경되었거나 중복입니다. 새로 조회하세요.',429:'요청 한도에 도달했습니다. 잠시 후 다시 시도하세요.',503:'일시적으로 사용할 수 없습니다.'};let detail;try{detail=await response.json();}catch{} const error=new Error(messages[response.status]||'요청에 실패했습니다.');error.status=response.status;error.code=detail?.error;error.attemptsRemaining=Number.isInteger(detail?.attemptsRemaining)?detail.attemptsRemaining:undefined;const retry=response.headers.get('Retry-After');error.retryAfterSeconds=retry!==null&&/^\d+$/.test(retry)?Number(retry):undefined;throw error; }
  if(path==='/api/v1/auth/login'||path==='/api/v1/auth/logout'){csrf=undefined;}
  const text=await response.text();return text?JSON.parse(text):null;
}
export async function refreshCsrf(){const response=await fetch('/api/v1/auth/csrf',{credentials:'same-origin',cache:'no-store'});if(!response.ok)throw new Error('보안 토큰을 받을 수 없습니다.');csrf=(await response.json()).token;}
export async function account(){const a=await request('/api/v1/account');sessionStorage.setItem('moapan-account',a.id);return a;}
