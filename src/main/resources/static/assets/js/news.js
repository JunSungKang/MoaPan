import {request} from './api.js';
import {el,report,date,viewer} from './common.js';
import {loadPreferences,canonicalize,savePreferences,defaults} from './preferences.js';
import {rank,effectivePreferences} from './ranking.js';
const paper=document.querySelector('#paper'),status=document.querySelector('[role="status"]'),more=document.querySelector('#more');
const dialog=document.querySelector('#search-dialog');
let candidates=[],prefs=defaults(),user=null,next,tagData,representative,filters={search:'',category:'',day:''};
let generation=0;
const effective=()=>effectivePreferences(prefs,representative.weights);
window.addEventListener('moapan-clear',()=>{generation++;candidates=[];user=null;next=null;prefs=defaults();filters={search:'',category:'',day:''};for(const id of ['search','category','day'])document.querySelector('#'+id).value='';document.querySelector('#search-summary').textContent='';document.querySelector('#clear-search').hidden=true;paper.replaceChildren();more.hidden=true;dialog.close();status.textContent='로그인 상태가 만료되었습니다. 새로고침하면 대표 관심사 지면을 읽을 수 있습니다.';});
function render(){
 const search=filters.search.toLocaleLowerCase();
 let rows=rank(candidates,effective()).filter(a=>(!search||(a.title+' '+a.summaryBullets.join(' ')).toLocaleLowerCase().includes(search))&&(!filters.category||a.tags.some(t=>t.category===filters.category))&&(!filters.day||new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Seoul'}).format(new Date(a.publishedAt||a.collectedAt))===filters.day));
 paper.replaceChildren();if(!rows.length)paper.append(el('p','현재 불러온 기사에서 검색 조건에 맞는 기사를 찾지 못했습니다.'));
 for(const [index,article]of rows.entries())paper.append(articleNode(article,index));
 document.querySelector('#search-summary').textContent=[filters.search&&('검색: '+filters.search),filters.category,filters.day].filter(Boolean).join(' · ');
 document.querySelector('#clear-search').hidden=!Object.values(filters).some(Boolean);

}
function safeUrl(value){try{const u=new URL(value);return ['http:','https:'].includes(u.protocol)?u.href:'#';}catch{return '#';}}
function articleNode(a,index){const article=el('article',undefined,index===0?'lead':'');article.append(el('div',a.tags.map(t=>t.category).filter((v,i,x)=>x.indexOf(v)===i).join(' · ')||'새 소식','eyebrow'));const heading=el('h2');const link=el('a',a.title);link.href=safeUrl(a.originalUrl);link.target='_blank';link.rel='noopener noreferrer';heading.append(link);article.append(heading);
  if(a.summaryBullets.length){const list=el('ul');for(const bullet of a.summaryBullets)list.append(el('li',bullet));article.append(list);}else article.append(el('p','요약 정보 부족 · 원문에서 확인하세요.','muted'));
  article.append(el('p',`${a.source.site} · ${a.publishedAt?'발행':'수집'} ${date(a.publishedAt||a.collectedAt)}`,'byline'));
  article.append(el('p',`${a.summaryMode==='MANUAL'?'수동 편집':'자동 추출'} · 원문 언어 ${a.source.language} (번역하지 않음)`,'byline'));
  if(a.matchedTags.length)article.append(el('p','일치 태그: '+a.matchedTags.map(t=>t.name).join(', '),'reason'));
  const tags=el('div',undefined,'tags');for(const tag of a.tags){const group=el('span');group.append(el('span',tag.name));if(user)for(const [label,delta]of [['더 보기',1],['덜 보기',-1]]){const b=el('button',label);b.type='button';b.setAttribute('aria-label',tag.name+' '+label);b.addEventListener('click',async()=>{prefs={...prefs,enabled:true,weights:{...effective().weights}};const w=prefs.weights[tag.id]||0;if(delta>0){prefs.weights[tag.id]=Math.min(5,w+1);prefs.excludedTags=prefs.excludedTags.filter(id=>id!==tag.id);}else if(w>1)prefs.weights[tag.id]=w-1;else{delete prefs.weights[tag.id];prefs.excludedTags=[...new Set([...prefs.excludedTags,tag.id])];}await savePreferences(user.id,prefs);render();});group.append(b);}tags.append(group);}article.append(tags);return article;
}

async function load(initial){const ticket=generation;more.disabled=true;status.textContent='지면을 불러오는 중입니다.';try{const path=!user?'/api/v1/public/news/edition':initial?'/api/v1/news/edition':'/api/v1/news/page?cursor='+encodeURIComponent(next);const page=await request(path);if(ticket!==generation)return;candidates=[...new Map([...candidates,...page.items].map(a=>[a.id,a])).values()];next=page.nextCursor;more.hidden=!user||!next;status.textContent='';render();}catch(error){report(error);}finally{more.disabled=false;}}
try{user=await viewer;tagData=await request('/api/v1/public/tags');representative=await request('/api/v1/public/interests');representative.weights=canonicalize({...defaults(),weights:representative.weights},tagData.redirects).weights;if(user)prefs=canonicalize(await loadPreferences(user.id),tagData.redirects);for(const c of [...new Set(tagData.tags.map(t=>t.category))]){const option=el('option',c);option.value=c;document.querySelector('#category').append(option);}await load(true);}catch(e){report(e);more.hidden=true;}
more.addEventListener('click',()=>load(false));
document.querySelector('#open-search').addEventListener('click',()=>{for(const id of ['search','category','day'])document.querySelector('#'+id).value=filters[id];dialog.showModal();});
document.querySelector('#cancel-search').addEventListener('click',()=>dialog.close());
document.querySelector('#search-form').addEventListener('submit',e=>{e.preventDefault();for(const id of ['search','category','day'])filters[id]=document.querySelector('#'+id).value;dialog.close();status.textContent='';if(representative)render();});
function resetSearch(){filters={search:'',category:'',day:''};dialog.close();status.textContent='';if(representative)render();}
document.querySelector('#reset-search').addEventListener('click',resetSearch);document.querySelector('#clear-search').addEventListener('click',resetSearch);

dialog.addEventListener('keydown',e=>{if(e.key==='Escape'){e.preventDefault();dialog.close();}});
