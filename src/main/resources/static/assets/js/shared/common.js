import {language,t,languagePicker} from './i18n.js';
import {brand} from './config.js';
import {renderBanner} from '../advertising/banner.js';
import {request,account} from './api.js';
export function el(tag,text,className){const node=document.createElement(tag);if(text!==undefined)node.textContent=t(text);if(className)node.className=className;return node;}
export function report(error){const node=document.querySelector('[role="status"]');if(node)node.textContent=error.message||String(error);}
export function date(value){return new Intl.DateTimeFormat(language==='en'?'en-US':'ko-KR',{timeZone:'Asia/Seoul',year:'numeric',month:'long',day:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(value));}
for(const node of document.querySelectorAll('[data-brand]'))node.textContent=brand.name;
for(const node of document.querySelectorAll('[data-brand-en]'))node.textContent=brand.english;
for(const node of document.querySelectorAll('[data-masthead-quote]'))node.textContent=brand.mastheadQuote;
const pageTitle=document.documentElement.dataset.pageTitle||document.title;
const pageDescription=document.querySelector('meta[name=description]')?.content;
function setPageMetadata(){document.title=t(pageTitle)+' | '+(language==='ko'?brand.name+' '+brand.english:brand.english);const og=document.querySelector('meta[property="og:title"]');if(og)og.content=document.title;if(pageDescription)for(const node of document.querySelectorAll('meta[name=description],meta[property="og:description"]'))node.content=t(pageDescription);const locale=document.querySelector('meta[property="og:locale"]');if(locale)locale.content=language==='ko'?'ko_KR':'en_US';const canonical=document.querySelector('link[rel="canonical"]');if(canonical)canonical.href=new URL(canonical.getAttribute('href'),location.origin).href;}
setPageMetadata();
let today=document.querySelector('#today');
export function setIssueDate(items){
 const latest=items.filter(a=>Number.isFinite(Date.parse(a.collectedAt))).sort((a,b)=>Date.parse(b.collectedAt)-Date.parse(a.collectedAt)||String(a.id).localeCompare(String(b.id)))[0];
 if(!today)return;
 if(!latest){const placeholder=document.createElement('span');placeholder.id='today';placeholder.textContent=t('발행 정보 없음');today.replaceWith(placeholder);today=placeholder;return;}
 if(today.tagName!=='TIME'){const time=document.createElement('time');time.id='today';today.replaceWith(time);today=time;}
 const published=Number.isFinite(Date.parse(latest.publishedAt));today.dataset.issueValue=published?latest.publishedAt:latest.collectedAt;today.dataset.issueKind=published?'published':'collected';renderIssueDate();
}
function renderIssueDate(){if(!today?.dataset.issueValue)return;today.dateTime=today.dataset.issueValue;today.textContent=(today.dataset.issueKind==='collected'?t('수집 기준')+' · ':'')+date(today.dataset.issueValue);}
if(today)today.textContent='';
const menu=document.querySelector('#account-menu');
function guestMenu(){if(menu){menu.replaceChildren();for(const [name,href] of [['로그인','/login.html'],['회원가입','/signup.html']]){const link=el('a',name);link.href=href;menu.append(link);}}for(const node of document.querySelectorAll('[data-auth]'))node.hidden=true;}
export const viewer=account().catch(error=>{if(error.status!==401)throw error;return null;});
viewer.then(a=>{if(!a){guestMenu();if(document.body.hasAttribute('data-requires-login'))location.replace('/login.html');return;}for(const node of document.querySelectorAll('[data-auth]'))node.hidden=node.dataset.auth==='admin'&&a.role!=='ADMIN';if(!menu)return;const name=el('span',a.username);name.dataset.originalContent='';menu.replaceChildren(name);const settings=el('a','관심사 설정');settings.href='/settings.html';const accountLink=el('a','계정 관리');accountLink.href='/account.html';menu.append(settings,accountLink);if(a.role==='ADMIN'){const admin=el('a','관리');admin.href='/admin.html';menu.append(admin);}const logout=el('button','로그아웃');logout.type='button';logout.addEventListener('click',async()=>{try{await request('/api/v1/auth/logout',{method:'POST'});sessionStorage.removeItem('moapan-account');window.dispatchEvent(new Event('moapan-clear'));location.assign('/login.html');}catch(e){report(e);}});menu.append(logout);}).catch(error=>{guestMenu();report(error);});
export async function requireAccount(role){const a=await viewer;if(!a){location.replace('/login.html');throw new Error('로그인이 필요합니다.');}if(role&&a.role!==role){location.replace('/index.html');throw new Error('관리자 권한이 필요합니다.');}const main=document.querySelector('[data-protected]');if(main)main.hidden=false;return a;}
window.addEventListener('moapan-auth-lost',()=>{guestMenu();const main=document.querySelector('[data-protected]');if(main)main.hidden=true;window.dispatchEvent(new Event('moapan-clear'));if(document.body.hasAttribute('data-requires-login'))location.replace('/login.html');});

const searchButton=document.querySelector('#open-search');
const languageSlot=document.querySelector('[data-language-slot]');
if(languageSlot){languageSlot.append(languagePicker());}else if(searchButton){const tools=document.createElement('div');tools.className='header-tools';searchButton.before(tools);tools.append(languagePicker(),searchButton);}else{const header=document.querySelector('.masthead,.auth-brand');if(header)header.prepend(languagePicker());}
window.addEventListener('moapan-language',()=>{renderIssueDate();setPageMetadata();});

const adSlot=document.querySelector('[data-ad-slot]');
if(adSlot)viewer.then(async a=>{if(!a)return;try{const config=await request('/api/v1/advertisements');renderBanner(adSlot,config);}catch{/* An unavailable ad must not stop the newspaper. */}});
