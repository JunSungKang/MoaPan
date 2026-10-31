import {language,t} from '../shared/i18n.js';
import {articleText} from '../article/article-text.js';
import {slideWindow} from './slides.js';
import {request} from '../shared/api.js';
import {el,report,date,viewer,setIssueDate} from '../shared/common.js';
import {loadPreferences,canonicalize,defaults} from '../personalization/preferences.js';
import {rank,effectivePreferences} from '../personalization/ranking.js';
const paper=document.querySelector('#paper'),status=document.querySelector('[role="status"]'),more=document.querySelector('#more');
const dialog=document.querySelector('#search-dialog');
let candidates=[],prefs=defaults(),user=null,next,tagData,representative,filters={search:'',category:'',day:''};
let generation=0,slide=0,searchActive=false,searchPage=null,searchBusy=false,searchTicket=0;
const effective=()=>effectivePreferences(prefs,representative.weights);
window.addEventListener('moapan-clear',()=>{generation++;searchTicket++;searchActive=false;searchPage=null;searchBusy=false;candidates=[];user=null;next=null;prefs=defaults();filters={search:'',category:'',day:''};for(const id of ['search','category','day'])document.querySelector('#'+id).value='';document.querySelector('#search-summary').textContent='';document.querySelector('#clear-search').hidden=true;paper.replaceChildren();setIssueDate([]);more.hidden=true;dialog.close();status.textContent='';if(representative)load(true);});
function render(){
 setIssueDate(candidates);
 const window=searchActive?{items:searchPage?.items||[],page:searchPage?.page||0,pages:searchPage?.pages||0}:slideWindow(rank(candidates,effective()),slide);
 slide=window.page;
 paper.replaceChildren();if(!window.items.length&&!searchBusy)paper.append(el('p',searchActive?'검색 조건에 맞는 기사가 없습니다.':'표시할 기사가 없습니다.'));
 for(const [index,article]of window.items.entries())paper.append(articleNode(article,index));
 document.querySelector('#slide-position').textContent=window.pages?(slide+1)+' / '+window.pages:'0 / 0';
 document.querySelector('#previous-slide').disabled=searchBusy||slide===0;
 document.querySelector('#next-slide').disabled=searchBusy||!window.pages||slide>=window.pages-1;
 document.querySelector('#search-summary').textContent=[filters.search&&('검색: '+filters.search),filters.category,filters.day].filter(Boolean).join(' · ');
 document.querySelector('#clear-search').hidden=!searchActive;

}
function safeUrl(value){try{const u=new URL(value);return ['http:','https:'].includes(u.protocol)?u.href:'#';}catch{return '#';}}
function articleNode(a,index){
 const text=articleText(a,language);
 const article=el('article',undefined,index===0?'lead':'');
 const heading=el('h2'),link=el('a',text.title);link.href=safeUrl(a.originalUrl);link.target='_blank';link.rel='noopener noreferrer';link.dataset.originalContent='';link.lang=text.language;heading.append(link);article.append(heading);
 const line=el('div',undefined,'article-meta-line');
 const subjects=el('span',a.tags.map(tag=>t(tag.name)).join(' · ')||'미분류','article-subjects');subjects.setAttribute('aria-label','주제');
 const written=el('span',a.publishedAt?new Intl.DateTimeFormat(language==='en'?'en-GB':'ko-KR',{timeZone:'Asia/Seoul',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}).format(new Date(a.publishedAt)):'정보 없음');written.className='article-written';written.setAttribute('aria-label','작성일');
 const author=el('span',a.author||'정보 없음','article-author');if(a.author)author.dataset.originalContent='';author.title=a.author||'';subjects.title=a.tags.map(t=>t.name).join(' · ');author.setAttribute('aria-label','작성자');
 line.append(subjects,written,author);article.append(line);
 const source=el('p',a.source.site,'article-source');source.setAttribute('aria-label','출처');article.append(source);
 if(text.summaryBullets.length){const list=el('ul');list.dataset.originalContent='';list.lang=text.language;for(const bullet of text.summaryBullets)list.append(el('li',bullet));article.append(list);}
 else article.append(el('p','연구 관점으로 요약할 정보가 부족합니다.','muted'));
 return article;
}

