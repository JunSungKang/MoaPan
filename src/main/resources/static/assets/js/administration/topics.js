import {request} from '../shared/api.js';
import {el} from '../shared/common.js';
import {t} from '../shared/i18n.js';

let cached, pending;
export function loadTopics(refresh=false) {
  if (pending) return refresh ? pending.catch(()=>{}).then(()=>loadTopics(true)) : pending;
  if (cached && !refresh) return Promise.resolve(cached);
  pending=request('/api/v1/tags').then(data=>{
    cached={...data,tags:[...data.tags].sort((a,b)=>a.name.localeCompare(b.name,'ko')||a.id.localeCompare(b.id))};
    window.dispatchEvent(new CustomEvent('moapan-topic-catalog',{detail:cached}));return cached;
  }).finally(()=>{pending=null;});return pending;
}
export function topicChecklist(ids=[]) {
  const node=el('fieldset',undefined,'topic-checklist');node.append(el('legend','기사 주제'));
  const filterLabel=el('label','주제 찾기'),filter=el('input');filter.type='search';filterLabel.append(filter);
  const choices=el('div',undefined,'topic-choices'),note=el('p','주제를 불러오는 중입니다.');
  node.append(filterLabel,choices,note);let selected=new Set(ids),disposed=false;
  const filterChoices=()=>{for(const label of choices.children)label.hidden=!label.textContent.toLocaleLowerCase().includes(filter.value.toLocaleLowerCase());};
  filter.addEventListener('input',event=>{event.stopPropagation();filterChoices();});filter.addEventListener('change',event=>event.stopPropagation());
  function paint(data) {
    if(disposed)return;const redirects=new Map(data.redirects.map(row=>[row.oldId,row.canonicalId]));
    selected=new Set([...selected].map(id=>{const seen=new Set();while(redirects.has(id)&&!seen.has(id)){seen.add(id);id=redirects.get(id);}return id;}));
    choices.replaceChildren();const known=new Set(data.tags.map(tag=>tag.id));
    const rows=[...data.tags,...[...selected].filter(id=>!known.has(id)).map(id=>({id,name:t('이름을 확인할 수 없는 기존 주제'),category:''}))];
    for(const tag of rows){const label=el('label',undefined,'check'),check=el('input');check.type='checkbox';check.value=tag.id;check.checked=selected.has(tag.id);
      const name=el('span',tag.name);name.dataset.originalContent='';label.append(check,name);
      if(tag.category)label.append(el('small',tag.category));check.addEventListener('change',()=>{if(check.checked)selected.add(tag.id);else selected.delete(tag.id);});choices.append(label);}
    note.textContent=t('최대 6개를 선택하세요. 변경하지 않으면 기존 주제를 유지합니다.');filterChoices();
  }
  loadTopics().then(paint).catch(()=>{note.textContent=t('주제를 불러오지 못했습니다. 기존 선택은 유지됩니다.');});
  const changed=event=>{if(node.isConnected)paint(event.detail);};window.addEventListener('moapan-topic-catalog',changed);
  return {node,values:()=>[...selected],dispose:()=>{disposed=true;window.removeEventListener('moapan-topic-catalog',changed);}};
}
