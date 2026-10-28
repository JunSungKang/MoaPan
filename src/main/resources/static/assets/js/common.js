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
function guestMenu(){if(menu){menu.replaceChildren();for(const [name,href] of [['로그인','/login.html'],['회원가입','/signup.html']]){const link=el('a',name);link.href=href;menu.append(link);}}for(const node of document.querySelectorAll('[data-auth]'))node.hidden=true;}
export const viewer=account().catch(error=>{if(error.status!==401)throw error;return null;});
viewer.then(a=>{if(!a){guestMenu();return;}for(const node of document.querySelectorAll('[data-auth]'))node.hidden=node.dataset.auth==='admin'&&a.role!=='ADMIN';if(!menu)return;menu.replaceChildren(el('span',a.username));const settings=el('a','관심사 설정');settings.href='/settings.html';const accountLink=el('a','계정 관리');accountLink.href='/account.html';menu.append(settings,accountLink);if(a.role==='ADMIN'){const admin=el('a','관리');admin.href='/admin.html';menu.append(admin);}const logout=el('button','로그아웃');logout.type='button';logout.addEventListener('click',async()=>{try{await request('/api/v1/auth/logout',{method:'POST'});sessionStorage.removeItem('moapan-account');window.dispatchEvent(new Event('moapan-clear'));location.assign('/index.html');}catch(e){report(e);}});menu.append(logout);}).catch(error=>{guestMenu();report(error);});
export async function requireAccount(role){const a=await viewer;if(!a){location.replace('/login.html');throw new Error('로그인이 필요합니다.');}if(role&&a.role!==role){location.replace('/index.html');throw new Error('관리자 권한이 필요합니다.');}const main=document.querySelector('[data-protected]');if(main)main.hidden=false;return a;}
window.addEventListener('moapan-auth-lost',()=>{guestMenu();const main=document.querySelector('[data-protected]');if(main)main.hidden=true;window.dispatchEvent(new Event('moapan-clear'));});