async function load(initial){const ticket=generation;more.disabled=true;status.textContent='지면을 불러오는 중입니다.';try{const path=!user?'/api/v1/public/news/edition':initial?'/api/v1/news/edition?size=42':'/api/v1/news/page?cursor='+encodeURIComponent(next);const page=await request(path);if(ticket!==generation)return;candidates=[...new Map([...candidates,...page.items].map(a=>[a.id,a])).values()];next=page.nextCursor;more.hidden=true;status.textContent='';render();}catch(error){report(error);}finally{more.disabled=false;}}
try{user=await viewer;tagData=await request('/api/v1/public/tags');representative=await request('/api/v1/public/interests');representative.weights=canonicalize({...defaults(),weights:representative.weights},tagData.redirects).weights;if(user)prefs=canonicalize(await loadPreferences(user.id),tagData.redirects);for(const c of [...new Set(tagData.tags.map(t=>t.category))]){const option=el('option',c);option.value=c;document.querySelector('#category').append(option);}await load(true);}catch(e){report(e);more.hidden=true;}
more.addEventListener('click',()=>load(false));
document.querySelector('#open-search').addEventListener('click',()=>{for(const id of ['search','category','day'])document.querySelector('#'+id).value=filters[id];dialog.showModal();});
document.querySelector('#cancel-search').addEventListener('click',()=>dialog.close());
async function search(page){
 const ticket=++searchTicket;searchBusy=true;status.textContent='기사를 검색하는 중입니다.';render();
 try{const result=await request(user?'/api/v1/news/search':'/api/v1/public/news/search',{method:'POST',body:{...filters,day:filters.day||null,page}});if(ticket!==searchTicket)return;searchPage=result;slide=result.page;status.textContent='';}
 catch(error){if(ticket!==searchTicket)return;report(error);}
 finally{if(ticket===searchTicket){searchBusy=false;render();}}
}
document.querySelector('#search-form').addEventListener('submit',e=>{e.preventDefault();for(const id of ['search','category','day'])filters[id]=document.querySelector('#'+id).value;dialog.close();searchActive=true;searchPage=null;slide=0;search(0);});
function resetSearch(){searchTicket++;searchActive=false;searchBusy=false;searchPage=null;filters={search:'',category:'',day:''};dialog.close();slide=0;status.textContent='';if(representative)render();}
document.querySelector('#reset-search').addEventListener('click',resetSearch);document.querySelector('#clear-search').addEventListener('click',resetSearch);

dialog.addEventListener('keydown',e=>{if(e.key==='Escape'){e.preventDefault();dialog.close();}});

function moveSlide(delta){const button=document.querySelector(delta<0?'#previous-slide':'#next-slide');if(button.disabled)return;if(searchActive){search(slide+delta);}else{slide+=delta;render();}}
document.querySelector('#previous-slide').addEventListener('click',()=>moveSlide(-1));
document.querySelector('#next-slide').addEventListener('click',()=>moveSlide(1));
paper.addEventListener('keydown',e=>{if(e.target!==paper)return;if(e.key==='ArrowLeft'||e.key==='ArrowRight'){e.preventDefault();moveSlide(e.key==='ArrowLeft'?-1:1);}});
let swipe=null;
paper.addEventListener('touchstart',e=>{if(e.touches.length===1)swipe={x:e.touches[0].clientX,y:e.touches[0].clientY};},{passive:true});
paper.addEventListener('touchend',e=>{if(!swipe||!e.changedTouches.length)return;const dx=e.changedTouches[0].clientX-swipe.x,dy=e.changedTouches[0].clientY-swipe.y;swipe=null;if(Math.abs(dx)>70&&Math.abs(dx)>Math.abs(dy)*1.5)moveSlide(dx<0?1:-1);},{passive:true});

window.addEventListener('moapan-language',()=>{if(representative)render();});
