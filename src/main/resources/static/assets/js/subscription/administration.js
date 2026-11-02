import {request} from '../shared/api.js';
import {el,date} from '../shared/common.js';
import {t} from '../shared/i18n.js';

const koreanTime=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Seoul',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'});

/** Operator lookup only; no username in URL, storage, history or logs. */
export function initializeSubscriptions() {
  const search=document.querySelector('#subscription-search'),list=document.querySelector('#subscription-accounts');
  const form=document.querySelector('#subscription-form'),note=document.querySelector('#subscription-status');
  let page=0,selected=null,generation=0,query='',busy=false;
  const states={NONE:'미구독',ACTIVE:'이용 중',SCHEDULED:'시작 예정',EXPIRED:'만료',REVOKED:'권한 회수'};
  function local(instant){const parts=koreanTime.format(new Date(instant));return parts.replace(' ','T');}
  function select(row){selected=row;document.querySelector('#subscription-selected').dataset.originalContent='';document.querySelector('#subscription-selected').textContent=row.username;form.hidden=false;form.elements.startsAt.value=local(row.startsAt||new Date().toISOString());form.elements.expiresAt.value=row.expiresAt?local(row.expiresAt):'';document.querySelector('#subscription-revoke').disabled=!row.version||row.subscriptionStatus==='REVOKED';}
  async function load(){
    const ticket=++generation;busy=true;search.querySelector('button').disabled=true;note.textContent=t('계정을 불러오고 있습니다.');
    try{const result=await request('/api/v1/admin/subscriptions/search',{method:'POST',body:{username:query,page,size:20}});if(ticket!==generation)return;list.replaceChildren();selected=null;form.hidden=true;document.querySelector('#subscription-selected').textContent=t('계정을 선택하세요.');
      for(const row of result.items){const tr=el('tr');const choose=el('button',row.username);choose.type='button';choose.dataset.originalContent='';choose.addEventListener('click',()=>select(row));const name=el('td');name.append(choose);tr.append(name,el('td',t(states[row.subscriptionStatus])+(row.active?'':' · '+t('계정 비활성'))),el('td',row.expiresAt?date(row.expiresAt):'—'));list.append(tr);}
      document.querySelector('#subscription-prev').disabled=page===0;document.querySelector('#subscription-next').disabled=!result.hasNext;document.querySelector('#subscription-page').textContent=String(page+1);note.textContent=t(result.items.length?'계정을 선택해 이용 기간을 설정하세요.':'검색 결과가 없습니다.');
    }catch(e){note.textContent=e.message;}finally{if(ticket===generation){busy=false;search.querySelector('button').disabled=false;}}
  }
  async function mutate(revoke){if(!selected||busy)return;busy=true;const buttons=[...form.querySelectorAll('button')];buttons.forEach(b=>b.disabled=true);const row=selected;
    try{const body=revoke?{version:row.version}:{version:row.version,startsAt:new Date(form.elements.startsAt.value+'+09:00').toISOString(),expiresAt:new Date(form.elements.expiresAt.value+'+09:00').toISOString()};
      await request('/api/v1/admin/subscriptions/'+row.id+(revoke?'/revoke':''),{method:revoke?'POST':'PUT',body});await load();note.textContent=t(revoke?'구독 권한을 회수했습니다.':'구독 이용 기간을 저장했습니다.');
    }catch(e){note.textContent=t(e.status===409?'다른 관리자가 먼저 변경했습니다. 계정을 다시 조회한 뒤 수정해 주세요.':e.message);}finally{busy=false;buttons.forEach(b=>b.disabled=false);document.querySelector('#subscription-revoke').disabled=!selected||!selected.version||selected.subscriptionStatus==='REVOKED';}
  }
  search.addEventListener('submit',e=>{e.preventDefault();if(busy)return;query=search.elements.username.value;page=0;load();});
  document.querySelector('#subscription-prev').addEventListener('click',()=>{if(!busy&&page>0){page--;load();}});
  document.querySelector('#subscription-next').addEventListener('click',()=>{if(!busy){page++;load();}});
  form.addEventListener('submit',e=>{e.preventDefault();mutate(false);});document.querySelector('#subscription-revoke').addEventListener('click',()=>mutate(true));
}
