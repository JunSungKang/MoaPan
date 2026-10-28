import {brand} from './config.js';
import {request,account} from './api.js';
export function el(tag,text,className){const node=document.createElement(tag);if(text!==undefined)node.textContent=text;if(className)node.className=className;return node;}
export function report(error){const node=document.querySelector('[role="status"]');if(node)node.textContent=error.message||String(error);}
export function date(value){return new Intl.DateTimeFormat('ko-KR',{timeZone:'Asia/Seoul',year:'numeric',month:'long',day:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(value));}
for(const node of document.querySelectorAll('[data-brand]'))node.textContent=brand.name;
for(const node of document.querySelectorAll('[data-brand-en]'))node.textContent=brand.english;
document.title=brand.name+' · '+document.title;
const today=document.querySelector('#today');if(today)today.textContent=date(Date.now());
const menu=document.querySelector('#account-menu');
if(menu)account().then(a=>{menu.replaceChildren(el('span',a.username));const settings=el('a','관심사·계정');settings.href='/settings.html';menu.append(settings);if(a.role==='ADMIN'){const admin=el('a','관리');admin.href='/admin.html';menu.append(admin);}const logout=el('button','로그아웃');logout.type='button';logout.addEventListener('click',async()=>{try{await request('/api/v1/auth/logout',{method:'POST'});sessionStorage.removeItem('moapan-account');window.dispatchEvent(new Event('moapan-clear'));location.assign('/login.html');}catch(e){report(e);}});menu.append(logout);}).catch(()=>{});
window.addEventListener('moapan-auth-lost',()=>{window.dispatchEvent(new Event('moapan-clear'));});
