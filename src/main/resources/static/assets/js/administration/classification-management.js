import {request} from '../shared/api.js';
import {el} from '../shared/common.js';
import {t} from '../shared/i18n.js';
import {loadTopics} from './topics.js';

export function initializeClassificationManagement(){
  const form=document.querySelector('#classification-form'),root=document.querySelector('#classification-topics');
  const note=document.querySelector('#classification-message'),refresh=document.querySelector('#refresh-classification');
  let version=null,dirty=false,loading=false,saving=false,blocked=true;
  const save=form.querySelector('[type=submit]');save.disabled=true;
  async function load(){
    if(loading)return;loading=true;refresh.disabled=true;form.inert=true;
    try{
      const [data,dictionary]=await Promise.all([request('/api/v1/admin/classification'),loadTopics(true)]);
      if(!data?.catalog?.topics)throw new Error(t('분류 기준을 불러오지 못했습니다.'));
      const available=new Set(dictionary.tags.map(tag=>tag.id));
      if(data.catalog.topics.some(topic=>!available.has(topic.id)))throw new Error(t('주제 목록이 변경 중입니다. 입력은 유지되며 다시 불러온 뒤 저장할 수 있습니다.'));
      const selected=new Map(data.catalog.topics.map(topic=>[topic.id,topic]));root.replaceChildren();
      for(const tag of dictionary.tags){
        const row=el('div',undefined,'classification-choice'),label=el('label',undefined,'check'),check=el('input');
        check.type='checkbox';check.value=tag.id;check.checked=selected.has(tag.id);check.id='classify-'+tag.id;
        const name=el('span',tag.name);name.dataset.originalContent='';label.append(check,name);
        const definition=el('textarea');definition.maxLength=200;definition.value=selected.get(tag.id)?.definition||'';
        definition.placeholder=t('포함할 내용과 제외할 내용을 적어 주세요.');definition.required=check.checked;definition.disabled=!check.checked;
        definition.setAttribute('aria-label',tag.name+' '+t('분류 기준'));
        check.addEventListener('change',()=>{definition.required=check.checked;definition.disabled=!check.checked;dirty=true;});
        row.append(label,definition);root.append(row);
      }
      version=data.catalog.version;dirty=false;blocked=false;
      document.querySelector('#classification-status').textContent=JSON.stringify({counts:data.counts,runtime:data.runtime,recent:data.recent},null,2);
      note.textContent=t('사용할 주제를 체크하고 분류 기준을 적은 뒤 저장하세요.');
    }catch(error){blocked=true;note.textContent=t(error.message);}
    finally{loading=false;refresh.disabled=saving;form.inert=saving;save.disabled=blocked;}
  }
  form.addEventListener('input',()=>{dirty=true;});
  refresh.addEventListener('click',()=>{if(dirty&&!confirm(t('입력한 분류 기준을 버리고 다시 불러올까요?')))return;load();});
  form.addEventListener('submit',async event=>{
    event.preventDefault();if(saving||loading||version===null||blocked)return;
    const topics=[...root.querySelectorAll('.classification-choice')].filter(row=>row.querySelector('input').checked).map(row=>({tagId:row.querySelector('input').value,definition:row.querySelector('textarea').value}));
    if(topics.length<1||topics.length>8){note.textContent=t('자동 분류 주제는 1~8개 선택하세요.');return;}
    saving=true;form.inert=true;refresh.disabled=true;
    try{await request('/api/v1/admin/classification/topics',{method:'PUT',body:{version,topics}});version=null;await load();note.textContent=t(blocked?'저장은 완료했지만 분류 기준을 다시 불러와야 합니다.':'저장했습니다. 기존 기사는 순차 재분류됩니다.');}
    catch(error){note.textContent=t(error.message);}
    finally{saving=false;form.inert=false;refresh.disabled=false;}
  });
  const ensure=()=>{if(version===null&&!loading)load();};
  document.querySelector('#sections-tab-topics').addEventListener('click',ensure);
  document.querySelector('#topics-tab-classification').addEventListener('click',ensure);
  window.addEventListener('moapan-topic-catalog',()=>{if(version!==null&&!loading)note.textContent=t('주제 목록이 바뀌었습니다. 분류 기준을 다시 불러와 확인하세요.');});
  if(!document.querySelector('#sections-panel-topics').hidden)ensure();
}
