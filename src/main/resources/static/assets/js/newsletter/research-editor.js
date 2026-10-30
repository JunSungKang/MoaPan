import {t} from '../shared/i18n.js';
import {el,date} from '../shared/common.js';
export function blankDossier(weekStart){const now=new Date(),day=new Date(now.getTime()+9*3600000);const monday=new Date(Date.UTC(day.getUTCFullYear(),day.getUTCMonth(),day.getUTCDate()-((day.getUTCDay()+6)%7)));const week=weekStart||monday.toISOString().slice(0,10),end=new Date(week+'T00:00:00+09:00').getTime()+7*86400000;return {weekStart:week,cutoff:new Date(Math.min(now.getTime(),end-1)).toISOString(),articleIds:[],forecasts:[],rightsConfirmed:false,editorialReviewed:false,correctionNote:''};}
export function researchEditor(onChange){
 let state=blankDossier(),evidence=[],expectedWeek=state.weekStart;
 const root=document.querySelector('#research-panel'),rows=document.querySelector('#research-evidence'),forecasts=document.querySelector('#research-forecasts');
 const invalid=(rights=false)=>{state.editorialReviewed=false;document.querySelector('#editorial-reviewed').checked=false;if(rights){state.rightsConfirmed=false;document.querySelector('#rights-confirmed').checked=false;}document.querySelector('#publication-review').replaceChildren();};
 function changed(review=false){document.querySelector('#publication-review').replaceChildren();if(!review)invalid();onChange();}
 function paint(){
  document.querySelector('#research-week').value=state.weekStart;document.querySelector('#roll-research-week').hidden=state.weekStart===expectedWeek;try{document.querySelector('#research-cutoff').textContent=date(state.cutoff);}catch{document.querySelector('#research-cutoff').textContent=t('발간 주간과 검토 기준시각을 확인하세요.');}
  document.querySelector('#rights-confirmed').checked=state.rightsConfirmed;document.querySelector('#editorial-reviewed').checked=state.editorialReviewed;document.querySelector('#correction-note').value=state.correctionNote;
  rows.replaceChildren();for(const id of state.articleIds){const item=evidence.find(e=>e.id===id);const row=el('div',undefined,'evidence-row');row.append(el('span',item?item.title+' · '+date(item.collectedAt):'자료를 다시 확인하세요.'));const remove=el('button','제외');remove.type='button';remove.addEventListener('click',()=>{state.articleIds=state.articleIds.filter(x=>x!==id);for(const f of state.forecasts)f.evidenceIds=f.evidenceIds.filter(x=>x!==id);changed();paint();});row.append(remove);rows.append(row);}
  if(!state.articleIds.length)rows.append(el('p','이번 주에 수집한 자료를 찾아 근거에 담아 주세요.'));
  forecasts.replaceChildren();state.forecasts.forEach((f,i)=>{
   const section=el('fieldset',undefined,'forecast-editor');section.append(el('legend',t('전망')+' '+(i+1)));
   const names={question:'연구 질문',observation:'자료에서 확인한 사실',outlook:'편집부의 전망·가설',horizon:'연구 기간',experiment:'다음 실험',successCriteria:'성공·전환 기준',stopCondition:'중단·보류 조건'};
   for(const [key,name]of Object.entries(names)){const label=el('label',name),input=el('textarea');input.value=f[key];input.maxLength=key==='horizon'?200:['observation','outlook','experiment'].includes(key)?2000:1000;input.addEventListener('input',()=>{f[key]=input.value;changed();});label.append(input);section.append(label);}
   const refs=el('fieldset');refs.append(el('legend','이 전망의 근거'));for(const id of state.articleIds){const item=evidence.find(e=>e.id===id),label=el('label',undefined,'check'),check=el('input');check.type='checkbox';check.checked=f.evidenceIds.includes(id);check.addEventListener('change',()=>{f.evidenceIds=check.checked?[...f.evidenceIds,id]:f.evidenceIds.filter(x=>x!==id);changed();});label.append(check,el('span',item?.title||id));refs.append(label);}section.append(refs);
   const remove=el('button','전망 삭제');remove.type='button';remove.addEventListener('click',()=>{state.forecasts.splice(i,1);changed();paint();});section.append(remove);forecasts.append(section);
  });
 }
 document.querySelector('#roll-research-week').addEventListener('click',()=>{if(!confirm(t('본문과 전망은 유지하고 이전 주 근거를 비울까요? 새 주간 자료로 다시 검토해야 합니다.')))return;const fresh=blankDossier(expectedWeek);state={...state,weekStart:expectedWeek,cutoff:fresh.cutoff,articleIds:[],forecasts:state.forecasts.map(f=>({...f,evidenceIds:[]})),rightsConfirmed:false,editorialReviewed:false};evidence=[];changed();paint();});
 document.querySelector('#add-forecast').addEventListener('click',()=>{if(state.forecasts.length>=8)return;state.forecasts.push({question:'',observation:'',outlook:'',horizon:'',experiment:'',successCriteria:'',stopCondition:'',evidenceIds:[]});changed();paint();});
 for(const [id,key]of [['rights-confirmed','rightsConfirmed'],['editorial-reviewed','editorialReviewed']])document.querySelector('#'+id).addEventListener('change',e=>{state[key]=e.target.checked;changed(key==='editorialReviewed');});
 document.querySelector('#correction-note').addEventListener('input',e=>{state.correctionNote=e.target.value;changed();});
 window.addEventListener('moapan-language',paint);
 paint();return {value:()=>structuredClone(state),load:(d,items=[],week)=>{expectedWeek=week||blankDossier().weekStart;state=d?structuredClone(d):blankDossier(expectedWeek);evidence=items;document.querySelector('#publication-review').replaceChildren();paint();},invalidateReview:invalid,setCutoff:value=>{state.cutoff=value;changed();paint();},addEvidence:item=>{if(state.articleIds.includes(item.id))return;if(state.articleIds.length>=40)throw Error('근거 자료는 최대 40개입니다.');state.articleIds.push(item.id);evidence.push(item);changed();paint();}};
}
