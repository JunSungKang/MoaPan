import {initialEdition,loadInitialEdition} from './initial-data.js';
import {t} from '../shared/i18n.js';
import {articleCard} from '../article/card.js';
import {slideWindow} from './slides.js';
import {request} from '../shared/api.js';
import {el,report,viewer,setIssueDate} from '../shared/common.js';
import {loadPreferences,canonicalize,defaults} from '../personalization/preferences.js';
import {rank,effectivePreferences} from '../personalization/ranking.js';
const paper=document.querySelector('#paper'),status=document.querySelector('[role="status"]');
const dialog=document.querySelector('#search-dialog');
let candidates=[],prefs=defaults(),user=null,tagData,representative,filters={search:'',category:'',day:''};
let generation=0,slide=0,searchActive=false,searchPage=null,searchBusy=false,searchTicket=0;
const effective=()=>effectivePreferences(prefs,representative.weights);
window.addEventListener('moapan-clear',()=>{generation++;searchTicket++;searchActive=false;searchPage=null;searchBusy=false;candidates=[];user=null;prefs=defaults();filters={search:'',category:'',day:''};for(const id of ['search','category','day'])document.querySelector('#'+id).value='';document.querySelector('#search-summary').textContent='';document.querySelector('#clear-search').hidden=true;paper.replaceChildren();setIssueDate([]);dialog.close();status.textContent='';if(representative)load();else bootstrap(Promise.resolve(null),loadInitialEdition());});
function render(){
 setIssueDate(candidates);
 const window=searchActive?{items:searchPage?.items||[],page:searchPage?.page||0,pages:searchPage?.pages||0}:slideWindow(rank(candidates,effective()),slide);
 slide=window.page;
 const content=document.createDocumentFragment();if(!window.items.length&&!searchBusy)content.append(el('p',searchActive?'검색 조건에 맞는 기사가 없습니다.':'표시할 기사가 없습니다.'));
 for(const [index,article]of window.items.entries())content.append(articleCard(article,index));
 paper.replaceChildren(content);
 document.querySelector('#slide-position').textContent=window.pages?(slide+1)+' / '+window.pages:'0 / 0';
 document.querySelector('#previous-slide').disabled=searchBusy||slide===0;
 document.querySelector('#next-slide').disabled=searchBusy||!window.pages||slide>=window.pages-1;
 document.querySelector('#search-summary').textContent=[filters.search&&(t('검색:')+' '+filters.search),filters.category,filters.day].filter(Boolean).join(' · ');
 document.querySelector('#clear-search').hidden=!searchActive;

}

async function load(initialPage){const ticket=generation;paper.setAttribute('aria-busy','true');status.textContent='지면을 불러오는 중입니다.';try{const page=initialPage??await request('/api/v1/public/news/edition');if(ticket!==generation)return;candidates=[...new Map(page.items.map(a=>[a.id,a])).values()];status.textContent='';render();}catch(error){report(error);}finally{if(ticket===generation)paper.setAttribute('aria-busy','false');}}
async function bootstrap(accountResult=viewer,editionResult=initialEdition){
 const ticket=generation;paper.setAttribute('aria-busy','true');status.textContent='지면을 불러오는 중입니다.';
 try{
  const [current,[dictionary,config,firstPage]]=await Promise.all([accountResult,editionResult]);
  if(ticket!==generation)return;
  const settings=current?await loadPreferences(current.id):defaults();if(ticket!==generation)return;
  user=current;tagData=dictionary;representative=config;
  representative.weights=canonicalize({...defaults(),weights:representative.weights},tagData.redirects).weights;
  prefs=canonicalize(settings,tagData.redirects);
  for(const category of [...new Set(tagData.tags.map(tag=>tag.category))]){const option=el('option',category);option.value=category;document.querySelector('#category').append(option);}
  await load(firstPage);
 }catch(error){if(ticket===generation)report(error);}
 finally{if(ticket===generation)paper.setAttribute('aria-busy','false');}
}

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

bootstrap();
