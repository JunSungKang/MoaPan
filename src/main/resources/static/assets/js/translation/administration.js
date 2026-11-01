import {request} from '../shared/api.js';
import {el,report} from '../shared/dom.js';

export function translationEditor(id,{onBusy=()=>{},onSaved=()=>{}}={}){
 const details=el('details'),heading=el('summary','번역 수정·잠금');details.append(heading);
 const language=el('select');language.setAttribute('aria-label','번역 언어');
 for(const [value,label]of [['ko','한국어'],['en','English']]){const option=el('option',label);option.value=value;language.append(option);}
 const load=el('button','번역 불러오기');load.type='button';
 const form=el('form'),title=el('input'),bullets=el('textarea'),locked=el('input'),save=el('button','번역 저장');
 title.maxLength=1000;title.required=true;title.setAttribute('aria-label','번역 제목');bullets.setAttribute('aria-label','번역 요약 (한 줄 한 항목)');
 locked.type='checkbox';const lockLabel=el('label','자동 처리로 덮어쓰지 않기');lockLabel.append(locked);
 form.append(title,bullets,lockLabel,save);save.disabled=true;let snapshot=null,epoch=0,saving=false,revision=0;
 const edits=new WeakMap(),note=el('p');note.setAttribute('aria-live','polite');
 const changed=event=>edits.set(event.target,++revision);
 form.addEventListener('input',changed);form.addEventListener('change',changed);
 language.addEventListener('change',()=>{epoch++;snapshot=null;save.disabled=true;title.value='';bullets.value='';note.textContent='';});
 load.addEventListener('click',async()=>{
  if(saving)return;const current=++epoch,fields=[title,bullets,locked],before=fields.map(field=>edits.get(field)||0);save.disabled=true;
  try{const result=await request('/api/v1/admin/translations/'+id+'/'+language.value);if(current!==epoch)return;
   snapshot=result;const values=[result.title,result.summaryBullets.join('\n'),result.locked];let preserved=false;
   fields.forEach((field,index)=>{if((edits.get(field)||0)!==before[index]){preserved=true;return;}if(field.type==='checkbox')field.checked=values[index];else field.value=values[index];});
   note.textContent=preserved?'입력 중인 내용을 유지했습니다.':'';save.disabled=false;
  }catch(error){if(current===epoch){note.textContent=error.message;report(error);}}
 });
 form.addEventListener('submit',async event=>{
  event.preventDefault();if(!snapshot||saving)return;
  saving=true;save.disabled=true;language.disabled=true;load.disabled=true;form.inert=true;onBusy(form,true);
  let reload=false;
  try{
   await request('/api/v1/admin/translations/'+id+'/'+language.value,{method:'PUT',body:{version:snapshot.version,contentHash:snapshot.contentHash,title:title.value,summaryBullets:bullets.value.split('\n').filter(Boolean),locked:locked.checked}});
   onSaved(form);snapshot=null;reload=true;
  }catch(error){snapshot=null;report(error);}
  finally{saving=false;language.disabled=false;load.disabled=false;form.inert=false;onBusy(form,false);}
  if(reload&&details.isConnected)load.click();
 });
 details.append(language,load,note,form);return details;
}
