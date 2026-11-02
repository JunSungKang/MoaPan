import {request} from '../shared/api.js';
import {el,date} from '../shared/common.js';
import {t} from '../shared/i18n.js';

const koreanTime=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Seoul',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'});

/** Operator lookup only; no username in URL, storage, history or logs. */
export function initializeSubscriptions() {
  const search=document.querySelector('#subscription-search'),list=document.querySelector('#subscription-accounts');
  const form=document.querySelector('#subscription-form'),note=document.querySelector('#subscription-status');
  let page=0,selected=null,generation=0,query='',loading=false,saving=false,hasNext=false,controller;
  const previous=document.querySelector('#subscription-prev'),next=document.querySelector('#subscription-next');
  const states={NONE:'미구독',ACTIVE:'이용 중',SCHEDULED:'시작 예정',EXPIRED:'만료',REVOKED:'권한 회수'};
  function local(instant){const parts=koreanTime.format(new Date(instant));return parts.replace(' ','T');}
  function select(row){if(loading||saving)return;selected=row;document.querySelector('#subscription-selected').dataset.originalContent='';document.querySelector('#subscription-selected').textContent=row.username;form.hidden=false;form.elements.startsAt.value=local(row.startsAt||new Date().toISOString());form.elements.expiresAt.value=row.expiresAt?local(row.expiresAt):'';document.querySelector('#subscription-revoke').disabled=!row.version||row.subscriptionStatus==='REVOKED';}
  function controls(){
    search.querySelector('button').disabled=loading||saving;
    previous.disabled=loading||saving||page===0;next.disabled=loading||saving||!hasNext;
    for(const field of search.querySelectorAll('input,select'))field.disabled=saving;
    for(const field of form.querySelectorAll('input,button'))field.disabled=loading||saving;
    document.querySelector('#subscription-revoke').disabled=loading||saving||!selected||!selected.version||selected.subscriptionStatus==='REVOKED';
  }
  async function load(){
    const ticket=++generation;controller?.abort();controller=new AbortController();loading=true;controls();previous.disabled=true;next.disabled=true;
    list.setAttribute('aria-busy','true');list.replaceChildren();selected=null;form.hidden=true;document.querySelector('#subscription-selected').textContent=t('계정을 선택하세요.');note.textContent=t('계정을 불러오고 있습니다.');
    const criteria={username:query,page,size:Number(search.elements.size.value),sort:search.elements.sort.value};
    try{const result=await request('/api/v1/admin/subscriptions/search',{method:'POST',body:criteria,signal:controller.signal});if(ticket!==generation)return;
      for(const row of result.items){const tr=el('tr');const choose=el('button',row.username);choose.type='button';choose.dataset.originalContent='';choose.addEventListener('click',()=>select(row));const name=el('td');name.append(choose);tr.append(name,el('td',t(states[row.subscriptionStatus])+(row.active?'':' · '+t('계정 비활성'))),el('td',row.expiresAt?date(row.expiresAt):'—'));list.append(tr);}
      if(!result.items.length){const tr=el('tr'),empty=el('td',t('검색 결과가 없습니다.'));empty.colSpan=3;tr.append(empty);list.append(tr);}
      hasNext=result.hasNext;document.querySelector('#subscription-page').textContent=String(page+1);note.textContent=t(result.items.length?'계정을 선택해 이용 기간을 설정하세요.':'검색 결과가 없습니다.');
    }catch(e){if(ticket===generation&&e.name!=='AbortError')note.textContent=t(e.message);}finally{if(ticket===generation){loading=false;list.removeAttribute('aria-busy');controls();}}
  }
  async function mutate(revoke){if(!selected||loading||saving)return;saving=true;controls();previous.disabled=true;next.disabled=true;const row=selected;
    try{const body=revoke?{version:row.version}:{version:row.version,startsAt:new Date(form.elements.startsAt.value+'+09:00').toISOString(),expiresAt:new Date(form.elements.expiresAt.value+'+09:00').toISOString()};
      await request('/api/v1/admin/subscriptions/'+row.id+(revoke?'/revoke':''),{method:revoke?'POST':'PUT',body});await load();note.textContent=t(revoke?'구독 권한을 회수했습니다.':'구독 이용 기간을 저장했습니다.');
    }catch(e){note.textContent=t(e.status===409?'다른 관리자가 먼저 변경했습니다. 계정을 다시 조회한 뒤 수정해 주세요.':e.message);}finally{saving=false;controls();}
  }
  function filter(){if(saving)return;query=search.elements.username.value;page=0;load();}
  search.addEventListener('submit',e=>{e.preventDefault();filter();});
  search.elements.sort.addEventListener('change',filter);search.elements.size.addEventListener('change',filter);
  previous.addEventListener('click',()=>{if(!loading&&!saving&&page>0){page--;load();}});
  next.addEventListener('click',()=>{if(!loading&&!saving){page++;load();}});
  form.addEventListener('submit',e=>{e.preventDefault();mutate(false);});document.querySelector('#subscription-revoke').addEventListener('click',()=>mutate(true));
  // Load once when this tab first becomes visible; preserve an editor's inputs on later tab switches.
  const panel=document.querySelector('#sections-panel-subscriptions');
  if(!panel.hidden)load();else{const observer=new MutationObserver(()=>{if(!panel.hidden){observer.disconnect();load();}});observer.observe(panel,{attributes:true,attributeFilter:['hidden']});}
}
