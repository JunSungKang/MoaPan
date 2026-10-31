import {request} from '../shared/api.js';
import {el,report} from '../shared/common.js';
export function translationEditor(id){
 const details=el('details'),heading=el('summary','번역 수정·잠금');details.append(heading);
 const language=el('select');language.setAttribute('aria-label','번역 언어');for(const [value,label]of [['ko','한국어'],['en','English']]){const option=el('option',label);option.value=value;language.append(option);}
 const load=el('button','번역 불러오기');load.type='button';const form=el('form'),title=el('input'),bullets=el('textarea'),locked=el('input'),save=el('button','번역 저장');title.maxLength=1000;title.required=true;title.setAttribute('aria-label','번역 제목');bullets.setAttribute('aria-label','번역 요약 (한 줄 한 항목)');locked.type='checkbox';const lockLabel=el('label','자동 처리로 덮어쓰지 않기');lockLabel.append(locked);form.append(title,bullets,lockLabel,save);save.disabled=true;let snapshot=null,epoch=0;
 language.addEventListener('change',()=>{epoch++;snapshot=null;save.disabled=true;title.value='';bullets.value='';});
 load.addEventListener('click',async()=>{const current=++epoch;save.disabled=true;try{const result=await request('/api/v1/admin/translations/'+id+'/'+language.value);if(current!==epoch)return;snapshot=result;title.value=result.title;bullets.value=result.summaryBullets.join('\n');locked.checked=result.locked;save.disabled=false;}catch(error){report(error);}});
 form.addEventListener('submit',async event=>{event.preventDefault();if(!snapshot)return;save.disabled=true;try{await request('/api/v1/admin/translations/'+id+'/'+language.value,{method:'PUT',body:{version:snapshot.version,contentHash:snapshot.contentHash,title:title.value,summaryBullets:bullets.value.split('\n').filter(Boolean),locked:locked.checked}});snapshot=null;load.click();}catch(error){snapshot=null;report(error);}});
 details.append(language,load,form);return details;
}
