import {request} from '../shared/api.js';
import{el,originalEl}from'../shared/dom.js';
import {t} from '../shared/i18n.js';
import {loadTopics} from './topics.js';

export function initializeTopicManagement() {
  const form=document.querySelector('#tag-form'),choice=document.querySelector('#topic-edit-choice');
  const aliasForm=document.querySelector('#alias-form'),mergeForm=document.querySelector('#merge-form'),note=document.querySelector('#tag-result');
  let catalog,selected='',busy=false,missingSelection=false;
  function availability() {
    for(const field of [form,aliasForm,mergeForm])field.inert=busy||!catalog;
    choice.disabled=busy||!catalog;
    for(const button of document.querySelectorAll('#topics-panel-dictionary button'))button.disabled=busy||(!catalog&&button.id!=='refresh-topic-catalog');
  }
  function options(select,empty,exclude='') {
    const value=select.value;select.replaceChildren();const blank=el('option',empty);blank.value='';select.append(blank);
    for(const tag of catalog.tags){if(tag.id===exclude)continue;const option=originalEl('option',`${tag.name} · ${tag.category}`);option.value=tag.id;select.append(option);}
    select.value=[...select.options].some(option=>option.value===value)?value:'';
  }
  async function refresh() {
    catalog=await loadTopics(true);options(choice,'새 주제 만들기');options(aliasForm.elements.tagId,'주제를 선택하세요.');
    options(mergeForm.elements.oldId,'합칠 주제를 선택하세요.');options(mergeForm.elements.canonicalId,'남길 주제를 선택하세요.',mergeForm.elements.oldId.value);
    if(selected&&catalog.tags.some(tag=>tag.id===selected)){choice.value=selected;form.querySelector('[type=submit]').textContent=t('주제 변경 저장');}
    else if(selected){selected='';choice.value='';missingSelection=form.dataset.edited==='true';if(!missingSelection){form.reset();form.querySelector('[type=submit]').textContent=t('새 주제 추가');}note.textContent=t('선택한 주제가 합쳐졌습니다. 다른 주제를 선택하세요.');}
    const list=document.querySelector('#topic-catalog-list');list.replaceChildren();
    for(const tag of catalog.tags){const row=el('li'),name=originalEl('strong',tag.name);
      const aliases=catalog.aliases.filter(alias=>alias.tagId===tag.id).map(alias=>alias.alias);
      row.append(name,originalEl('span',tag.category),aliases.length?originalEl('small',aliases.join(' · ')):el('small','연결한 다른 표현이 없습니다.'));list.append(row);}
  }
  function editSelection() {
    selected=choice.value;missingSelection=false;const tag=catalog?.tags.find(tag=>tag.id===selected);form.elements.name.value=tag?.name||'';form.elements.category.value=tag?.category||'';
    form.querySelector('[type=submit]').textContent=t(tag?'주제 변경 저장':'새 주제 추가');
  }
  async function action(work,message='저장했습니다.') {
    if(busy)return;busy=true;availability();
    try{await work();await refresh();note.textContent=t(message);}
    catch(error){note.textContent=t(error.message);}
    finally{busy=false;availability();}
  }
  choice.addEventListener('change',()=>{if(form.dataset.edited==='true'&&!confirm(t('입력한 주제 변경을 버릴까요?'))){choice.value=selected;return;}form.dataset.edited='';editSelection();});
  form.addEventListener('input',()=>{form.dataset.edited='true';});
  document.querySelector('#new-topic').addEventListener('click',()=>{if(form.dataset.edited==='true'&&!confirm(t('입력한 주제 변경을 버릴까요?')))return;choice.value='';form.dataset.edited='';editSelection();form.elements.name.focus();});
  document.querySelector('#refresh-topic-catalog').addEventListener('click',()=>action(async()=>{},'목록을 불러왔습니다.'));
  form.addEventListener('submit',event=>{event.preventDefault();if(missingSelection){note.textContent=t('원래 주제가 변경되었습니다. 입력은 보관 중이며 주제를 다시 선택해야 저장할 수 있습니다.');return;}action(async()=>{const result=await request('/api/v1/admin/tags'+(selected?'/'+encodeURIComponent(selected):''),{method:selected?'PATCH':'POST',body:{name:form.elements.name.value,category:form.elements.category.value}});if(result?.id)selected=result.id;form.dataset.edited='';});});
  aliasForm.addEventListener('submit',event=>{event.preventDefault();action(()=>request('/api/v1/admin/tags/'+encodeURIComponent(aliasForm.elements.tagId.value)+'/aliases',{method:'POST',body:{alias:aliasForm.elements.alias.value}}));});
  mergeForm.elements.oldId.addEventListener('change',()=>options(mergeForm.elements.canonicalId,'남길 주제를 선택하세요.',mergeForm.elements.oldId.value));
  mergeForm.addEventListener('submit',event=>{event.preventDefault();const old=mergeForm.elements.oldId,target=mergeForm.elements.canonicalId;
    if(!old.value||!target.value||old.value===target.value)return;
    if(selected===old.value&&form.dataset.edited==='true'){note.textContent=t('합칠 주제에 저장하지 않은 변경이 있습니다. 먼저 저장하세요.');return;}
    if(!confirm(`${old.selectedOptions[0].textContent} → ${target.selectedOptions[0].textContent}\n${t('기사와 기존 관심사 연결도 남길 주제로 옮깁니다. 합칠까요?')}`))return;
    action(()=>request('/api/v1/admin/tags/'+encodeURIComponent(old.value)+'/merge',{method:'POST',body:{canonicalId:target.value}}));});
  busy=true;availability();
  refresh().catch(error=>{note.textContent=t(error.message);}).finally(()=>{busy=false;availability();});
}
