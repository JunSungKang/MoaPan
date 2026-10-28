import {request,account} from './api.js';
import {el,report,date} from './common.js';
import {loadPreferences,canonicalize,savePreferences} from './preferences.js';
import {rank} from './ranking.js';
const paper=document.querySelector('#paper'),status=document.querySelector('[role="status"]'),more=document.querySelector('#more');
let candidates=[],prefs,user,next,tagData,view='all';
window.addEventListener('moapan-clear',()=>{candidates=[];user=null;next=null;paper.replaceChildren();document.querySelector('#latest').replaceChildren();more.hidden=true;status.textContent='로그인 후 뉴스를 읽을 수 있습니다.';});
function render(){
  const search=document.querySelector('#search').value.toLocaleLowerCase();const category=document.querySelector('#category').value;const day=document.querySelector('#day').value;
  let rows=rank(candidates,view==='interest'?prefs:{...prefs,enabled:false});
  rows=rows.filter(a=>(!search||(a.title+' '+a.summaryBullets.join(' ')).toLocaleLowerCase().includes(search))&&(!category||a.tags.some(t=>t.category===category))&&(!day||new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Seoul'}).format(new Date(a.publishedAt||a.collectedAt))===day));
  paper.replaceChildren();if(!rows.length)paper.append(el('p','현재 내려받은 후보에서 해당 기사를 찾지 못했습니다.'));
  for(const [index,article]of rows.entries())paper.append(articleNode(article,index));
  const latest=document.querySelector('#latest');latest.replaceChildren();if(view==='interest'){latest.append(el('h2','일반 최신뉴스'));for(const a of rank(candidates,{...prefs,enabled:false}).slice(0,4)){const p=el('p');const link=el('a',a.title);link.href=safeUrl(a.originalUrl);link.target='_blank';link.rel='noopener noreferrer';p.append(link);latest.append(p);}if(!rows.some(a=>a.matchedTags.length))status.textContent='현재 후보에 관심 태그와 일치하는 기사가 부족합니다. 최신순 지면도 확인하세요.';}
}
function safeUrl(value){try{const u=new URL(value);return ['http:','https:'].includes(u.protocol)?u.href:'#';}catch{return '#';}}
function articleNode(a,index){const article=el('article',undefined,index===0?'lead':'');article.append(el('div',a.tags.map(t=>t.category).filter((v,i,x)=>x.indexOf(v)===i).join(' · ')||'새 소식','eyebrow'));const heading=el('h2');const link=el('a',a.title);link.href=safeUrl(a.originalUrl);link.target='_blank';link.rel='noopener noreferrer';heading.append(link);article.append(heading);
  if(a.summaryBullets.length){const list=el('ul');for(const bullet of a.summaryBullets)list.append(el('li',bullet));article.append(list);}else article.append(el('p','요약 정보 부족 · 원문에서 확인하세요.','muted'));
  article.append(el('p',`${a.source.site} · ${a.publishedAt?'발행':'수집'} ${date(a.publishedAt||a.collectedAt)}`,'byline'));
  article.append(el('p',`${a.summaryMode==='MANUAL'?'수동 편집':'자동 추출'} · 원문 언어 ${a.source.language} (번역하지 않음)`,'byline'));
  if(view==='interest'&&a.matchedTags.length)article.append(el('p','일치 태그: '+a.matchedTags.map(t=>t.name).join(', '),'reason'));
  const tags=el('div',undefined,'tags');for(const tag of a.tags){const group=el('span');group.append(el('span',tag.name));for(const [label,delta]of [['더 보기',1],['덜 보기',-1]]){const b=el('button',label);b.type='button';b.setAttribute('aria-label',tag.name+' '+label);b.addEventListener('click',async()=>{const w=prefs.weights[tag.id]||0;if(delta>0){prefs.weights[tag.id]=Math.min(5,w+1);prefs.excludedTags=prefs.excludedTags.filter(id=>id!==tag.id);}else if(w>1)prefs.weights[tag.id]=w-1;else{delete prefs.weights[tag.id];prefs.excludedTags=[...new Set([...prefs.excludedTags,tag.id])];}await savePreferences(user.id,prefs);render();});group.append(b);}tags.append(group);}article.append(tags);return article;
}
async function load(initial){more.disabled=true;status.textContent='지면을 불러오는 중입니다.';try{const page=await request(initial?'/api/v1/news/edition':'/api/v1/news/page?cursor='+encodeURIComponent(next));candidates=[...new Map([...candidates,...page.items].map(a=>[a.id,a])).values()];next=page.nextCursor;more.hidden=!next;document.querySelector('#range').textContent=`최근 ${page.periodDays}일 · 공통 후보 ${page.candidateCount}개 · 현재 ${candidates.length}개 로드 · 기준 ${date(page.editionAt)}`;status.textContent='';render();}catch(error){report(error);}finally{more.disabled=false;}}
try{user=await account();tagData=await request('/api/v1/tags');prefs=canonicalize(await loadPreferences(user.id),tagData.redirects);await savePreferences(user.id,prefs);for(const c of [...new Set(tagData.tags.map(t=>t.category))]){const option=el('option',c);option.value=c;document.querySelector('#category').append(option);}await load(true);}catch(e){status.textContent='로그인하면 실제 뉴스 지면을 읽을 수 있습니다.';more.hidden=true;}
more.addEventListener('click',()=>load(false));for(const id of ['search','category','day'])document.querySelector('#'+id).addEventListener('input',()=>{if(user)render();});for(const b of document.querySelectorAll('[data-view]'))b.addEventListener('click',()=>{view=b.dataset.view;for(const other of document.querySelectorAll('[data-view]'))other.setAttribute('aria-pressed',String(other===b));if(user)render();});
