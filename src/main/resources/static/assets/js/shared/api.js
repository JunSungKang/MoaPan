import {responseErrorMessage} from './request-errors.js';
let csrf,csrfPending,csrfGeneration=0;
function clearCsrf(){csrf=undefined;csrfPending=undefined;csrfGeneration++;}
export async function request(path, options = {}) {
  const method=options.method||'GET';const headers={'Accept':'application/json',...(options.headers||{})};
  if(!['GET','HEAD'].includes(method)){ if(!csrf) await refreshCsrf(); headers['X-CSRF-TOKEN']=csrf;headers['Content-Type']='application/json'; }
  const response=await fetch(path,{...options,method,headers,credentials:'same-origin',cache:'no-store',body:options.body===undefined?undefined:JSON.stringify(options.body)});
  if(response.status===404&&/^\/api\/v1\/newsletters(?:\/|[?#]|$)/.test(path))window.dispatchEvent(new Event('moapan-newsletter-unavailable'));
  if(!response.ok){ if(response.status===401&&!path.includes('/auth/login')){ clearCsrf();sessionStorage.removeItem('moapan-account');window.dispatchEvent(new Event('moapan-auth-lost')); } let detail;try{detail=await response.json();}catch{} const error=new Error(responseErrorMessage(response.status,detail?.error));error.status=response.status;error.code=detail?.error;error.attemptsRemaining=Number.isInteger(detail?.attemptsRemaining)?detail.attemptsRemaining:undefined;const retry=response.headers.get('Retry-After');error.retryAfterSeconds=retry!==null&&/^\d+$/.test(retry)?Number(retry):undefined;throw error; }
  if(path==='/api/v1/auth/login'||path==='/api/v1/auth/logout'){clearCsrf();}
  if(response.status===204)return null;
  const text=await response.text();return text?JSON.parse(text):null;
}
export function refreshCsrf(){
 if(csrfPending)return csrfPending;
 const generation=csrfGeneration;
 const pending=fetch('/api/v1/auth/csrf',{credentials:'same-origin',cache:'no-store'}).then(async response=>{
  if(!response.ok)throw new Error('보안 토큰을 받을 수 없습니다.');const data=await response.json();
  if(generation!==csrfGeneration)throw new Error('로그인 상태가 변경되었습니다. 다시 시도하세요.');csrf=data.token;
 }).finally(()=>{if(csrfPending===pending)csrfPending=undefined;});
 csrfPending=pending;return pending;
}
export async function account(){const a=await request('/api/v1/auth/session');if(a)sessionStorage.setItem('moapan-account',a.id);else{const previous=sessionStorage.getItem('moapan-account');sessionStorage.removeItem('moapan-account');if(previous)window.dispatchEvent(new Event('moapan-auth-lost'));}return a;}
